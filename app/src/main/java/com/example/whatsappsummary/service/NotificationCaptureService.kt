package com.example.whatsappsummary.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.App
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.util.ChatUtils
import com.example.whatsappsummary.util.SocialAppRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * NotificationCaptureService: captura notificaciones del dispositivo.
 *
 * Flujo:
 * 1. Filtro rápido por app / preferencias / ignore-list.
 * 2. Extracción robusta de título+texto+sender (soporta MessagingStyle).
 * 3. Descartar placeholders ("3 mensajes nuevos", "Comprobando mensajes", ...).
 * 4. Dedup por `sbn.key` + hash de contenido (ventana 2 min).
 * 5. Routing:
 *    - App social (WhatsApp, Telegram, Messenger, ...): agrupa por chat.
 *    - App no social: colapsa en una sola entrada por app (chatId = "app:<pkg>").
 */
class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase

    // signature -> lastSeenMs. Usado para filtrar duplicados cercanos.
    private val recentSignatures = HashMap<String, Long>()
    private val recentLock = Any()

    companion object {
        private const val TAG = "NotificationCapture"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val DEDUP_WINDOW_MS = 2 * 60 * 1000L          // 2 min
        private const val SIG_GC_AFTER_MS = 10 * 60 * 1000L         // 10 min
        private const val FUZZY_MATCH_THRESHOLD = 0.72
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        DailySummaryWorker.scheduleDailyWork(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        val prefs = applicationContext.getSharedPreferences("wa_listener_prefs", MODE_PRIVATE)
        val collectWhatsApp = prefs.getBoolean("collect_whatsapp", true)
        val collectOthers = prefs.getBoolean("collect_others", false)

        val isWhatsApp = packageName == WHATSAPP_PACKAGE || packageName == WHATSAPP_BUSINESS
        val isSocial = SocialAppRegistry.isSocial(packageName)

        if (isWhatsApp && !collectWhatsApp) return
        if (!isWhatsApp && !collectOthers) return

        val appPrefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (appPrefs.getBoolean("ignore_${packageName}", false)) return

        // Ignorar notificaciones "ongoing" (servicios en 1er plano): reproductor, VPN, etc.
        val flags = sbn.notification?.flags ?: 0
        if (flags and Notification.FLAG_ONGOING_EVENT != 0 && flags and Notification.FLAG_FOREGROUND_SERVICE != 0) {
            return
        }

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val extracted = extractContent(extras, notif, packageName) ?: return
        val (rawTitle, rawText) = extracted

        // Limpieza de contadores de "no leídos" en el título, comunes en varias apps.
        val cleanedTitle = ChatUtils.normalizeChatTitle(rawTitle)
        val cleanedText = rawText
            .replace(
                Regex(
                    "\\s*\\(\\s*\\d+\\+?\\s*(mensajes?\\s*nuevos?|mensajes?|msgs?|new(?:\\s+messages?)?|unread)?\\s*\\)",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    "\\|\\s*\\d+\\s*(mensajes?\\s*nuevos?|new\\s+messages?|mensajes?|msgs?|unread)",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        if (cleanedTitle.isBlank() && cleanedText.isBlank()) return

        // Filtro DURO de placeholders: ya no se guardan nunca.
        if (ChatUtils.isPlaceholderNotification(cleanedTitle, cleanedText)) {
            Log.d(TAG, "Placeholder descartado: [$packageName] $cleanedTitle - $cleanedText")
            return
        }

        // Dedup por `sbn.key` + contenido. `sbn.key` se repite cuando la misma
        // notificación se actualiza; no queremos una fila nueva cada vez.
        val keySignature = "${sbn.key}|${cleanedText.lowercase(Locale.getDefault())}"
        if (isRecentDuplicate(keySignature, DEDUP_WINDOW_MS)) {
            Log.d(TAG, "Dedup key: $keySignature")
            return
        }

        serviceScope.launch {
            try {
                if (isSocial) {
                    saveSocialNotification(packageName, cleanedTitle, cleanedText)
                } else {
                    saveAppBucketNotification(packageName, cleanedTitle, cleanedText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando notificación", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Extracción de contenido
    // -------------------------------------------------------------------------

    private fun extractContent(
        extras: android.os.Bundle,
        notif: Notification,
        packageName: String
    ): Pair<String, String>? {
        var title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // Preferir el último mensaje de MessagingStyle (WhatsApp / Messenger / Telegram)
        try {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                val last = messages.last() as? android.os.Bundle
                val lastText = last?.getCharSequence("text")?.toString()
                val lastSender = last?.getCharSequence("sender")?.toString()
                if (!lastText.isNullOrBlank()) text = lastText
                if (!lastSender.isNullOrBlank() && title.isBlank()) title = lastSender
            }
        } catch (_: Exception) {
            // algunos ROMs no exponen EXTRA_MESSAGES correctamente
        }

        if (text.isBlank()) {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { text = it }
        }
        if (text.isBlank()) {
            extras.getString(Notification.EXTRA_SUMMARY_TEXT)
                ?.takeIf { it.isNotBlank() }?.let { text = it }
        }
        if (text.isBlank()) {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { it.toString() }
                ?.let { text = it }
        }
        if (text.isBlank()) {
            notif.tickerText?.toString()?.takeIf { it.isNotBlank() }?.let { text = it }
        }

        if (title.isBlank()) {
            extras.getString(Notification.EXTRA_SUB_TEXT)?.takeIf { it.isNotBlank() }?.let { title = it }
        }
        if (title.isBlank()) {
            try {
                val ai = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                title = applicationContext.packageManager.getApplicationLabel(ai).toString()
            } catch (_: Exception) { /* ignore */ }
        }

        if (title.isBlank() && text.isBlank()) return null
        return title.trim() to text.trim()
    }

    // -------------------------------------------------------------------------
    // Guardado por tipo de app
    // -------------------------------------------------------------------------

    private suspend fun saveSocialNotification(packageName: String, title: String, text: String) {
        val appId = getOrCreateApp(packageName)
        if (appId == -1L) return

        // Detectar grupo si el texto incluye "Nombre: mensaje"
        val isGroupFromText = text.contains(":")
        // Título suele ser "Grupo: Autor" o "Autor" (privado)
        val rawChatTitle = if (title.contains(":")) title.substringBefore(":").trim() else title
        val chatName = ChatUtils.normalizeChatTitle(rawChatTitle).ifBlank { title }

        val existingId = findMatchingChatId(chatName, appId)
        val chatId = existingId ?: ChatUtils.canonicalize(chatName).ifBlank { "chat:${System.currentTimeMillis()}" }

        // Sender: en grupo suele venir como "Sender: texto"
        val sender: String
        val messageText: String
        if (isGroupFromText) {
            sender = text.substringBefore(":").trim().ifBlank { title }
            messageText = text.substringAfter(":").trim()
        } else {
            sender = title.ifBlank { chatName }
            messageText = text
        }

        if (messageText.isBlank()) return

        val contentSig = "$chatId|${sender.lowercase(Locale.getDefault())}|${messageText.lowercase(Locale.getDefault())}"
        if (isRecentDuplicate(contentSig, DEDUP_WINDOW_MS)) return
        if (existsSameTextRecent(chatId, messageText)) return
        // Cross-chat: WhatsApp puede enviar el mismo mensaje con dos títulos distintos
        // dejándolo en chats diferentes. Descartamos el segundo.
        if (existsSameTextInAppRecent(appId, messageText)) return

        getOrCreateChat(chatId, chatName, appId, isGroup = isGroupFromText || title.contains(":"))

        val notif = com.example.whatsappsummary.data.entity.Notification(
            appId = appId,
            chatId = chatId,
            title = title,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            sender = sender,
            isFromMe = false,
            isGroup = isGroupFromText,
            extrasJson = null
        )
        database.notificationDao().insertNotification(notif)
        database.chatDao().incrementUnreadCount(chatId)
    }

    /**
     * Para apps no-sociales, todas las notificaciones de un mismo paquete se
     * guardan bajo un único chat colapsado (chatId = "app:<pkg>"). Así el usuario
     * ve "Gmail", "Banco X", etc. como filas únicas en lugar de una fila por cada
     * asunto distinto.
     */
    private suspend fun saveAppBucketNotification(packageName: String, title: String, text: String) {
        val appId = getOrCreateApp(packageName)
        if (appId == -1L) return

        val chatId = SocialAppRegistry.appBucketChatId(packageName)
        val appName = resolveAppName(packageName)
        val chatName = appName

        val messageText = text.ifBlank { title }
        if (messageText.isBlank()) return

        val contentSig = "$chatId|${messageText.lowercase(Locale.getDefault())}"
        if (isRecentDuplicate(contentSig, DEDUP_WINDOW_MS)) return
        if (existsSameTextRecent(chatId, messageText)) return
        if (existsSameTextInAppRecent(appId, messageText)) return

        getOrCreateChat(chatId, chatName, appId, isGroup = false)

        val notif = com.example.whatsappsummary.data.entity.Notification(
            appId = appId,
            chatId = chatId,
            title = title.ifBlank { appName },
            text = messageText,
            timestamp = System.currentTimeMillis(),
            sender = title.ifBlank { appName },
            isFromMe = false,
            isGroup = false,
            extrasJson = null
        )
        database.notificationDao().insertNotification(notif)
        database.chatDao().incrementUnreadCount(chatId)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun getOrCreateApp(packageName: String): Long {
        val existing = database.appDao().getAppByPackageName(packageName)
        if (existing != null) return existing.id

        val appName = resolveAppName(packageName)
        val inserted = database.appDao().insertApp(App(packageName = packageName, appName = appName))
        if (inserted == 0L) {
            return database.appDao().getAppByPackageName(packageName)?.id ?: -1L
        }
        return inserted
    }

    private fun resolveAppName(packageName: String): String = try {
        val ai = applicationContext.packageManager.getApplicationInfo(packageName, 0)
        applicationContext.packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        packageName
    }

    private suspend fun getOrCreateChat(chatId: String, chatName: String, appId: Long, isGroup: Boolean) {
        val existing = database.chatDao().getChatById(chatId)
        if (existing == null) {
            database.chatDao().insertChat(
                Chat(chatId = chatId, chatName = chatName, appId = appId, isGroup = isGroup, unreadCount = 0)
            )
        }
    }

    private suspend fun findMatchingChatId(candidateName: String, appId: Long): String? {
        if (candidateName.isBlank()) return null
        val chats = database.chatDao().getChatsByAppId(appId)
        if (chats.isEmpty()) return null

        var best: Pair<Chat, Double>? = null
        for (c in chats) {
            // nunca mergear con buckets de app
            if (c.chatId.startsWith("app:")) continue
            val score = ChatUtils.trigramSimilarity(candidateName, c.chatName)
            if (score > (best?.second ?: 0.0)) best = c to score
        }

        return if (best != null && best.second >= FUZZY_MATCH_THRESHOLD) best.first.chatId else null
    }

    private fun isRecentDuplicate(signature: String, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentLock) {
            // GC
            val cutoff = now - SIG_GC_AFTER_MS
            recentSignatures.entries.removeAll { it.value < cutoff }

            val prev = recentSignatures[signature]
            if (prev != null && now - prev <= windowMs) return true
            recentSignatures[signature] = now
            return false
        }
    }

    /**
     * Dedup por contenido consultando la BD: si ya existe en los últimos 10
     * minutos una notificación con el mismo texto en el mismo chat, la nueva se
     * considera duplicada (p.ej. WhatsApp reemite la misma notif varias veces).
     */
    private suspend fun existsSameTextRecent(chatId: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        val since = now - 10 * 60 * 1000L
        return try {
            database.notificationDao().countExactNotification(chatId, text, since, now) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Dedup cross-chat: misma app, mismo texto, ventana corta. Evita el caso típico
     * de WhatsApp en grupos donde un mismo mensaje llega dos veces con títulos
     * distintos ("Grupo: Sender" vs solo "Sender") y queda guardado en dos chatIds.
     */
    private suspend fun existsSameTextInAppRecent(
        appId: Long,
        text: String,
        windowMs: Long = 3 * 60 * 1000L
    ): Boolean {
        if (text.isBlank()) return false
        val now = System.currentTimeMillis()
        val since = now - windowMs
        return try {
            database.notificationDao().countSameTextInAppRecent(appId, text, since, now) > 0
        } catch (_: Exception) {
            false
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        super.onDestroy()
    }
}
