package com.example.whatsappsummary.repository

import androidx.lifecycle.LiveData
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.MessageDao
import com.example.whatsappsummary.data.dao.NotificationDao
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Message

/**
 * Repository centralizado para manejar todas las notificaciones del dispositivo.
 * Gestiona chats/fuentes, mensajes, notificaciones y resúmenes diarios.
 */
class NotificationRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val dailySummaryDao: DailySummaryDao,
    private val notificationDao: NotificationDao
) {
    fun getAllPackages(): List<String> = chatDao.getAllPackages()

    fun getChatsByPackage(packageName: String): List<Chat> = chatDao.getChatsByPackage(packageName)
    // Chat operations
    val allChats: LiveData<List<Chat>> = chatDao.getAllChats()
    
    suspend fun getChatById(chatId: String): Chat? = chatDao.getChatById(chatId)
    
    suspend fun insertChat(chat: Chat) = chatDao.insertChat(chat)
    
    suspend fun deleteChat(chat: Chat) = chatDao.deleteChat(chat)
    
    suspend fun deleteChatById(chatId: String) {
        chatDao.deleteChatById(chatId)
        messageDao.deleteMessagesByChatId(chatId)
        dailySummaryDao.deleteSummariesByChatId(chatId)
    }
    
    suspend fun resetUnreadCount(chatId: String) = chatDao.resetUnreadCount(chatId)
    
    // Message operations
    fun getMessagesByChatId(chatId: String): LiveData<List<Message>> = 
        messageDao.getMessagesByChatId(chatId)
    
    suspend fun getMessagesByDateRange(chatId: String, startTime: Long, endTime: Long): List<Message> =
        messageDao.getMessagesByDateRange(chatId, startTime, endTime)
    
    suspend fun insertMessage(message: Message) {
        val textTrimmed = message.messageText.trim()
        if (textTrimmed.isEmpty() || textTrimmed.equals("(sin contenido)", ignoreCase = true)) return
        val toInsert = if (textTrimmed != message.messageText) message.copy(messageText = textTrimmed) else message
        // Si los últimos 3 mensajes del chat son idénticos al mensaje a insertar, omitir
        try {
            val lastThree = messageDao.getLastMessages(toInsert.chatId, 3)
            if (lastThree.size >= 3) {
                val allEqual = lastThree.all { it.messageText.trim().equals(textTrimmed, ignoreCase = true) }
                if (allEqual) return
            }
        } catch (e: Exception) {
            // Si falla la consulta, no bloqueamos la inserción; continuar normalmente
        }

        messageDao.insertMessage(toInsert)
    }
    
    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    
    // Daily summary operations
    fun getSummariesByChatId(chatId: String): LiveData<List<DailySummary>> =
        dailySummaryDao.getSummariesByChatId(chatId)
    
    suspend fun getSummariesByDateRange(chatId: String, startDate: String, endDate: String): List<DailySummary> =
        dailySummaryDao.getSummariesByDateRange(chatId, startDate, endDate)

    suspend fun getSummariesByTimestampRange(chatId: String, startTs: Long, endTs: Long): List<DailySummary> =
        dailySummaryDao.getSummariesByTimestampRange(chatId, startTs, endTs)
    
    suspend fun insertSummary(summary: DailySummary) = dailySummaryDao.insertSummary(summary)
    
    suspend fun deleteSummary(summary: DailySummary) = dailySummaryDao.deleteSummary(summary)

    // Notification operations
    suspend fun insertNotification(notification: com.example.whatsappsummary.data.entity.Notification) = notificationDao.insertNotification(notification)

    suspend fun getNotificationsByChatIdAndRange(chatId: String, start: Long, end: Long) = notificationDao.getNotificationsByChatIdAndRange(chatId, start, end)

    suspend fun getNotificationsByRange(start: Long, end: Long) = notificationDao.getNotificationsByRange(start, end)

    suspend fun getNotificationsByPackageAndRange(pkg: String, start: Long, end: Long) = notificationDao.getNotificationsByPackageAndRange(pkg, start, end)
}
