package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.data.entity.Notification
import com.example.whatsappsummary.repository.NotificationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NotificationRepository
    
    private val _messages = MutableLiveData<List<Notification>>()
    val messages: LiveData<List<Notification>> = _messages
    
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
            database.appDao(),
            database.chatDao(),
            database.notificationDao(),
            database.dailySummaryDao()
        )
    }

    fun loadChatData(chatId: String) {
        currentChatId = chatId
        
        // Cargar mensajes/notificaciones
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val notifList = repository.getNotificationsByChatId(chatId)
                // apply timestamp and text filter if set
                val filtered = notifList.filter { notif ->
                    val inRange = if (filterStart != null && filterEnd != null) notif.timestamp in filterStart!!..filterEnd!! else true
                    val matchesText = filterText?.let { ft ->
                        val q = normalize(ft)
                        normalize(notif.text).contains(q) || normalize(notif.sender ?: "").contains(q)
                    } ?: true
                    inRange && matchesText
                }
                _messages.postValue(filtered)
            }
        }
        
        // Cargar resúmenes (observables)
        repository.getSummariesByChatId(chatId).observeForever { summaryList ->
            val filtered = summaryList.filter { s ->
                val inRange = if (filterStart != null && filterEnd != null) s.timestamp in filterStart!!..filterEnd!! else true
                val matchesText = filterText?.let { ft ->
                    val q = normalize(ft)
                    normalize(s.summary).contains(q) || normalize(s.date).contains(q)
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
                // notificaciones via repository (suspend)
                val notifs = withContext(Dispatchers.IO) { repository.getNotificationsByDateRange(cid, startTs, endTs) }
                // apply text filter if present
                val q = filterText?.let { normalize(it) }
                val filteredNotifs = q?.let { pattern ->
                    notifs.filter { n ->
                        normalize(n.text).contains(pattern) || normalize(n.sender ?: "").contains(pattern)
                    }
                } ?: notifs
                _messages.postValue(filteredNotifs.sortedBy { it.timestamp })

                val sums = withContext(Dispatchers.IO) { repository.getSummariesByTimestampRange(cid, startTs, endTs) }
                val filteredSums = q?.let { pattern ->
                    sums.filter { s ->
                        normalize(s.summary).contains(pattern) || normalize(s.date).contains(pattern)
                    }
                } ?: sums
                _summaries.postValue(filteredSums)
            } else {
                // clear filter -> reload
                loadChatData(cid)
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
    private fun generateLocalSummary(messages: List<Notification>): String {
        if (messages.isEmpty()) return "(sin mensajes visibles)"

        // Obtener participantes (hasta 4)
        val participants = messages.mapNotNull { it.sender }.distinct().take(4)
        val partStr = when (participants.size) {
            0 -> ""
            1 -> "Participante: ${participants[0]}. "
            else -> "Participantes: ${participants.joinToString(", ")}. "
        }

        // Tomar los últimos mensajes (hasta 6), formar frases cortas
        val recent = messages.sortedBy { it.timestamp }.takeLast(6)
        val msgParts = recent.map { m ->
            val author = m.sender ?: ""
            val body = m.text.replace("\\s+".toRegex(), " ").trim()
            if (author.isNotBlank()) "$author: $body" else body
        }.filter { it.isNotBlank() }

        val messagesStr = if (msgParts.isEmpty()) "Sin contenido significativo." else msgParts.joinToString("; ")

        // Construir párrafo limitado a ~400 chars
        var paragraph = (partStr + messagesStr).trim()
        if (paragraph.length > 400) paragraph = paragraph.take(397).trimEnd() + "..."
        return paragraph
    }
    
    fun deleteNotification(notification: Notification) = viewModelScope.launch {
        // No hay deleteNotification en repository por ahora, se puede agregar si es necesario
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
                        repository.getNotificationsByDateRange(cid, filterStart!!, filterEnd!!)
                    } else {
                        // everything up to now
                        repository.getNotificationsByDateRange(cid, 0L, now)
                    }
                }

                // apply text filter if present
                val q = filterText?.let { normalize(it) }
                val visible = q?.let { pattern ->
                    msgs.filter { m ->
                        normalize(m.text).contains(pattern) || normalize(m.sender ?: "").contains(pattern)
                    }
                } ?: msgs

                // Build a concise context string from visible messages (most recent last)
                val contextBuilder = StringBuilder()
                if (visible.isEmpty()) {
                    contextBuilder.append("(sin mensajes visibles)")
                } else {
                    visible.sortedBy { it.timestamp }.forEach { m ->
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.timestamp))
                        val author = m.sender ?: ""
                        val body = m.text
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
