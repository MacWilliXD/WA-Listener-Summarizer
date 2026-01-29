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
                instance
            }
        }
    }
}
