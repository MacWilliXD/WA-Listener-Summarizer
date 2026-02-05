package com.example.whatsappsummary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.whatsappsummary.data.dao.AppDao
import com.example.whatsappsummary.data.dao.ChatDao
import com.example.whatsappsummary.data.dao.DailySummaryDao
import com.example.whatsappsummary.data.dao.NotificationDao
import com.example.whatsappsummary.data.entity.App
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Notification

@Database(
    entities = [App::class, Chat::class, Notification::class, DailySummary::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun chatDao(): ChatDao
    abstract fun notificationDao(): NotificationDao
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
