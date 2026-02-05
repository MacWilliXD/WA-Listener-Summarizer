package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Message
import com.example.whatsappsummary.repository.NotificationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NotificationRepository
    
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages
    
    private val _summaries = MutableLiveData<List<DailySummary>>()
    val summaries: LiveData<List<DailySummary>> = _summaries
    
    private val _isGenerating = MutableLiveData<Boolean>(false)
    val isGenerating: LiveData<Boolean> = _isGenerating
    private val _generationError = MutableLiveData<String?>()
    val generationError: LiveData<String?> = _generationError

    fun clearGenerationError() {
        _generationError.postValue(null)
    }
    
    private var currentChatId: String? = null
    private var filterStart: Long? = null
    private var filterEnd: Long? = null
    private var filterText: String? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NotificationRepository(
            database.chatDao(),
            database.messageDao(),
            database.dailySummaryDao(),
            database.notificationDao()
        )
    }

    fun loadChatData(chatId: String) {
        currentChatId = chatId
        
        // Cargar mensajes (observables)
        repository.getMessagesByChatId(chatId).observeForever { messageList ->
            // apply timestamp and text filter if set
            val filtered = messageList.filter { msg ->
                val inRange = if (filterStart != null && filterEnd != null) msg.timestamp in filterStart!!..filterEnd!! else true
                val matchesText = filterText?.let { ft ->
                    val q = normalize(ft)
                    normalize(msg.messageText ?: "").contains(q) || normalize(msg.senderName ?: "").contains(q)
                } ?: true
                inRange && matchesText
            }
            _messages.value = filtered
        }
        
        // Cargar resúmenes (observables)
        repository.getSummariesByChatId(chatId).observeForever { summaryList ->
            val filtered = summaryList.filter { s ->
                val inRange = if (filterStart != null && filterEnd != null) s.timestamp in filterStart!!..filterEnd!! else true
                val matchesText = filterText?.let { ft ->
                    val q = normalize(ft)
                    normalize(s.summary ?: "").contains(q) || normalize(s.date ?: "").contains(q)
                } ?: true
                inRange && matchesText
            }
            _summaries.value = filtered
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
    
    fun setFilterByTimestamps(startTs: Long?, endTs: Long?) {
        filterStart = startTs
        filterEnd = endTs
        val cid = currentChatId ?: return
        // reload filtered data for messages and summaries
        viewModelScope.launch {
            if (startTs != null && endTs != null) {
                // messages via repository (suspend)
                val msgs = withContext(Dispatchers.IO) { repository.getMessagesByDateRange(cid, startTs, endTs) }
                // apply text filter if present
                val q = filterText?.let { normalize(it) }
                val filteredMsgs = q?.let { pattern ->
                    msgs.filter { m ->
                        normalize(m.messageText ?: "").contains(pattern) || normalize(m.senderName ?: "").contains(pattern)
                    }
                } ?: msgs
                _messages.postValue(filteredMsgs.sortedByDescending { it.timestamp })

                val sums = withContext(Dispatchers.IO) { repository.getSummariesByTimestampRange(cid, startTs, endTs) }
                val filteredSums = q?.let { pattern ->
                    sums.filter { s ->
                        normalize(s.summary ?: "").contains(pattern) || normalize(s.date ?: "").contains(pattern)
                    }
                } ?: sums
                _summaries.postValue(filteredSums)
            } else {
                // clear filter -> reload observables
                val allMsgs = repository.getMessagesByChatId(cid)
                // observeForever previously registered in loadChatData; rely on that to update _messages
                val allSums = repository.getSummariesByChatId(cid)
            }
        }
    }

    fun setTextFilter(text: String?) {
        filterText = text?.takeIf { it.isNotBlank() }
        // re-trigger load to apply filter on current cached data
        currentChatId?.let { loadChatData(it) }
    }

    private fun normalize(input: String): String {
        val n = Normalizer.normalize(input, Normalizer.Form.NFD)
        return n.replace("""\p{M}+""".toRegex(), "").lowercase(Locale.getDefault())
    }

    // Fallback local summarizer: genera un párrafo conciso a partir de los últimos mensajes visibles
    private fun generateLocalSummary(messages: List<Message>): String {
        if (messages.isEmpty()) return "(sin mensajes visibles)"

        // Obtener participantes (hasta 4)
        val participants = messages.mapNotNull { it.senderName }.distinct().take(4)
        val partStr = when (participants.size) {
            0 -> ""
            1 -> "Participante: ${participants[0]}. "
            else -> "Participantes: ${participants.joinToString(", ")}. "
        }

        // Tomar los últimos mensajes (hasta 6), formar frases cortas
        val recent = messages.sortedBy { it.timestamp }.takeLast(6)
        val msgParts = recent.map { m ->
            val author = m.senderName ?: ""
            val body = (m.messageText ?: "").replace("\\s+".toRegex(), " ").trim()
            if (author.isNotBlank()) "$author: $body" else body
        }.filter { it.isNotBlank() }

        val messagesStr = if (msgParts.isEmpty()) "Sin contenido significativo." else msgParts.joinToString("; ")

        // Construir párrafo limitado a ~400 chars
        var paragraph = (partStr + messagesStr).trim()
        if (paragraph.length > 400) paragraph = paragraph.take(397).trimEnd() + "..."
        return paragraph
    }
    
    fun deleteMessage(message: Message) = viewModelScope.launch {
        repository.deleteMessage(message)
    }
    
    fun deleteSummary(summary: DailySummary) = viewModelScope.launch {
        repository.deleteSummary(summary)
    }

    /**
     * Manual summary generation (simple heuristic): grab recent messages and insert a DailySummary.
     * Shows a loading state via [isGenerating].
     */
    fun generateManualSummary(summaryLength: Int? = null, detailLevel: String? = null, extraPrompt: String? = null) {
        val cid = currentChatId ?: return
        viewModelScope.launch {
            android.util.Log.d("ChatDetailVM", "Iniciando generación de resumen manual para chatId=$cid")
            _isGenerating.postValue(true)
            try {
                if (cid.isBlank()) {
                    val em = "No hay chat seleccionado para generar resumen"
                    _generationError.postValue(em)
                    return@launch
                }
                // Determine visible messages according to filters (if set) or all messages
                val now = System.currentTimeMillis()
                val msgs = withContext(Dispatchers.IO) {
                    if (filterStart != null && filterEnd != null) {
                        repository.getMessagesByDateRange(cid, filterStart!!, filterEnd!!)
                    } else {
                        // everything up to now
                        repository.getMessagesByDateRange(cid, 0L, now)
                    }
                }

                // apply text filter if present
                val q = filterText?.let { normalize(it) }
                val visible = q?.let { pattern ->
                    msgs.filter { m ->
                        normalize(m.messageText ?: "").contains(pattern) || normalize(m.senderName ?: "").contains(pattern)
                    }
                } ?: msgs

                // Build a concise context string from visible messages (most recent last)
                val contextBuilder = StringBuilder()
                if (visible.isEmpty()) {
                    contextBuilder.append("(sin mensajes visibles)")
                } else {
                    visible.sortedBy { it.timestamp }.forEach { m ->
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.timestamp))
                        val author = m.senderName ?: ""
                        val body = m.messageText ?: ""
                        contextBuilder.append("[").append(time).append("] ").append(author).append(": ").append(body).append("\n")
                    }
                }

                // Prepare date string and insert a placeholder summary so UI shows "Generando resumen..."
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
                val placeholder = DailySummary(
                    id = 0,
                    chatId = cid,
                    date = dateStr,
                    messageCount = visible.size,
                    summary = "Generando resumen...",
                    timestamp = now,
                    type = "manual"
                )
                withContext(Dispatchers.IO) { repository.insertSummary(placeholder) }
                android.util.Log.d("ChatDetailVM", "Placeholder de resumen insertado para $cid en $dateStr")

                // Use the new SummaryGenerator util to request a summary (reads API keys from prefs)
                var finalSummaryText: String? = null
                try {
                    val generator = com.example.whatsappsummary.util.SummaryGenerator(getApplication(), repository)
                    finalSummaryText = withContext(Dispatchers.IO) {
                        generator.generateDailySummary(
                            cid,
                            summaryLength,
                            detailLevel ?: "Intermedio",
                            extraPrompt,
                            filterStart,
                            filterEnd,
                            filterText
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatDetailVM", "Error usando SummaryGenerator", e)
                    finalSummaryText = "ERROR interno: ${e.message}"
                }

            android.util.Log.d("ChatDetailVM", "Insertando resumen final para $cid: ${finalSummaryText?.take(300)}...")
            val updatedSummary = DailySummary(
                id = 0,
                chatId = cid,
                date = dateStr,
                messageCount = visible.size,
                summary = finalSummaryText ?: "",
                timestamp = System.currentTimeMillis(),
                type = "manual"
            )
            withContext(Dispatchers.IO) { repository.insertSummary(updatedSummary) }
            android.util.Log.d("ChatDetailVM", "Resumen final insertado y recargando datos de chat $cid")
            // summaries LiveData will update automatically; optionally refresh
            loadChatData(cid)
            } catch (e: Exception) {
                android.util.Log.e("ChatDetailVM", "Unhandled error generating summary", e)
                _generationError.postValue(e.message ?: e.toString())
            } finally {
                _isGenerating.postValue(false)
            }
        }
    }
}
