package com.example.whatsappsummary.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.whatsappsummary.R
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.ui.SummaryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Envía dos notificaciones recordatorio al día:
 *   - 21:00 (modo "evening"): "Hoy recolecté X notificaciones de Y apps · resumen del día"
 *   - 07:00 (modo "morning"):  "Ayer hubieron X mensajes de Y apps · lo pendiente/importante"
 * Ambas al tocarlas abren [SummaryActivity] con el rango y el modo adecuados.
 *
 * Si en el rango no hay notificaciones, no se posteará nada (para no hacer ruido).
 */
class SummaryReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val mode = inputData.getString(KEY_MODE) ?: return@withContext Result.success()
            val db = AppDatabase.getDatabase(context)

            val (rangeStart, rangeEnd) = computeRange(mode)
            val notifs = db.notificationDao().getNotificationsByRange(rangeStart, rangeEnd)
            if (notifs.isEmpty()) return@withContext Result.success()

            val uniqueApps = notifs.map { it.appId }.toSet().size
            val count = notifs.size

            postReminderNotification(mode, count, uniqueApps, rangeStart, rangeEnd)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error en recordatorio", e)
            Result.success() // no reintentar
        }
    }

    private fun computeRange(mode: String): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (mode) {
            MODE_EVENING -> {
                // Hoy 00:00 → ahora (que debe ser ~21:00)
                val end = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            MODE_MORNING -> {
                // Últimas 24 horas
                val end = cal.timeInMillis
                val start = end - 24L * 60 * 60 * 1000L
                start to end
            }
            else -> 0L to System.currentTimeMillis()
        }
    }

    private fun postReminderNotification(
        mode: String,
        count: Int,
        appCount: Int,
        rangeStart: Long,
        rangeEnd: Long
    ) {
        ensureChannel()

        val (title, body) = when (mode) {
            MODE_EVENING -> "Resumen del día" to "Recolecté $count notificaciones de $appCount app${if (appCount == 1) "" else "s"} hoy. ¿Quieres un resumen detallado?"
            MODE_MORNING -> "Buenos días" to "Ayer hubo $count mensajes de $appCount app${if (appCount == 1) "" else "s"}. ¿Te digo lo pendiente e importante?"
            else -> "Notirizer" to "Nuevo resumen disponible."
        }

        val intent = SummaryActivity.newIntent(
            context = context,
            chatIds = emptyList(),  // marker para "todos los chats"
            title = title,
            subtitle = "$count notificaciones · $appCount app${if (appCount == 1) "" else "s"}",
            detail = if (mode == MODE_EVENING) "Detallado" else "Intermedio",
            startTs = rangeStart,
            endTs = rangeEnd,
            onlyPriority = mode == MODE_MORNING
        ).apply {
            putExtra(SummaryActivity.EXTRA_ALL_CHATS, true)
            // Al abrir desde notificación, que sea una pila nueva
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            context,
            if (mode == MODE_EVENING) PENDING_EVENING else PENDING_MORNING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sparkle)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notifId = if (mode == MODE_EVENING) NOTIF_EVENING else NOTIF_MORNING
        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS no concedido en Android 13+; el usuario verá la app igualmente al abrir.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de resumen",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notificaciones diarias con el estado de tus mensajes."
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "SummaryReminder"
        const val CHANNEL_ID = "summary_reminders"
        const val KEY_MODE = "mode"
        const val MODE_EVENING = "evening"
        const val MODE_MORNING = "morning"

        private const val WORK_EVENING = "summary_reminder_evening"
        private const val WORK_MORNING = "summary_reminder_morning"
        private const val NOTIF_EVENING = 7001
        private const val NOTIF_MORNING = 7002
        private const val PENDING_EVENING = 17001
        private const val PENDING_MORNING = 17002

        fun scheduleAll(context: Context) {
            schedule(context, WORK_EVENING, MODE_EVENING, hour = 21, minute = 0)
            schedule(context, WORK_MORNING, MODE_MORNING, hour = 7, minute = 0)
        }

        private fun schedule(
            context: Context,
            uniqueName: String,
            mode: String,
            hour: Int,
            minute: Int
        ) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<SummaryReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MODE to mode))
                .addTag(uniqueName)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
