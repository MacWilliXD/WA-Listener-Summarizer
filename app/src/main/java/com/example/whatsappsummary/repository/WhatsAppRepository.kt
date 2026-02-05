package com.example.whatsappsummary.repository

import androidx.lifecycle.LiveData
import com.example.whatsappsummary.data.dao.AppDao
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.NotificationDao
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Notification

class WhatsAppRepository(
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
    
    // Notification/Message operations (ahora unificadas)
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
