package com.example.whatsappsummary.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.whatsappsummary.data.entity.Message

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesByChatId(chatId: String): LiveData<List<Message>>
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getMessagesByDateRange(chatId: String, startTime: Long, endTime: Long): List<Message>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
    
    @Delete
    suspend fun deleteMessage(message: Message)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getMessageCountByDateRange(chatId: String, startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND senderName = :senderName AND messageText = :messageText AND timestamp >= :sinceTime")
    suspend fun countSimilarRecent(chatId: String, senderName: String, messageText: String, sinceTime: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND messageText = :messageText AND timestamp = :timestamp")
    suspend fun countExactMessage(chatId: String, messageText: String, timestamp: Long): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(chatId: String, limit: Int): List<Message>

    @Query("UPDATE messages SET chatId = :newChatId WHERE chatId = :oldChatId")
    suspend fun moveMessagesToChat(oldChatId: String, newChatId: String)

    @Query("DELETE FROM messages WHERE TRIM(messageText) = '' OR LOWER(TRIM(messageText)) = '(sin contenido)'")
    suspend fun deleteEmptyOrPlaceholderMessages(): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatOrdered(chatId: String): List<Message>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)
}
