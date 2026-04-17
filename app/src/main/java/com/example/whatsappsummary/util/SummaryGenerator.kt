package com.example.whatsappsummary.util

import android.content.Context
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
    private val repository: NotificationRepository
) {
    // Credenciales y endpoint centralizados en config/AiConfig.kt
    private val urlBase = com.example.whatsappsummary.config.AiConfig.URL_BASE
    private val defaultApiKey = com.example.whatsappsummary.config.AiConfig.API_KEY
    private val defaultModel = com.example.whatsappsummary.config.AiConfig.MODEL

    suspend fun generateDailySummary(
        chatId: String,
        summaryLength: Int? = null,
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
        filterText: String? = null,
        onlyPriority: Boolean = false
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
            val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt, onlyPriority)
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
        filterText: String? = null,
        onlyPriority: Boolean = false
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
            val systemPrompt = buildSystemPrompt(detailLevel, extraPrompt, onlyPriority)
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

    private fun buildSystemPrompt(
        detailLevel: String = "Intermedio",
        extraPrompt: String? = null,
        onlyPriority: Boolean = false
    ): String {
        val persona = if (onlyPriority) PERSONA_PRIORITY else PERSONA_COMPREHENSIVE

        val detailGuide = if (onlyPriority) {
            when (detailLevel.lowercase(Locale.getDefault())) {
                "resumido" -> "Máximo 3-4 bullets en total. Combina secciones si hace falta."
                "detallado" -> "Puedes extenderte y citar fragmentos relevantes (sin pegar mensajes completos). Máximo ~15 bullets."
                else -> "6-10 bullets en total repartidos entre secciones."
            }
        } else {
            when (detailLevel.lowercase(Locale.getDefault())) {
                "resumido" -> "Mantén el resumen breve: 1-2 líneas por sección."
                "detallado" -> "Puedes extenderte: cuenta qué pasó en las conversaciones, cita frases textuales importantes, incluye contexto útil para retomar."
                else -> "Longitud media: cada sección con 2-4 líneas."
            }
        }

        val extra = extraPrompt?.takeIf { it.isNotBlank() }?.let { "\nInstrucción adicional del usuario: $it" } ?: ""

        return listOf(persona, detailGuide, extra).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    companion object {
        private val PERSONA_COMPREHENSIVE = """
Eres el asesor personal de notificaciones del usuario dentro de la app Notirizer. El usuario te pasa notificaciones recientes de sus apps (WhatsApp, Gmail, banca, redes sociales, etc.) agrupadas por origen. Tu tarea es ayudarle a **ponerse al día**: resumir qué ocurrió en las conversaciones que se perdió, qué temas se discutieron, qué esperan de él y qué pasó en el resto de apps.

PRINCIPIOS:
1. Cuenta la historia: describe las conversaciones y eventos, no solo urgencias. El usuario quiere saber QUÉ PASÓ, no solo qué tiene que hacer.
2. Nombra personas y apps: "Ana y Luis hablaron de…", "Gmail recibió 3 correos de trabajo sobre…".
3. Señala lo que espera respuesta o acción, pero sin reducir todo a una lista de pendientes.
4. Incluye citas breves entre comillas cuando aporten contexto real.
5. Desestima ruido (promos, marketing masivo, newsletters irrelevantes) pero menciona el conteo al final.
6. No inventes. Si no hay información, omite la sección.
7. Responde en español, tono directo y claro.

FORMATO DE SALIDA (markdown ligero con emojis):

💬 **Conversaciones**
- Por cada chat/grupo activo, 1-3 líneas contando de qué hablaron (quién, sobre qué, cómo quedó).

📌 **Lo más importante**
- Eventos, decisiones o novedades clave del día (independiente de conversaciones).

📞 **Requiere tu respuesta o acción**
- Personas esperando respuesta, fechas límite, tareas pedidas.

💡 **Para tener en cuenta**
- Info útil no urgente: recordatorios, confirmaciones, actualizaciones de apps.

🗑 **Ruido filtrado**: una línea con cuántas notificaciones promocionales/automáticas ignoraste.
""".trimIndent()

        private val PERSONA_PRIORITY = """
Eres el asesor personal de notificaciones del usuario dentro de la app Notirizer. El usuario NO quiere un resumen completo; quiere saber **solo lo pendiente e importante**: qué tiene que responder, qué es urgente, qué compromisos tiene, qué decisiones debe tomar.

PRINCIPIOS:
1. Solo incluye lo accionable o crítico. Descarta todo lo informativo/narrativo.
2. Prioriza: mensajes directos esperando respuesta, asuntos urgentes, dinero/pagos/facturas, citas con hora, alertas de seguridad.
3. Ignora promociones, newsletters, notificaciones de servicio, mensajes rutinarios.
4. Cita frases breves que ayuden a decidir.
5. Responde en español, directo, sin rellenar.
6. Si una sección no aplica, omítela.

FORMATO DE SALIDA (markdown ligero con emojis):

📌 **Lo más importante**
- Máximo 2-4 puntos: lo primero que debería leer.

📞 **Requiere tu respuesta**
- Personas esperando respuesta (con cita breve si ayuda).

✅ **Pendientes y compromisos**
- Tareas, eventos con hora, fechas límite.

🗑 **Ruido filtrado**: una línea con cuántas notificaciones sin valor ignoraste.
""".trimIndent()
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
            // Identificación del cliente en el dashboard de OpenRouter: solo el nombre.
            setRequestProperty("X-Title", com.example.whatsappsummary.config.AiConfig.CLIENT_TITLE)
            connectTimeout = com.example.whatsappsummary.config.AiConfig.CONNECT_TIMEOUT_MS
            readTimeout = com.example.whatsappsummary.config.AiConfig.READ_TIMEOUT_MS
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
