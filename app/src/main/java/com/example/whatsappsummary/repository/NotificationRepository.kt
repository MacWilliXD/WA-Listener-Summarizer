package com.example.whatsappsummary.repository

import androidx.lifecycle.LiveData
import com.example.whatsappsummary.data.dao.AppDao
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.NotificationDao
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.ChatWithLastMessage
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Notification

/**
 * Repository centralizado para manejar todas las notificaciones del dispositivo.
 * Gestiona apps, chats, notificaciones/mensajes y resúmenes diarios.
 */
class NotificationRepository(
    private val appDao: AppDao,
    private val chatDao: ChatDao,
    private val notificationDao: NotificationDao,
    private val dailySummaryDao: DailySummaryDao
) {
    // App operations
    suspend fun getAppByPackageName(packageName: String) = appDao.getAppByPackageName(packageName)
    
    suspend fun getAllApps() = appDao.getAllApps()
    
    // Chat operations
    val allChats: LiveData<List<Chat>> = chatDao.getAllChats()
    
    suspend fun getAllChatsList(): List<Chat> = chatDao.getAllChatsList()
    
    suspend fun getAllChatsWithLastMessage(): List<ChatWithLastMessage> {
        val allChats = chatDao.getAllChatsList()
        val appsById = appDao.getAllApps().associateBy { it.id }
        return allChats.map { chat ->
            val lastTs = notificationDao.getLastMessageTimeForChat(chat.chatId)
            val preview = notificationDao.getLastMessageTextForChat(chat.chatId).orEmpty()
            val app = appsById[chat.appId]
            val pkg = app?.packageName.orEmpty()
            ChatWithLastMessage(
                chat = chat,
                lastMessageTime = lastTs,
                lastMessagePreview = preview,
                packageName = pkg,
                appName = app?.appName.orEmpty(),
                isSocial = com.example.whatsappsummary.util.SocialAppRegistry.isSocial(pkg)
            )
        }
    }

    /**
     * Limpieza: borra filas con textos placeholder legados, mergea los chats
     * no-sociales de un mismo paquete en un único bucket "app:<pkg>", y elimina
     * los chats huérfanos. Devuelve un informe simple.
     */
    suspend fun cleanupGarbage(): CleanupReport {
        var deletedPlaceholders = 0
        var mergedChats = 0
        var deletedOrphans = 0

        // 1) Borrar filas basura (guardadas antes del fix)
        try {
            deletedPlaceholders = notificationDao.deleteAutoPlaceholderRows()
        } catch (_: Exception) { /* Room IGNORE returns void on some versions */ }

        // 2) Para cada app NO social, fusionar todos sus chats en app:<pkg>
        val apps = appDao.getAllApps()
        for (app in apps) {
            val pkg = app.packageName
            if (com.example.whatsappsummary.util.SocialAppRegistry.isSocial(pkg)) continue

            val bucketChatId = com.example.whatsappsummary.util.SocialAppRegistry.appBucketChatId(pkg)
            val chats = chatDao.getChatsByAppId(app.id)
            if (chats.isEmpty()) continue
            // Si hay más de un chat, o el único no es el bucket, consolidamos
            val needsMerge = chats.size > 1 || chats.any { it.chatId != bucketChatId }
            if (!needsMerge) continue

            // Crear/asegurar el bucket
            if (chats.none { it.chatId == bucketChatId }) {
                chatDao.insertChat(
                    com.example.whatsappsummary.data.entity.Chat(
                        chatId = bucketChatId,
                        chatName = app.appName ?: app.packageName,
                        appId = app.id,
                        isGroup = false,
                        unreadCount = 0
                    )
                )
            }
            // Reasignar notificaciones de todos los chats de la app al bucket
            notificationDao.reassignNotificationsToBucket(app.id, bucketChatId)
            // Borrar los chats viejos
            chatDao.deleteOtherChatsForApp(app.id, bucketChatId)
            mergedChats += chats.size - 1
        }

        // 3) Chats huérfanos (sin notificaciones) fuera
        try {
            deletedOrphans = chatDao.deleteOrphanChats()
        } catch (_: Exception) { }

        return CleanupReport(deletedPlaceholders, mergedChats, deletedOrphans)
    }

    data class CleanupReport(
        val deletedPlaceholders: Int,
        val mergedChats: Int,
        val deletedOrphans: Int
    ) {
        val totalChanges: Int get() = deletedPlaceholders + mergedChats + deletedOrphans
    }
    
    suspend fun getChatById(chatId: String): Chat? = chatDao.getChatById(chatId)
    
    suspend fun getChatsByPackage(packageName: String): List<Chat> {
        val app = appDao.getAppByPackageName(packageName) ?: return emptyList()
        return chatDao.getChatsByAppId(app.id)
    }
    
    suspend fun insertChat(chat: Chat) = chatDao.insertChat(chat)
    
    suspend fun deleteChat(chat: Chat) = chatDao.deleteChat(chat)
    
    suspend fun deleteChatById(chatId: String) {
        chatDao.deleteChatById(chatId)
        notificationDao.deleteNotificationsByChatId(chatId)
        dailySummaryDao.deleteSummariesByChatId(chatId)
    }
    
    suspend fun resetUnreadCount(chatId: String) = chatDao.resetUnreadCount(chatId)
    
    // Notification/Message operations (unificadas)
    suspend fun getNotificationsByChatId(chatId: String): List<Notification> = 
        notificationDao.getNotificationsByChatId(chatId)
    
    suspend fun getNotificationsByDateRange(chatId: String, startTime: Long, endTime: Long): List<Notification> =
        notificationDao.getNotificationsByChatIdAndRange(chatId, startTime, endTime)
    
    suspend fun insertNotification(notification: Notification) {
        val textTrimmed = notification.text.trim()
        if (textTrimmed.isEmpty() || textTrimmed.equals("(sin contenido)", ignoreCase = true)) return
        
        val toInsert = if (textTrimmed != notification.text) {
            notification.copy(text = textTrimmed)
        } else {
            notification
        }

        notificationDao.insertNotification(toInsert)
    }
    
    // Daily summary operations
    fun getSummariesByChatId(chatId: String): LiveData<List<DailySummary>> =
        dailySummaryDao.getSummariesByChatId(chatId)
    
    suspend fun getSummariesByDateRange(chatId: String, startDate: String, endDate: String): List<DailySummary> =
        dailySummaryDao.getSummariesByDateRange(chatId, startDate, endDate)

    suspend fun getSummariesByTimestampRange(chatId: String, startTs: Long, endTs: Long): List<DailySummary> =
        dailySummaryDao.getSummariesByTimestampRange(chatId, startTs, endTs)
    
    suspend fun insertSummary(summary: DailySummary) = dailySummaryDao.insertSummary(summary)
    
    suspend fun deleteSummary(summary: DailySummary) = dailySummaryDao.deleteSummary(summary)

    suspend fun getNotificationsByChatIdAndRange(chatId: String, start: Long, end: Long) = notificationDao.getNotificationsByChatIdAndRange(chatId, start, end)

    suspend fun getNotificationsByRange(start: Long, end: Long) = notificationDao.getNotificationsByRange(start, end)

    suspend fun getNotificationsByPackageAndRange(pkg: String, start: Long, end: Long) = notificationDao.getNotificationsByPackageAndRange(pkg, start, end)
}
