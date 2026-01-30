
package com.example.whatsappsummary.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.whatsappsummary.data.entity.Chat

@Dao
interface ChatDao {
        @Query("SELECT DISTINCT packageName FROM chats WHERE packageName != '' ORDER BY packageName")
        fun getAllPackages(): List<String>

        @Query("SELECT * FROM chats WHERE packageName = :packageName ORDER BY lastMessageTime DESC")
        fun getChatsByPackage(packageName: String): List<Chat>
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): LiveData<List<Chat>>
    
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChatsList(): List<Chat>
    
    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): Chat?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)
    
    @Update
    suspend fun updateChat(chat: Chat)
    
    @Delete
    suspend fun deleteChat(chat: Chat)
    
    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)
    
    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    suspend fun incrementUnreadCount(chatId: String)
    
    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun resetUnreadCount(chatId: String)
}
