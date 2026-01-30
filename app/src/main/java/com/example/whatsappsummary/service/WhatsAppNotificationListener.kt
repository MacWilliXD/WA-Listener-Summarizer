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

    private fun normalizeForCompare(s: String): String = s.toLowerCase(Locale.getDefault()).replace(" ", "")

    private fun findMatchingChatId(candidateName: String): String? {
        val allChats = database.chatDao().getAllChatsList()
        if (allChats.isEmpty()) return null
        var maxScore = 0.0
        var bestChat: Chat? = null
        for (c in allChats) {
            val na = normalizeForCompare(c.chatName)
            val nb = normalizeForCompare(candidateName)
            val minLen = if (na.length < nb.length) na.length else nb.length
            var match = 0
            for (i in 0 until minLen) {
                if (na[i] == nb[i]) match++
            }
            val score = if (na.isEmpty() || nb.isEmpty()) 0.0 else match.toDouble() / (if (na.length > nb.length) na.length else nb.length)
            if (score > maxScore) {
                maxScore = score
                bestChat = c
            }
        }
        return if (maxScore > 0.75) bestChat?.chatId else null
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        val prefs = applicationContext.getSharedPreferences("wa_listener_prefs", MODE_PRIVATE)
        val collectWhatsApp = prefs.getBoolean("collect_whatsapp", true)
        val collectOthers = prefs.getBoolean("collect_others", false)

        // Decide whether to process this notification
        val isWhatsApp = packageName == WHATSAPP_PACKAGE || packageName == WHATSAPP_BUSINESS
        if (isWhatsApp && !collectWhatsApp) {
            Log.d(TAG, "Skipping WhatsApp notification because collect_whatsapp=false")
            return
        }
        if (!isWhatsApp && !collectOthers) {
            Log.d(TAG, "Skipping non-WhatsApp notification because collect_others=false")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extraer información de la notificación (más robusto)
        var title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Fallbacks: big text, summary, text lines, ticker
        if (text.isBlank()) {
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            if (!big.isNullOrBlank()) text = big
        }
        if (text.isBlank()) {
            val summary = extras.getString(Notification.EXTRA_SUMMARY_TEXT)
            if (!summary.isNullOrBlank()) text = summary
        }
        if (text.isBlank()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                text = lines.joinToString(separator = "\n") { it.toString() }
            }
        }
        if (text.isBlank()) {
            val ticker = notification.tickerText?.toString()
            if (!ticker.isNullOrBlank()) text = ticker
        }

        // If title missing, try summary or package label
        if (title.isBlank()) {
            title = extras.getString(Notification.EXTRA_SUB_TEXT) ?: title
            if (title.isBlank()) {
                try {
                    val ai = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                    title = applicationContext.packageManager.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        // Debug: list extras keys (helpful when some WA notifications use messaging style)
        try {
            Log.d(TAG, "Notification extras keys: ${extras.keySet()}")
        } catch (e: Exception) {
            // ignore
        }

        // Procesar todas las notificaciones de WhatsApp: quitar (x mensajes) y unificar por nombre de chat
        if (isWhatsApp) {
            // Eliminar patrones como (47 mensajes), (47), (99+), etc. del título y texto
            val cleanTitle = title.replace(Regex("\\s*\\(\\s*\\d+\\+?\\s*(mensajes|unread|unread_messages|new)?\\s*\\)"), "").trim()
            val cleanText = text.replace(Regex("\\s*\\(\\s*\\d+\\+?\\s*(mensajes|unread|unread_messages|new)?\\s*\\)"), "").trim()
            // También quitar patrones como "| 47 mensajes nuevos" al final del texto
            val cleanText2 = cleanText.replace(Regex("\\|\\s*\\d+\\s*(mensajes nuevos|new messages|new|mensajes|unread)"), "").trim()
            title = cleanTitle
            text = cleanText2
        }



        // Filtrar notificaciones de WhatsApp irrelevantes solicitadas:
        if (isWhatsApp) {
            val lowerText = text.toLowerCase(Locale.getDefault()).trim()
            // "Comprobando si hay mensajes nuevos"
            if (lowerText.contains("comprobando si hay mensajes nuevos")) {
                Log.d(TAG, "Treating WhatsApp probe notification as message: $text")
                text = "(Notificación automática) $text"
            }
            // "x mensajes de x chats" e.g. "5 mensajes de 3 chats"
            if (Regex("^\\s*\\d+\\s+mensajes\\s+de\\s+\\d+\\s+chats\\s*$", RegexOption.IGNORE_CASE).matches(lowerText)) {
                Log.d(TAG, "Treating WhatsApp aggregated messages notification as message: $text")
                text = "(Notificación automática) $text"
            }
            // "x mensajes nuevos" e.g. "3 mensajes nuevos"
            if (Regex("^\\s*\\d+\\s+mensajes\\s+nuevos\\s*$", RegexOption.IGNORE_CASE).matches(lowerText)) {
                Log.d(TAG, "Treating WhatsApp 'mensajes nuevos' notification as message: $text")
                text = "(Notificación automática) $text"
            }
        }

        // Verificar que no sea una notificación de sistema
        if (title.isEmpty() && text.isEmpty()) return

        Log.d(TAG, "Notification from $packageName: $title - $text")

        serviceScope.launch {
            try {
                if (isWhatsApp) {
                    saveNotificationData(packageName, title, text)
                } else {
                    saveOtherNotification(packageName, title, text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notification", e)
            }
        }
    }

    private suspend fun saveOtherNotification(packageName: String, title: String, text: String) {
        val timestamp = System.currentTimeMillis()

        // Use packageName + normalized title as chatId to group notifications by app+title
        val rawTitle = if (title.isNotEmpty()) title else packageName
        val chatIdCandidate = com.example.whatsappsummary.util.ChatUtils.normalizeChatTitle(rawTitle)
        val matched = findMatchingChatId(chatIdCandidate)
        val finalChatId = matched ?: "$packageName|$chatIdCandidate"
        val chatName = if (matched != null) database.chatDao().getChatById(matched)?.chatName ?: chatIdCandidate else chatIdCandidate

        // Use senderName as app name
        val senderName = packageName
        val actualMessage = if (text.isNotEmpty()) text else "(sin contenido)"

        var chat = database.chatDao().getChatById(finalChatId)
        if (chat == null) {
            chat = Chat(
                chatId = finalChatId,
                chatName = chatName,
                lastMessage = actualMessage,
                lastMessageTime = timestamp,
                    unreadCount = 1,
                    packageName = packageName
            )
            database.chatDao().insertChat(chat)
        } else {
            val updatedChat = chat.copy(
                lastMessage = actualMessage,
                lastMessageTime = timestamp,
                    unreadCount = chat.unreadCount + 1,
                    packageName = packageName
            )
            database.chatDao().updateChat(updatedChat)
        }

        // Evitar duplicados en mensajes generales
        val dedupeWindowMs = 5_000L
        val sinceTime = timestamp - dedupeWindowMs
        val similarCount = database.messageDao().countSimilarRecent(finalChatId, senderName, actualMessage, sinceTime)
        val exactCount = database.messageDao().countExactMessage(finalChatId, actualMessage, timestamp)
        if (exactCount == 0 && similarCount == 0) {
            val message = Message(
                chatId = finalChatId,
                senderName = senderName,
                messageText = actualMessage,
                timestamp = timestamp,
                isGroupMessage = false
            )
            database.messageDao().insertMessage(message)
        } else {
            Log.d(TAG, "Duplicate other-notification detected, skipping")
        }

        // Only generate automatic daily summary if enabled for this chat
        val prefsAuto = applicationContext.getSharedPreferences("whatsapp_prefs", MODE_PRIVATE)
        val autoKey = "auto_summaries_${finalChatId}"
        val enabled = prefsAuto.getBoolean(autoKey, false)
        if (enabled) {
            generateDailySummary(finalChatId, timestamp)
        } else {
            Log.d(TAG, "Auto summary disabled for $finalChatId")
        }
    }

    private suspend fun saveNotificationData(notificationPackage: String, title: String, messageText: String) {
        val timestamp = System.currentTimeMillis()

        // Determinar si es un grupo (grupos suelen tener formato "Nombre: mensaje")
        val isGroup = title.contains(":") || messageText.contains(":")

        // Extraer el ID del chat (usamos el título como identificador)
        fun normalizeChatTitle(raw: String): String {
            var s = raw.trim()
            // Eliminar sufijos comunes que indican mensajes no leídos como "(7 mensajes)", "(7)", "(99+)", "(7 unread)"
            s = s.replace(Regex("\\s*\\(\\s*\\d+\\+?\\s*(mensajes|unread|unread_messages|new)?\\s*\\)\\s*$", RegexOption.IGNORE_CASE), "")
            // También eliminar patrones como " - 7" al final si aparecen
            s = s.replace(Regex("\\s*-\\s*\\d+\\+?\\s*$"), "")
            return s.trim()
        }


        val rawChatTitle = if (title.indexOf(":") >= 0) title.substring(0, title.indexOf(":")) else title
        val chatIdCandidate = normalizeChatTitle(rawChatTitle)
        val chatName = chatIdCandidate

        // Buscar chats existentes y unificar con un match si hay coincidencia >75%
        val chatId = findMatchingChatId(chatName) ?: chatIdCandidate

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
                unreadCount = 1,
                packageName = notificationPackage
            )
            database.chatDao().insertChat(chat)
        } else {
            // Actualizar chat existente
            val updatedChat = chat.copy(
                lastMessage = actualMessage,
                lastMessageTime = timestamp,
                unreadCount = chat.unreadCount + 1,
                packageName = notificationPackage
            )
            database.chatDao().updateChat(updatedChat)
        }

        // Evitar duplicados: si ya existe un mensaje similar en los últimos segundos, omitir
        val dedupeWindowMs = 5_000L // 5 segundos
        val sinceTime = timestamp - dedupeWindowMs
        val similarCount = database.messageDao().countSimilarRecent(chatId, senderName, actualMessage, sinceTime)
        val exactCount = database.messageDao().countExactMessage(chatId, actualMessage, timestamp)
        if (exactCount == 0 && similarCount == 0) {
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

        // Generar resumen diario sólo si está activado para este chat
        val prefsAuto = applicationContext.getSharedPreferences("whatsapp_prefs", MODE_PRIVATE)
        val autoKey = "auto_summaries_${chatId}"
        val enabled = prefsAuto.getBoolean(autoKey, false)
        if (enabled) {
            generateDailySummary(chatId, timestamp)
        } else {
            Log.d(TAG, "Auto summary disabled for $chatId")
        }
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
