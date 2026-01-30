package com.example.whatsappsummary.util

import android.content.Context
import android.content.SharedPreferences
import com.example.whatsappsummary.repository.WhatsAppRepository
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
 * SummaryGenerator: genera un resumen diario para los mensajes de un chat.
 * - Lee claves desde SharedPreferences `wa_listener_prefs` → `openrouter_api_keys` (JSON array)
 * - Intenta múltiples claves y modelos hasta obtener respuesta válida
 */
class SummaryGenerator(
	private val context: Context,
	private val repository: WhatsAppRepository,
	private val apiKeys: List<String> = emptyList(),
	private val models: List<String> = listOf("openai/gpt-4o-mini:latest")
) {
	private val urlBase = "https://openrouter.ai/api/v1/chat/completions"

	suspend fun generateDailySummary(chatId: String): String = withContext(Dispatchers.IO) {
		try {
			val (startOfDay, endOfDay) = todayRange()
			val messages = repository.getMessagesByDateRange(chatId, startOfDay, endOfDay)
			if (messages.isEmpty()) return@withContext "No hay mensajes hoy para este chat."

			val systemPrompt = buildSystemPrompt()
			val userContent = buildUserContent(messages)

			// Forzar uso de UNA sola API key y UN solo modelo (según petición del usuario)
			val key = "APIKEY"
			val model = "arcee-ai/trinity-large-preview:free"
			try {
				val resp = callChatCompletionApi(key, model, systemPrompt, userContent)
				if (!resp.isNullOrBlank()) {
					var finalResp = resp.trim()

					// Post-procesado local: detectar acciones y urgencia en los mensajes
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
		} catch (e: Exception) {
			return@withContext "ERROR interno: ${e.message}"
		}
	}

	/**
	 * Genera un resumen agregando mensajes de varios chats (rango: hoy).
	 */
	suspend fun generateSummaryForChats(chatIds: List<String>): String = withContext(Dispatchers.IO) {
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

			val systemPrompt = buildSystemPrompt()
			val userContent = buildUserContent(allMessages.sortedBy { it.timestamp })

			// Forzar uso de UNA sola API key y UN solo modelo (igual que generateDailySummary)
			val key = "APIKEY"
			val model = "arcee-ai/trinity-large-preview:free"
			try {
				val resp = callChatCompletionApi(key, model, systemPrompt, userContent)
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

	private fun buildSystemPrompt(): String {
		return """
Eres un asistente que genera resúmenes diarios de conversaciones para la app WA-Listener-Summarizer.
Tu tarea es leer los mensajes proporcionados (hora, remitente, texto) y producir un resumen conciso y útil: puntos clave, participantes activos, número total de mensajes, y recomendaciones si procede (por ejemplo, marcar para revisión).
Si detectas actividades, pedidos o tareas solicitadas en los mensajes, enlista esas actividades bajo el encabezado "Acciones:" al final del resumen. Si alguna de las actividades parece urgente (palabras como "urgente", "ASAP", "lo antes posible", "necesito"), coloca "URGENTE!!" en la primera línea del resumen y luego el resto del contenido. Responde en español y preferiblemente en un máximo de 6 oraciones (excepto la lista de acciones que puede extenderse si hay varias).
""".trimIndent()
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

	private fun callChatCompletionApi(apiKey: String, model: String, systemPrompt: String, userContent: String): String? {
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
