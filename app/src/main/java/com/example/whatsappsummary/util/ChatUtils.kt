package com.example.whatsappsummary.util

import java.text.Normalizer
import java.util.Locale

object ChatUtils {

    /**
     * Elimina sufijos de "mensajes no leídos" del título de una notificación.
     * Ejemplos: "Juan (3)", "Grupo (99+)", "Chat - 5", "Juan (7 mensajes)", "Chat | 3 mensajes nuevos"
     */
    fun normalizeChatTitle(raw: String): String {
        var s = raw.trim()
        // Contadores entre paréntesis en cualquier posición: (3), (3 mensajes), (99+), (unread), etc.
        s = s.replace(
            Regex(
                "\\s*\\(\\s*\\d+\\+?\\s*(mensajes?\\s*nuevos?|mensajes?|msgs?|unread(?:_messages)?|new(?:\\s+messages?)?)?\\s*\\)",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        // Sufijos tipo " - 7" o " · 7" al final
        s = s.replace(Regex("\\s*[-·—]\\s*\\d+\\+?\\s*$"), "")
        // Sufijos tipo " | 3 mensajes nuevos" en cualquier posición
        s = s.replace(
            Regex(
                "\\s*\\|\\s*\\d+\\s*(mensajes?\\s*nuevos?|new\\s+messages?|mensajes?|msgs?|unread)",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        // Limpieza de puntuación residual si quedó colgando: "Nombre : …"  →  "Nombre: …"
        s = s.replace(Regex("\\s+:"), ":")
        // Colapsar espacios duplicados
        s = s.replace(Regex("\\s{2,}"), " ")
        return s.trim()
    }

    /**
     * Forma canónica para comparación: sin acentos, minúsculas, solo letras y
     * dígitos Unicode. Preserva árabe, chino, hebreo, etc. así funciona la
     * detección de chats duplicados en cualquier idioma.
     */
    fun canonicalize(raw: String): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{M}"), "")          // quita marcas diacríticas
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{Nd}]"), "") // conserva LETRAS y DÍGITOS de cualquier script
    }

    /**
     * Similitud robusta basada en trigramas (índice de Jaccard).
     * Mucho más fiable que comparación posición-a-posición para nombres con
     * variaciones ("Juan Perez" vs "Juan P.", "Mamá ❤" vs "Mama").
     * Devuelve 0.0 a 1.0.
     */
    fun trigramSimilarity(a: String, b: String): Double {
        val tga = trigrams(canonicalize(a))
        val tgb = trigrams(canonicalize(b))
        if (tga.isEmpty() || tgb.isEmpty()) return 0.0
        val intersection = tga.intersect(tgb).size
        val union = tga.union(tgb).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    private fun trigrams(s: String): Set<String> {
        if (s.length < 3) return if (s.isEmpty()) emptySet() else setOf(s)
        val padded = "  $s  "
        val out = HashSet<String>(padded.length)
        for (i in 0..padded.length - 3) {
            out.add(padded.substring(i, i + 3))
        }
        return out
    }

    /**
     * Detecta si el contenido de una notificación es un "resumen" agregado
     * (placeholder que WhatsApp y otras apps emiten: "3 mensajes nuevos",
     * "5 mensajes de 2 chats", "Checking for new messages", etc.).
     * Se evalúa título + texto para capturar ambos casos.
     */
    fun isPlaceholderNotification(title: String, text: String): Boolean {
        val full = "${title.orEmpty()} ${text.orEmpty()}".lowercase(Locale.getDefault()).trim()
        if (full.isBlank()) return true

        val patterns = listOf(
            // Español
            Regex("\\b\\d+\\s+mensajes?\\s+(nuevos?|pendientes?|sin\\s+leer)\\b"),
            Regex("\\b\\d+\\s+mensajes?\\s+de\\s+\\d+\\s+chats?\\b"),
            Regex("\\b\\d+\\s+msg\\s+de\\s+\\d+\\s+chats?\\b"),
            Regex("\\bcomprobando\\s+si\\s+hay\\s+mensajes?\\s+nuevos?\\b"),
            Regex("\\brevisando\\s+mensajes?\\b"),
            Regex("\\bbuscando\\s+mensajes?\\s+nuevos?\\b"),
            Regex("\\brecibiendo\\s+mensajes?\\b"),
            Regex("\\bnuevas?\\s+notificaciones?\\b"),
            Regex("\\bnotificaci[oó]n\\s+silenciada\\b"),
            // Inglés
            Regex("\\b\\d+\\s+new\\s+messages?\\b"),
            Regex("\\b\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?\\b"),
            Regex("\\b\\d+\\s+msgs?\\s+from\\s+\\d+\\s+chats?\\b"),
            Regex("\\bchecking\\s+for\\s+new\\s+messages?\\b"),
            Regex("\\bnew\\s+notifications?\\b"),
            Regex("\\bincoming\\s+messages?\\b"),
            Regex("\\b\\d+\\s+unread\\b"),
            // Notificaciones de servicio / sistema WA
            Regex("\\bllamada\\s+perdida\\b.*\\bwhatsapp\\b"),
            Regex("\\brespaldo\\b.*\\b(activo|completo|terminado)\\b")
        )
        return patterns.any { it.containsMatchIn(full) }
    }
}
