package com.example.whatsappsummary.service

import android.content.Context
import androidx.work.*
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.repository.NotificationRepository
import com.example.whatsappsummary.util.SummaryGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * DailySummaryWorker: genera resúmenes automáticos diarios a las 23:00 para todos los chats
 * que no tengan un resumen automático para el día actual.
 */
class DailySummaryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val repo = NotificationRepository(
                database.chatDao(),
                database.messageDao(),
                database.dailySummaryDao(),
                database.notificationDao()
            )
            val generator = SummaryGenerator(context, repo)

            // Obtener fecha de hoy (el día que terminó a las 23:00)
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayDate = dateFormat.format(calendar.time)

            // Obtener todos los chats
            val allChats = database.chatDao().getAllChatsList()

            for (chat in allChats) {
                try {
                    // Verificar si ya existe un resumen automático para hoy
                    val existingSummary = database.dailySummaryDao().getSummaryByDateAndType(chat.chatId, todayDate, "automatic")

                    if (existingSummary == null) {
                        // Generar resumen para hoy
                        val (startOfDay, endOfDay) = getDayRange(calendar.timeInMillis)

                        val summary = generator.generateDailySummary(
                            chat.chatId,
                            summaryLength = 120,
                            detailLevel = "Resumido",
                            extraPrompt = null,
                            startTimestamp = startOfDay,
                            endTimestamp = endOfDay
                        )

                        if (!summary.startsWith("ERROR") && !summary.startsWith("No hay")) {
                            // Contar mensajes del día
                            val messageCount = database.messageDao().getMessagesByDateRange(chat.chatId, startOfDay, endOfDay).size

                            val newSummary = com.example.whatsappsummary.data.entity.DailySummary(
                                chatId = chat.chatId,
                                date = todayDate,
                                messageCount = messageCount,
                                summary = summary,
                                timestamp = System.currentTimeMillis(),
                                type = "automatic"
                            )
                            database.dailySummaryDao().insertSummary(newSummary)
                        }
                    }
                } catch (e: Exception) {
                    // Log error but continue with other chats
                    android.util.Log.e("DailySummaryWorker", "Error generating summary for chat ${chat.chatId}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailySummaryWorker", "Error in daily summary generation", e)
            Result.failure()
        }
    }

    private fun getDayRange(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis
        return Pair(start, end)
    }

    companion object {
        private const val WORK_NAME = "daily_summary_work"

        /**
         * Programa el worker para ejecutarse diariamente a las 23:00
         */
        fun scheduleDailyWork(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // Cancelar trabajos anteriores si existen
            workManager.cancelUniqueWork(WORK_NAME)

            // Calcular el delay inicial hasta las 23:00 de hoy
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Si ya pasaron las 23:00 hoy, programar para mañana
            if (now.after(targetTime)) {
                targetTime.add(Calendar.DAY_OF_MONTH, 1)
            }

            val initialDelay = targetTime.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}