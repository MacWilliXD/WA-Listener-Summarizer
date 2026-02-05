package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.data.entity.ChatWithLastMessage
import com.example.whatsappsummary.repository.NotificationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NotificationRepository
    val allChats: LiveData<List<Chat>>
    
    private val _allChatsWithMessage = MutableLiveData<List<ChatWithLastMessage>>()
    val allChatsWithMessage: LiveData<List<ChatWithLastMessage>> = _allChatsWithMessage

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NotificationRepository(
            database.appDao(),
            database.chatDao(),
            database.notificationDao(),
            database.dailySummaryDao()
        )
        allChats = repository.allChats
        
        // Cargar chats con último mensaje cuando cambian los chats
        allChats.observeForever {
            loadChatsWithMessage()
        }
    }
    
    private fun loadChatsWithMessage() {
        viewModelScope.launch {
            val chatsWithMessage = withContext(Dispatchers.IO) {
                repository.getAllChatsWithLastMessage()
            }
            _allChatsWithMessage.postValue(chatsWithMessage)
        }
    }

    fun deleteChat(chatId: String) = viewModelScope.launch {
        repository.deleteChatById(chatId)
    }
    
    fun resetUnreadCount(chatId: String) = viewModelScope.launch {
        repository.resetUnreadCount(chatId)
    }

    fun fetchAllPackages(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { repository.getAllApps() }
            onResult(apps.map { it.packageName })
        }
    }

    fun getChatPackageMap(onResult: (Map<String, String>) -> Unit) {
        viewModelScope.launch {
            val chatPackageMap = withContext(Dispatchers.IO) {
                val allChats = repository.getAllChatsList()
                val allApps = repository.getAllApps()
                val appIdToPackage = allApps.associate { it.id to it.packageName }
                allChats.associate { chat -> 
                    chat.chatId to (appIdToPackage[chat.appId] ?: "")
                }
            }
            onResult(chatPackageMap)
        }
    }
}
