package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Message
import com.example.whatsappsummary.repository.WhatsAppRepository
import kotlinx.coroutines.launch

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WhatsAppRepository
    
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages
    
    private val _summaries = MutableLiveData<List<DailySummary>>()
    val summaries: LiveData<List<DailySummary>> = _summaries
    
    private var currentChatId: String? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WhatsAppRepository(
            database.chatDao(),
            database.messageDao(),
            database.dailySummaryDao()
        )
    }

    fun loadChatData(chatId: String) {
        currentChatId = chatId
        
        // Cargar mensajes
        repository.getMessagesByChatId(chatId).observeForever { messageList ->
            _messages.value = messageList
        }
        
        // Cargar resúmenes
        repository.getSummariesByChatId(chatId).observeForever { summaryList ->
            _summaries.value = summaryList
        }
        
        // Resetear contador de no leídos
        viewModelScope.launch {
            repository.resetUnreadCount(chatId)
        }
    }
    
    fun loadSummariesByDateRange(chatId: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            val summaries = repository.getSummariesByDateRange(chatId, startDate, endDate)
            _summaries.postValue(summaries)
        }
    }
    
    fun deleteMessage(message: Message) = viewModelScope.launch {
        repository.deleteMessage(message)
    }
    
    fun deleteSummary(summary: DailySummary) = viewModelScope.launch {
        repository.deleteSummary(summary)
    }
}
