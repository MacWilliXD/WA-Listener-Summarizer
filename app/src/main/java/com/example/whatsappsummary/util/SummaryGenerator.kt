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
    private val models: List<String> = listOf("openai/gpt-oss-120b:free")
) {
    private val urlBase = "https://openrouter.ai/api/v1/chat/completions"

    // Variables globales para API key y model
    private val defaultApiKey = "API KEY"
    private val defaultModel = "openai/gpt-oss-120b:free"

    suspend fun generateDailySummary(
        chatId: String,
        summaryLength: Int? = null,
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
        filterText: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val (startOfDay, endOfDay) = if (startTimestamp != null && endTimestamp != null) {
                Pair(startTimestamp, endTimestamp)
            } else {
                todayRange()
            }
            // Usar notificaciones como fuente primaria de datos
            val notifications = repository.getNotificationsByDateRange(chatId, startOfDay, endOfDay)
            
            // Aplicar filtro de texto si existe
            val filteredNotifications = if (!filterText.isNullOrBlank()) {
                val normalizedFilter = filterText.lowercase()
                notifications.filter { notif ->
                    (notif.text.lowercase().contains(normalizedFilter)) ||
                    (notif.title?.lowercase()?.contains(normalizedFilter) == true) ||
                    (notif.sender?.lowercase()?.contains(normalizedFilter) == true)
                }
            } else {
                notifications
            }
            
            if (filteredNotifications.isEmpty()) {
                return@withContext "No hay notificaciones que coincidan con los filtros aplicados."
            }

            val appNameById = loadAppNameMap()
            val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt)
            val userContent = buildUserContentFromNotifications(filteredNotifications, appNameById)

            try {
                val key = defaultApiKey
                val model = defaultModel
                val resp = callChatCompletionApi(key, model, systemPrompt, userContent, summaryLength)
                if (!resp.isNullOrBlank()) return@withContext resp.trim()
            } catch (e: Exception) {
                // Error al llamar API
            }
            return@withContext "ERROR: No se pudo generar resumen con la clave/modelo proporcionado."
        } catch (e: Exception) {
            return@withContext "ERROR interno: ${e.message}"
        }
    }

    suspend fun generateSummaryForChats(
        chatIds: List<String>,
        summaryLength: Int? = null,
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
        filterText: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val (startOfDay, endOfDay) = if (startTimestamp != null && endTimestamp != null) {
                Pair(startTimestamp, endTimestamp)
            } else {
                todayRange()
            }
            val allNotifications = mutableListOf<com.example.whatsappsummary.data.entity.Notification>()
            for (cid in chatIds) {
                try {
                    val notifs = repository.getNotificationsByDateRange(cid, startOfDay, endOfDay)
                    allNotifications.addAll(notifs)
                } catch (e: Exception) {
                    // ignorar chat si falla
                }
            }
            
            // Aplicar filtro de texto si existe
            val filteredNotifications = if (!filterText.isNullOrBlank()) {
                val normalizedFilter = filterText.lowercase()
                allNotifications.filter { notif ->
                    (notif.text.lowercase().contains(normalizedFilter)) ||
                    (notif.title?.lowercase()?.contains(normalizedFilter) == true) ||
                    (notif.sender?.lowercase()?.contains(normalizedFilter) == true)
                }
            } else {
                allNotifications
            }
            
            if (filteredNotifications.isEmpty()) return@withContext "No hay notificaciones que coincidan con los filtros aplicados."

            val appNameById = loadAppNameMap()
            val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt)
            val userContent = buildUserContentFromNotifications(
                filteredNotifications.sortedBy { it.timestamp },
                appNameById
            )

            val key = defaultApiKey
            val model = defaultModel
            try {
                val resp = callChatCompletionApi(key, model, systemPrompt, userContent, summaryLength)
                if (!resp.isNullOrBlank()) return@withContext resp.trim()
            } catch (e: Exception) {
                // fallo
            }

            return@withContext "ERROR: No se pudo generar resumen con la clave/modelo proporcionado."
        } catch (e: Exception) {
            return@withContext "ERROR interno: ${e.message}"
        }
    }

    private suspend fun loadAppNameMap(): Map<Long, String> = try {
        repository.getAllApps().associate { app ->
            app.id to (app.appName?.takeIf { it.isNotBlank() } ?: app.packageName)
        }
    } catch (_: Exception) {
        emptyMap()
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

    private fun buildUserContentFromNotifications(
        notifs: List<com.example.whatsappsummary.data.entity.Notification>,
        appNameById: Map<Long, String>
    ): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        // Agrupar por app origen para darle contexto jerárquico al modelo
        val grouped = notifs.groupBy { appNameById[it.appId] ?: "App desconocida" }
        val sb = StringBuilder()
        sb.append("=== NOTIFICACIONES A ANALIZAR ===\n")
        sb.append("Total: ").append(notifs.size).append(" · Apps: ").append(grouped.keys.size).append("\n\n")
        for ((appName, items) in grouped.entries.sortedByDescending { it.value.size }) {
            sb.append("── ").append(appName).append(" (").append(items.size).append(") ──\n")
            for (n in items.sortedBy { it.timestamp }) {
                val time = df.format(Date(n.timestamp))
                val sender = n.sender?.takeIf { it.isNotBlank() } ?: n.title?.takeIf { it.isNotBlank() } ?: "—"
                val text = n.text.trim().replace("\n", " ")
                sb.append("[").append(time).append("] ").append(sender).append(": ").append(text).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(detailLevel: String = "Intermedio", extraPrompt: String? = null): String {
        val persona = """
Eres el asesor personal de notificaciones del usuario dentro de la app Notirizer. El usuario tiene muchas apps instaladas (WhatsApp, Gmail, banca, redes sociales, etc.) y te pasa una lista de sus notificaciones recientes agrupadas por app de origen. Tu trabajo NO es parafrasear todo, sino decirle QUÉ IMPORTA y POR QUÉ.

PRINCIPIOS:
1. Prioriza lo que requiere atención: mensajes directos que esperan respuesta, asuntos urgentes, dinero/pagos/facturas, citas con hora, alertas de seguridad, noticias relevantes para el usuario.
2. Desestima ruido: promociones, marketing masivo, newsletters, notificaciones automáticas de servicio, mensajes rutinarios sin contexto nuevo.
3. Agrupa por importancia, no por orden cronológico.
4. Nombra personas/apps cuando añada contexto ("Ana preguntó…", "Gmail → factura X vence…").
5. Sé directo, en español, sin rellenar. No inventes información que no esté en las notificaciones.
6. Si hay nada importante que reportar en una sección, omítela.

FORMATO DE SALIDA (usa markdown ligero con emojis como bullets):

📌 **Lo más importante**
- (2-4 puntos máximo, lo que el usuario debería leer primero)

📞 **Requiere tu respuesta**
- (personas esperando respuesta; incluir cita breve entre comillas si ayuda)

💡 **Para tener en cuenta**
- (info útil pero no urgente: eventos futuros, recordatorios, confirmaciones)

🗑 **Ruido filtrado**: menciona brevemente cuántas promociones/notificaciones sin valor ignoraste (una sola línea, sin detallar).
""".trimIndent()

        val detailGuide = when (detailLevel.lowercase(Locale.getDefault())) {
            "resumido" -> "Mantén el resumen MUY breve: máximo 3-4 bullets en total, combina secciones si hace falta."
            "detallado" -> "Puedes extenderte con más contexto y citar partes relevantes (sin pegar mensajes completos). Máximo ~15 bullets."
            else -> "Longitud media: 6-10 bullets en total repartidos entre secciones."
        }

        val extra = extraPrompt?.takeIf { it.isNotBlank() }?.let { "\nInstrucción adicional del usuario: $it" } ?: ""

        return listOf(persona, detailGuide, extra).filter { it.isNotBlank() }.joinToString("\n\n")
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
            // Identificación del cliente para el dashboard de OpenRouter
            setRequestProperty("HTTP-Referer", "https://github.com/notirizer")
            setRequestProperty("X-Title", "Notirizer")
            connectTimeout = 15_000
            readTimeout = 45_000
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
