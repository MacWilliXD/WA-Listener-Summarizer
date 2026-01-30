package com.example.whatsappsummary.util

object ChatUtils {
    fun normalizeChatTitle(raw: String): String {
        var s = raw.trim()
        // Eliminar sufijos comunes que indican mensajes no leídos como "(7 mensajes)", "(7)", "(99+)", "(7 unread)"
        s = s.replace(Regex("\\s*\\(\\s*\\d+\\+?\\s*(mensajes|unread|unread_messages|new)?\\s*\\)\\s*$", RegexOption.IGNORE_CASE), "")
        // También eliminar patrones como " - 7" al final si aparecen
        s = s.replace(Regex("\\s*-\\s*\\d+\\+?\\s*$"), "")
        return s.trim()
    }
}
