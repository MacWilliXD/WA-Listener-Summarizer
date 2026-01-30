package com.example.whatsappsummary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.MessageDao
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(
    entities = [Chat::class, Message::class, DailySummary::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun dailySummaryDao(): DailySummaryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsapp_summary_database"
                )
                    // Si el esquema cambió y no hay migraciones, limpiar la BD.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                // Ejecutar limpieza de títulos de chat una sola vez (si es necesario)
                try {
                    val prefs = context.applicationContext.getSharedPreferences("wa_listener_prefs", Context.MODE_PRIVATE)
                    val cleaned = prefs.getBoolean("chat_titles_normalized_v1", false)
                    if (!cleaned) {
                        // Ejecutar en background
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                instance.performChatTitleNormalization(context.applicationContext as android.content.Context)
                                prefs.edit().putBoolean("chat_titles_normalized_v1", true).apply()
                            } catch (e: Exception) {
                                android.util.Log.e("AppDatabase", "Error normalizing chat titles", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error scheduling normalization", e)
                }

                instance
            }
        }
    }

    private fun performChatTitleNormalization(context: Context) {
        try {
            val chatDao = this.chatDao()
            val messageDao = this.messageDao()
            val summaryDao = this.dailySummaryDao()

            // Traer todos los chats
            val chats = kotlinx.coroutines.runBlocking { chatDao.getAllChatsList() }
            for (chat in chats) {
                val normalized = com.example.whatsappsummary.util.ChatUtils.normalizeChatTitle(chat.chatId)
                if (normalized.isEmpty() || normalized == chat.chatId) continue

                // Si ya existe un chat con el nombre normalizado, fusionar
                    val existing = kotlinx.coroutines.runBlocking { chatDao.getChatById(normalized) }
                if (existing == null) {
                    // Insertar nuevo chat con ID normalizado
                    val newChat = Chat(
                        chatId = normalized,
                        chatName = normalized,
                        lastMessage = chat.lastMessage,
                        lastMessageTime = chat.lastMessageTime,
                        unreadCount = chat.unreadCount
                    )
                    kotlinx.coroutines.runBlocking { chatDao.insertChat(newChat) }

                    // Mover mensajes
                    kotlinx.coroutines.runBlocking { messageDao.moveMessagesToChat(chat.chatId, normalized) }

                    // Mover resúmenes: intentar insertar y en caso de fecha duplicada, fusionar
                    val oldSummaries = kotlinx.coroutines.runBlocking { summaryDao.getSummariesListByChatId(chat.chatId) }
                    for (s in oldSummaries) {
                        val existingSummary = kotlinx.coroutines.runBlocking { summaryDao.getSummaryByDate(normalized, s.date) }
                        if (existingSummary == null) {
                            val newSummary = DailySummary(
                                chatId = normalized,
                                date = s.date,
                                messageCount = s.messageCount,
                                summary = s.summary,
                                timestamp = s.timestamp
                            )
                            kotlinx.coroutines.runBlocking { summaryDao.insertSummary(newSummary) }
                        } else {
                            // Fusionar: sumar counts, combinar textos y tomar timestamp máximo
                            val merged = existingSummary.copy(
                                messageCount = existingSummary.messageCount + s.messageCount,
                                summary = (existingSummary.summary + "\n" + s.summary).trim(),
                                timestamp = maxOf(existingSummary.timestamp, s.timestamp)
                            )
                            kotlinx.coroutines.runBlocking { summaryDao.updateSummary(merged) }
                        }
                    }

                    // Eliminar chat antiguo y sus resúmenes (los mensajes ya fueron movidos)
                    kotlinx.coroutines.runBlocking { summaryDao.deleteSummariesByChatId(chat.chatId) }
                    kotlinx.coroutines.runBlocking { chatDao.deleteChatById(chat.chatId) }
                } else {
                    // Fusionar en existing
                    kotlinx.coroutines.runBlocking { messageDao.moveMessagesToChat(chat.chatId, normalized) }
                    val oldSummaries = kotlinx.coroutines.runBlocking { summaryDao.getSummariesListByChatId(chat.chatId) }
                    for (s in oldSummaries) {
                        val existingSummary = kotlinx.coroutines.runBlocking { summaryDao.getSummaryByDate(normalized, s.date) }
                        if (existingSummary == null) {
                            val newSummary = DailySummary(
                                chatId = normalized,
                                date = s.date,
                                messageCount = s.messageCount,
                                summary = s.summary,
                                timestamp = s.timestamp
                            )
                            kotlinx.coroutines.runBlocking { summaryDao.insertSummary(newSummary) }
                        } else {
                            val merged = existingSummary.copy(
                                messageCount = existingSummary.messageCount + s.messageCount,
                                summary = (existingSummary.summary + "\n" + s.summary).trim(),
                                timestamp = maxOf(existingSummary.timestamp, s.timestamp)
                            )
                            kotlinx.coroutines.runBlocking { summaryDao.updateSummary(merged) }
                        }
                    }
                    kotlinx.coroutines.runBlocking { summaryDao.deleteSummariesByChatId(chat.chatId) }
                    kotlinx.coroutines.runBlocking { chatDao.deleteChatById(chat.chatId) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppDatabase", "Error during chat title normalization", e)
        }
    }
}
