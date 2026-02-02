package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.Notification

class AppNotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    suspend fun getNotificationsForApp(packageName: String): List<Notification> {
        return database.notificationDao().getNotificationsByPackage(packageName)
    }
}