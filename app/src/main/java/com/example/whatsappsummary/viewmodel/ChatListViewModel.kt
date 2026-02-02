package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.repository.NotificationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NotificationRepository
    val allChats: LiveData<List<Chat>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NotificationRepository(
            database.chatDao(),
            database.messageDao(),
            database.dailySummaryDao(),
            database.notificationDao()
        )
        allChats = repository.allChats
    }

    fun deleteChat(chatId: String) = viewModelScope.launch {
        repository.deleteChatById(chatId)
    }
    
    fun resetUnreadCount(chatId: String) = viewModelScope.launch {
        repository.resetUnreadCount(chatId)
    }

    fun fetchAllPackages(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val pkgs = withContext(Dispatchers.IO) { repository.getAllPackages() }
            onResult(pkgs)
        }
    }
}
