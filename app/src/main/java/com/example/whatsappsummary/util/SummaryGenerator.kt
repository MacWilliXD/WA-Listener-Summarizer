package com.example.whatsappsummary.util

import android.content.Context
import android.content.SharedPreferences
import com.example.whatsappsummary.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * SummaryGenerator: genera resúmenes diarios basados en notificaciones capturadas.
 * - Trabaja con notificaciones de cualquier aplicación del dispositivo
 * - Genera resúmenes por fuente (app/chat), por rango temporal o agregados
 * - Usa API de chat/completions para generar contenido con IA
 */
class SummaryGenerator(
    private val context: Context,
    private val repository: NotificationRepository,
    private val apiKeys: List<String> = emptyList(),
    private val models: List<String> = listOf("openai/gpt-4o-mini:latest")
) {
    private val urlBase = "https://openrouter.ai/api/v1/chat/completions"

    suspend fun generateDailySummary(
        chatId: String,
        summaryLength: Int? = null,
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val (startOfDay, endOfDay) = todayRange()
            // Prefer notifications as the primary data source
            val notifications = try { 
                repository.getNotificationsByChatIdAndRange(chatId, startOfDay, endOfDay) 
            } catch (e: Exception) { 
                emptyList<com.example.whatsappsummary.data.entity.Notification>() 
            }
            
            if (notifications.isEmpty()) {
                // Fallback a mensajes antiguos si no hay notificaciones
                val messages = repository.getMessagesByDateRange(chatId, startOfDay, endOfDay)
                if (messages.isEmpty()) return@withContext "No hay mensajes hoy para este chat."
                val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt)
                val userContent = buildUserContent(messages)
                try {
                    val key = " YOUR API KEY"
                    val model = "arcee-ai/trinity-large-preview:free"
                    val resp = callChatCompletionApi(key, model, systemPrompt, userContent, summaryLength)
                    if (!resp.isNullOrBlank()) {
                        var finalResp = resp.trim()
                        val actions = extractActionItems(messages)
                        val urgent = detectUrgency(messages)
                        if (urgent && !finalResp.startsWith("URGENTE!!", ignoreCase = true)) {
                            finalResp = "URGENTE!!\n" + finalResp
                        }
                        if (actions.isNotEmpty() && !finalResp.contains("Acciones:", ignoreCase = true)) {
                            val actionsText = actions.joinToString("\n") { "- $it" }
                            finalResp = "$finalResp\n\nAcciones:\n$actionsText"
                        }
                        return@withContext finalResp
                    }
                } catch (e: Exception) {
                    // fallo
                }
                return@withContext "ERROR: No se pudo generar resumen con la clave/modelo proporcionado."
            } else {
                val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt)
                val userContent = buildUserContentFromNotifications(notifications)
                try {
                    val key = " YOUR API KEY"
                    val model = "arcee-ai/trinity-large-preview:free"
                    val resp = callChatCompletionApi(key, model, systemPrompt, userContent, summaryLength)
                    if (!resp.isNullOrBlank()) {
                        var finalResp = resp.trim()
                        val actions = extractActionItemsFromNotifications(notifications)
                        val urgent = detectUrgencyFromNotifications(notifications)
                        if (urgent && !finalResp.startsWith("URGENTE!!", ignoreCase = true)) {
                            finalResp = "URGENTE!!\n" + finalResp
                        }
                        if (actions.isNotEmpty() && !finalResp.contains("Acciones:", ignoreCase = true)) {
                            val actionsText = actions.joinToString("\n") { "- $it" }
                            finalResp = "$finalResp\n\nAcciones:\n$actionsText"
                        }
                        return@withContext finalResp
                    }
                } catch (e: Exception) {
                    // fallo
                }
                return@withContext "ERROR: No se pudo generar resumen con la clave/modelo proporcionado."
            }
        } catch (e: Exception) {
            return@withContext "ERROR interno: ${e.message}"
        }
    }

    suspend fun generateSummaryForChats(
        chatIds: List<String>,
        summaryLength: Int? = null,
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val (startOfDay, endOfDay) = todayRange()
            val allMessages = mutableListOf<com.example.whatsappsummary.data.entity.Message>()
            for (cid in chatIds) {
                try {
                    val msgs = repository.getMessagesByDateRange(cid, startOfDay, endOfDay)
                    allMessages.addAll(msgs)
                } catch (e: Exception) {
                    // ignorar chat si falla
                }
            }
            if (allMessages.isEmpty()) return@withContext "No hay mensajes hoy en ningún chat."

            val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt)
            val userContent = buildUserContent(allMessages.sortedBy { it.timestamp })

            val key = " YOUR API KEY"
            val model = "arcee-ai/trinity-large-preview:free"
            try {
                val resp = callChatCompletionApi(key, model, systemPrompt, userContent, summaryLength)
                if (!resp.isNullOrBlank()) {
                    var finalResp = resp.trim()

                    val actions = extractActionItems(allMessages)
                    val urgent = detectUrgency(allMessages)

                    if (urgent && !finalResp.startsWith("URGENTE!!", ignoreCase = true)) {
                        finalResp = "URGENTE!!\n" + finalResp
                    }

                    if (actions.isNotEmpty() && !finalResp.contains("Acciones:", ignoreCase = true)) {
                        val actionsText = actions.joinToString("\n") { "- $it" }
                        finalResp = "$finalResp\n\nAcciones:\n$actionsText"
                    }

                    return@withContext finalResp
                }
            } catch (e: Exception) {
                // fallo
            }

            return@withContext "ERROR: No se pudo generar resumen con la clave/modelo proporcionado."
        } catch (e: Exception) {
            return@withContext "ERROR interno: ${e.message}"
        }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    private fun buildUserContent(messages: List<com.example.whatsappsummary.data.entity.Message>): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Mensajes del día:\n")
        for (m in messages) {
            val textRaw = m.messageText?.trim()
            if (textRaw.isNullOrEmpty() || textRaw.equals("(sin contenido)", ignoreCase = true)) continue
            val time = df.format(Date(m.timestamp))
            val sender = m.senderName ?: "(desconocido)"
            sb.append("[").append(time).append("] ").append(sender).append(": ").append(textRaw).append("\n")
        }
        return sb.toString()
    }

    private fun buildUserContentFromNotifications(notifs: List<com.example.whatsappsummary.data.entity.Notification>): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Notificaciones del día:\n")
        for (n in notifs) {
            val time = df.format(Date(n.timestamp))
            val app = n.appName ?: n.packageName
            val titlePart = n.title?.let { " ($it)" } ?: ""
            val text = n.text.trim()
            sb.append("[").append(time).append("] ").append(app).append(titlePart).append(": ").append(text).append("\n")
        }
        return sb.toString()
    }

    private fun detectUrgency(messages: List<com.example.whatsappsummary.data.entity.Message>): Boolean {
        val urgentKeywords = listOf("urgente", "asap", "lo antes posible", "necesito", "necesitamos", "con prioridad", "urgencia", "prioridad")
        for (m in messages) {
            val txtRaw = m.messageText?.trim()
            if (txtRaw.isNullOrEmpty() || txtRaw.equals("(sin contenido)", ignoreCase = true)) continue
            val txt = txtRaw.toLowerCase(Locale.getDefault())
            for (k in urgentKeywords) if (txt.contains(k)) return true
        }
        return false
    }

    private fun detectUrgencyFromNotifications(notifs: List<com.example.whatsappsummary.data.entity.Notification>): Boolean {
        val urgentKeywords = listOf("urgente", "asap", "lo antes posible", "necesito", "necesitamos", "con prioridad", "urgencia", "prioridad")
        for (n in notifs) {
            val txtRaw = n.text?.trim() ?: continue
            if (txtRaw.isEmpty()) continue
            val txt = txtRaw.toLowerCase(Locale.getDefault())
            for (k in urgentKeywords) if (txt.contains(k)) return true
        }
        return false
    }

    private fun extractActionItems(messages: List<com.example.whatsappsummary.data.entity.Message>): List<String> {
        val actionKeywords = listOf("por favor", "favor de", "necesito", "necesitamos", "enviar", "revisar", "asignar", "hacer", "entregar", "reservar", "confirmar", "llamar", "contactar", "agendar", "programar", "pedir", "pedido", "tarea", "pendiente")
        val actions = mutableListOf<String>()
        for (m in messages) {
            val txt = m.messageText?.trim()
            if (txt.isNullOrEmpty() || txt.equals("(sin contenido)", ignoreCase = true)) continue
            val low = txt.toLowerCase(Locale.getDefault())
            for (k in actionKeywords) {
                if (low.contains(k)) {
                    val sentence = txt.split(Regex("[\\.\\!\\?]")).firstOrNull()?.trim() ?: txt
                    val sender = m.senderName ?: "(desconocido)"
                    actions.add("${sender}: ${sentence}")
                    break
                }
            }
        }
        return actions.distinct()
    }

    private fun extractActionItemsFromNotifications(notifs: List<com.example.whatsappsummary.data.entity.Notification>): List<String> {
        val actionKeywords = listOf("por favor", "favor de", "necesito", "necesitamos", "enviar", "revisar", "asignar", "hacer", "entregar", "reservar", "confirmar", "llamar", "contactar", "agendar", "programar", "pedir", "pedido", "tarea", "pendiente")
        val actions = mutableListOf<String>()
        for (n in notifs) {
            val txt = n.text.trim()
            if (txt.isEmpty()) continue
            val low = txt.toLowerCase(Locale.getDefault())
            for (k in actionKeywords) {
                if (low.contains(k)) {
                    val sentence = txt.split(Regex("[\\.\\!\\?]")).firstOrNull()?.trim() ?: txt
                    val sender = n.title ?: n.appName ?: n.packageName
                    actions.add("${sender}: ${sentence}")
                    break
                }
            }
        }
        return actions.distinct()
    }

    private fun buildSystemPrompt(detailLevel: String = "Intermedio", extraPrompt: String? = null): String {
        val base = """
Eres un asistente que genera resúmenes diarios de conversaciones para la app WA-Listener-Summarizer.
Tu tarea es leer los mensajes proporcionados (hora, remitente, texto) y producir un resumen conciso y útil: puntos clave, participantes activos, número total de mensajes, y recomendaciones si procede (por ejemplo, marcar para revisión).
Si detectas actividades, pedidos o tareas solicitadas en los mensajes, enlista esas actividades bajo el encabezado "Acciones:" al final del resumen. Si alguna de las actividades parece urgente (palabras como "urgente", "ASAP", "lo antes posible", "necesito"), coloca "URGENTE!!" en la primera línea del resumen y luego el resto del contenido. Responde en español.
""".trimIndent()

        val detailGuide = when (detailLevel.lowercase(Locale.getDefault())) {
            "resumido" -> "Haz un resumen muy breve (1-2 frases), prioriza lo esencial."
            "detallado" -> "Incluye más detalles y contexto, hasta 6-8 oraciones y lista de participantes/acciones."
            else -> "Resumen de longitud media (3-4 oraciones), puntos clave y acciones."
        }

        val extra = extraPrompt?.takeIf { it.isNotBlank() }?.let { "\nInstrucción adicional: $it" } ?: ""

        return listOf(base, detailGuide, extra).joinToString("\n\n")
    }

    private fun loadApiKeysFromPrefs(): List<String> {
        return try {
            val prefs: SharedPreferences = context.getSharedPreferences("wa_listener_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("openrouter_api_keys", null)
            if (raw.isNullOrBlank()) return listOf("YOUR_OPENROUTER_API_KEY")
            val arr = JSONArray(raw)
            if (arr.length() == 0) return listOf("YOUR_OPENROUTER_API_KEY")
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun callChatCompletionApi(apiKey: String, model: String, systemPrompt: String, userContent: String, maxTokens: Int? = null): String? {
        val url = URL(urlBase)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        val payload = JSONObject()
        payload.put("model", model)
        val messages = JSONArray()
        val sys = JSONObject()
        sys.put("role", "system")
        sys.put("content", systemPrompt)
        messages.put(sys)
        val usr = JSONObject()
        usr.put("role", "user")
        usr.put("content", userContent)
        messages.put(usr)
        payload.put("messages", messages)
        if (maxTokens != null && maxTokens > 0) {
            try {
                payload.put("max_tokens", maxTokens)
            } catch (_: Exception) {
            }
        }

        val body = payload.toString()

        BufferedOutputStream(conn.outputStream).use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val code = conn.responseCode
        val reader = if (code in 200..299) BufferedReader(InputStreamReader(conn.inputStream)) else BufferedReader(InputStreamReader(conn.errorStream))
        val respSb = StringBuilder()
        reader.useLines { lines -> lines.forEach { respSb.append(it) } }
        val respText = respSb.toString()

        if (code !in 200..299) return null

        val json = JSONObject(respText)
        if (json.has("choices")) {
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                if (choice.has("message")) {
                    val message = choice.getJSONObject("message")
                    if (message.has("content")) return message.getString("content")
                }
                if (choice.has("text")) return choice.getString("text")
            }
        }

        return null
    }
}
