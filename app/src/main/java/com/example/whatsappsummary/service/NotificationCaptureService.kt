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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

/**
 * NotificationCaptureService: captura y procesa TODAS las notificaciones del dispositivo.
 * - Recopila notificaciones de cualquier aplicación (WhatsApp, Telegram, Gmail, etc.)
 * - Las normaliza, filtra y almacena en la base de datos local
 * - Genera resúmenes automáticos diarios según configuración
 * - Respeta configuración del usuario (apps permitidas, filtros, etc.)
 */
class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    // Map to track recent notifications to avoid duplicate processing: signature -> timestamp
    private val recentNotifications = mutableMapOf<String, Long>()
    private val recentLock = Any()
    
    companion object {
        private const val TAG = "NotificationCapture"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }

    /**
     * Normaliza un nombre de chat para comparación:
     * - Convierte a minúsculas
     * - Elimina espacios, acentos y caracteres especiales
     * - Mantiene solo letras y números
     */
    private fun normalizeForCompare(s: String): String {
        // Normalizar unicode (eliminar acentos)
        val normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        // Eliminar marcas diacríticas, convertir a minúsculas, eliminar espacios y caracteres especiales
        return normalized
            .replace(Regex("\\p{M}"), "") // Eliminar marcas diacríticas
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]"), "") // Solo letras y números
    }

    /**
     * Busca o crea una aplicación en la base de datos.
     * Retorna el ID de la app.
     */
    private suspend fun getOrCreateApp(packageName: String): Long {
        // Buscar app existente
        var app = database.appDao().getAppByPackageName(packageName)
        
        if (app == null) {
            // Obtener nombre legible de la app
            val appName = try {
                val applicationInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                applicationContext.packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: Exception) {
                packageName // Fallback al package name
            }
            
            // Crear nueva app
            app = App(
                packageName = packageName,
                appName = appName
            )
            val appId = database.appDao().insertApp(app)
            
            // Si insertApp retorna 0 (conflicto), buscar de nuevo
            if (appId == 0L) {
                app = database.appDao().getAppByPackageName(packageName)
                return app?.id ?: -1L
            }
            
            return appId
        }
        
        return app.id
    }

    /**
     * Busca o crea un chat en la base de datos.
     * Retorna el chatId.
     */
    private suspend fun getOrCreateChat(chatId: String, chatName: String, appId: Long, isGroup: Boolean): String {
        var chat = database.chatDao().getChatById(chatId)
        
        if (chat == null) {
            // Crear nuevo chat
            chat = Chat(
                chatId = chatId,
                chatName = chatName,
                appId = appId,
                isGroup = isGroup,
                unreadCount = 0
            )
            database.chatDao().insertChat(chat)
        }
        
        return chat.chatId
    }

    /**
     * Busca un chat existente que coincida con el nombre candidato.
     * Compara la similitud en ambas direcciones y retorna el chatId si hay >=70% de coincidencia.
     * 
     * @param candidateName Nombre del chat a buscar
     * @param appId ID de la aplicación
     * @return chatId del chat coincidente o null si no hay coincidencia >= 70%
     */
    private suspend fun findMatchingChatId(candidateName: String, appId: Long): String? {
        val allChats = database.chatDao().getChatsByAppId(appId)
        if (allChats.isEmpty()) return null
        
        val normalizedCandidate = normalizeForCompare(candidateName)
        if (normalizedCandidate.isEmpty()) return null
        
        var maxScore = 0.0
        var bestChat: Chat? = null
        
        for (chat in allChats) {
            val normalizedExisting = normalizeForCompare(chat.chatName)
            if (normalizedExisting.isEmpty()) continue
            
            // Calcular similitud en ambas direcciones
            val score1 = calculateSimilarity(normalizedCandidate, normalizedExisting)
            val score2 = calculateSimilarity(normalizedExisting, normalizedCandidate)
            
            // Tomar el máximo de ambas direcciones
            val finalScore = maxOf(score1, score2)
            
            if (finalScore > maxScore) {
                maxScore = finalScore
                bestChat = chat
            }
        }
        
        // Retornar el chat si la similitud es >= 70%
        return if (maxScore >= 0.70) {
            Log.d(TAG, "Chat match found: '$candidateName' -> '${bestChat?.chatName}' (similarity: ${maxScore * 100}%)")
            bestChat?.chatId
        } else {
            Log.d(TAG, "No matching chat found for '$candidateName' (best similarity: ${maxScore * 100}%)")
            null
        }
    }
    
    /**
     * Calcula la similitud entre dos strings normalizados.
     * Retorna un valor entre 0.0 y 1.0 indicando el porcentaje de caracteres que coinciden
     * en las mismas posiciones.
     * 
     * @param str1 Primer string normalizado
     * @param str2 Segundo string normalizado
     * @return Porcentaje de similitud (0.0 a 1.0)
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        
        val minLen = minOf(str1.length, str2.length)
        val maxLen = maxOf(str1.length, str2.length)
        
        var matches = 0
        for (i in 0 until minLen) {
            if (str1[i] == str2[i]) {
                matches++
            }
        }
        
        // Calcular similitud basada en el string más largo
        return matches.toDouble() / maxLen.toDouble()
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        Log.d(TAG, "Service created")

        // Programar generación automática de resúmenes diarios a las 23:00
        DailySummaryWorker.scheduleDailyWork(applicationContext)
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

        // Verificar si la aplicación está ignorada
        val appPrefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isIgnored = appPrefs.getBoolean("ignore_${packageName}", false)
        if (isIgnored) {
            Log.d(TAG, "Skipping notification from ignored app: $packageName")
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

    private fun isRecentDuplicate(signature: String, windowMs: Long = 15_000L): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentLock) {
            // remove old entries older than 60s to keep map small
            val cutoff = now - 60_000L
            val it = recentNotifications.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value < cutoff) it.remove()
            }

            val existing = recentNotifications[signature]
            if (existing != null && now - existing <= windowMs) {
                return true
            }
            recentNotifications[signature] = now
            return false
        }
    }

    private suspend fun saveOtherNotification(packageName: String, title: String, text: String) {
        val timestamp = System.currentTimeMillis()

        // 1. Obtener o crear la App
        val appId = getOrCreateApp(packageName)
        if (appId == -1L) {
            Log.e(TAG, "Failed to get or create app: $packageName")
            return
        }

        // 2. Determinar chatId y chatName
        val rawTitle = if (title.isNotEmpty()) title else packageName
        val chatIdCandidate = com.example.whatsappsummary.util.ChatUtils.normalizeChatTitle(rawTitle)
        
        // Buscar chat similar existente en la misma app
        val matched = findMatchingChatId(chatIdCandidate, appId)
        val finalChatId = matched ?: "$packageName|$chatIdCandidate"
        val chatName = if (matched != null) {
            database.chatDao().getChatById(matched)?.chatName ?: chatIdCandidate
        } else {
            chatIdCandidate
        }

        // Determinar si es grupo
        val isGroup = title.contains(":") || text.contains(":")
        
        val actualMessage = text.trim()
        if (actualMessage.isEmpty()) return

        // Evitar procesar notificaciones idénticas muy recientes
        try {
            val sig = "$finalChatId|$packageName|${actualMessage.lowercase(Locale.getDefault())}"
            if (isRecentDuplicate(sig)) {
                Log.d(TAG, "Recent duplicate other-notification skipped: $sig")
                return
            }
        } catch (e: Exception) {
            // ignore
        }

        // 3. Obtener o crear el Chat
        getOrCreateChat(finalChatId, chatName, appId, isGroup)

        // 4. Verificar duplicados en los últimos 10 segundos
        val dedupeWindowMs = 10_000L
        val sinceTime = timestamp - dedupeWindowMs
        val notifCount = database.notificationDao().countExactNotification(finalChatId, actualMessage, sinceTime, timestamp)
        
        if (notifCount == 0) {
            // 5. Guardar la notificación
            val notification = com.example.whatsappsummary.data.entity.Notification(
                appId = appId,
                chatId = finalChatId,
                title = title,
                text = actualMessage,
                timestamp = timestamp,
                sender = null,
                isFromMe = false,
                isGroup = isGroup,
                extrasJson = null
            )
            database.notificationDao().insertNotification(notification)
            
            // 6. Incrementar contador de no leídos
            database.chatDao().incrementUnreadCount(finalChatId)
        } else {
            Log.d(TAG, "Notificación duplicada detectada, omitiendo: $finalChatId | $actualMessage")
        }
    }

    private suspend fun saveNotificationData(notificationPackage: String, title: String, messageText: String) {
        val timestamp = System.currentTimeMillis()

        // 1. Obtener o crear la App
        val appId = getOrCreateApp(notificationPackage)
        if (appId == -1L) {
            Log.e(TAG, "Failed to get or create app: $notificationPackage")
            return
        }

        // 2. Determinar si es un grupo
        val isGroup = title.contains(":") || messageText.contains(":")

        // 3. Normalizar título del chat
        fun normalizeChatTitle(raw: String): String {
            var s = raw.trim()
            // Eliminar sufijos comunes que indican mensajes no leídos
            s = s.replace(Regex("\\s*\\(\\s*\\d+\\+?\\s*(mensajes|unread|unread_messages|new)?\\s*\\)\\s*$", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("\\s*-\\s*\\d+\\+?\\s*$"), "")
            return s.trim()
        }

        val rawChatTitle = if (title.indexOf(":") >= 0) title.substring(0, title.indexOf(":")) else title
        val chatIdCandidate = normalizeChatTitle(rawChatTitle)
        val chatName = chatIdCandidate

        // 4. Buscar chats existentes y unificar con un match si hay coincidencia >75%
        val chatId = findMatchingChatId(chatName, appId) ?: chatIdCandidate

        // 5. Extraer el remitente
        val senderName = if (isGroup && messageText.contains(":")) {
            messageText.substringBefore(":").trim()
        } else {
            title  // En chats privados, el title contiene el nombre del contacto
        }

        // 6. Extraer el mensaje real
        val actualMessage = if (isGroup && messageText.contains(":")) {
            messageText.substringAfter(":").trim()
        } else {
            messageText.trim()
        }
        if (actualMessage.isEmpty()) return

        // 7. Evitar procesar notificaciones idénticas muy recientes
        try {
            val sig = "$chatId|$senderName|${actualMessage.lowercase(Locale.getDefault())}"
            if (isRecentDuplicate(sig)) {
                Log.d(TAG, "Recent duplicate whatsapp-notification skipped: $sig")
                return
            }
        } catch (e: Exception) {
            // ignore
        }

        // 8. Obtener o crear el Chat
        getOrCreateChat(chatId, chatName, appId, isGroup)

        // 9. Verificar duplicados en los últimos 10 segundos
        val dedupeWindowMs = 10_000L
        val sinceTime = timestamp - dedupeWindowMs
        val notifCount = database.notificationDao().countExactNotification(chatId, actualMessage, sinceTime, timestamp)

        if (notifCount == 0) {
            // 10. Guardar la notificación/mensaje
            val notification = com.example.whatsappsummary.data.entity.Notification(
                appId = appId,
                chatId = chatId,
                title = title,
                text = actualMessage,
                timestamp = timestamp,
                sender = senderName,
                isFromMe = false,
                isGroup = isGroup,
                extrasJson = null
            )
            database.notificationDao().insertNotification(notification)
            
            // 11. Incrementar contador de no leídos
            database.chatDao().incrementUnreadCount(chatId)
        } else {
            Log.d(TAG, "Mensaje duplicado detectado, omitiendo: $chatId | $senderName | $actualMessage")
        }

        // Automatic daily summaries are now handled by DailySummaryWorker at 23:00
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
