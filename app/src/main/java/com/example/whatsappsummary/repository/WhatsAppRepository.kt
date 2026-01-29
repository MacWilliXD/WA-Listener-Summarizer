package com.example.whatsappsummary.repository

import androidx.lifecycle.LiveData
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.MessageDao
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Message

class WhatsAppRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val dailySummaryDao: DailySummaryDao
) {
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
    
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)
    
    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    
    // Daily summary operations
    fun getSummariesByChatId(chatId: String): LiveData<List<DailySummary>> =
        dailySummaryDao.getSummariesByChatId(chatId)
    
    suspend fun getSummariesByDateRange(chatId: String, startDate: String, endDate: String): List<DailySummary> =
        dailySummaryDao.getSummariesByDateRange(chatId, startDate, endDate)
    
    suspend fun insertSummary(summary: DailySummary) = dailySummaryDao.insertSummary(summary)
    
    suspend fun deleteSummary(summary: DailySummary) = dailySummaryDao.deleteSummary(summary)
}
