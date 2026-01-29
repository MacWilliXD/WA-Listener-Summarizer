package com.example.whatsappsummary.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WhatsAppNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    
    companion object {
        private const val TAG = "WANotificationListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Verificar si la notificación es de WhatsApp
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extraer información de la notificación
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        
        // Verificar que no sea una notificación de sistema
        if (title.isEmpty() || text.isEmpty()) return
        
        Log.d(TAG, "WhatsApp notification: $title - $text")

        serviceScope.launch {
            try {
                saveNotificationData(title, text)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notification", e)
            }
        }
    }

    private suspend fun saveNotificationData(title: String, messageText: String) {
        val timestamp = System.currentTimeMillis()
        
        // Determinar si es un grupo (grupos suelen tener formato "Nombre: mensaje")
        val isGroup = title.contains(":") || messageText.contains(":")
        
        // Extraer el ID del chat (usamos el título como identificador)
        val chatId = title.substringBefore(":").trim()
        val chatName = chatId
        
        // Extraer el remitente
        val senderName = if (isGroup && messageText.contains(":")) {
            messageText.substringBefore(":").trim()
        } else {
            chatName
        }
        
        // Extraer el mensaje real
        val actualMessage = if (isGroup && messageText.contains(":")) {
            messageText.substringAfter(":").trim()
        } else {
            messageText
        }

        // Verificar o crear el chat
        var chat = database.chatDao().getChatById(chatId)
        if (chat == null) {
            chat = Chat(
                chatId = chatId,
                chatName = chatName,
                lastMessage = actualMessage,
                lastMessageTime = timestamp,
                unreadCount = 1
            )
            database.chatDao().insertChat(chat)
        } else {
            // Actualizar chat existente
            val updatedChat = chat.copy(
                lastMessage = actualMessage,
                lastMessageTime = timestamp,
                unreadCount = chat.unreadCount + 1
            )
            database.chatDao().updateChat(updatedChat)
        }

        // Evitar duplicados: si ya existe un mensaje similar en los últimos segundos, omitir
        val dedupeWindowMs = 5_000L // 5 segundos
        val sinceTime = timestamp - dedupeWindowMs
        val similarCount = database.messageDao().countSimilarRecent(chatId, senderName, actualMessage, sinceTime)
        if (similarCount == 0) {
            // Guardar el mensaje
            val message = Message(
                chatId = chatId,
                senderName = senderName,
                messageText = actualMessage,
                timestamp = timestamp,
                isGroupMessage = isGroup
            )
            database.messageDao().insertMessage(message)
        } else {
            Log.d(TAG, "Mensaje duplicado detectado, omitiendo insert: $chatId | $senderName | $actualMessage")
        }

        // Generar resumen diario
        generateDailySummary(chatId, timestamp)
    }

    private suspend fun generateDailySummary(chatId: String, timestamp: Long) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date(timestamp))
        
        // Obtener inicio y fin del día
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        // Obtener mensajes del día
        val messages = database.messageDao().getMessagesByDateRange(chatId, startOfDay, endOfDay)
        val messageCount = messages.size
        
        // Crear resumen simple
        val summary = buildString {
            append("Total de mensajes: $messageCount\n")
            if (messages.isNotEmpty()) {
                val senders = messages.map { it.senderName }.distinct()
                append("Participantes: ${senders.joinToString(", ")}\n")
                append("\nÚltimos mensajes:\n")
                messages.takeLast(5).forEach { msg ->
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    append("[$time] ${msg.senderName}: ${msg.messageText}\n")
                }
            }
        }
        
        // Guardar o actualizar resumen
        val existingSummary = database.dailySummaryDao().getSummaryByDate(chatId, date)
        if (existingSummary == null) {
            val newSummary = com.example.whatsappsummary.data.entity.DailySummary(
                chatId = chatId,
                date = date,
                messageCount = messageCount,
                summary = summary,
                timestamp = timestamp
            )
            database.dailySummaryDao().insertSummary(newSummary)
        } else {
            val updatedSummary = existingSummary.copy(
                messageCount = messageCount,
                summary = summary,
                timestamp = timestamp
            )
            database.dailySummaryDao().updateSummary(updatedSummary)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Opcional: manejar cuando se eliminan notificaciones
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
