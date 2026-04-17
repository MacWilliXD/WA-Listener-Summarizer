package com.example.whatsappsummary.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Formateador ligero de Markdown → CharSequence para TextView.
 *
 * Soporta:
 *   **negrita**      → bold
 *   *cursiva*        → italic
 *   # / ## / ###     → títulos de línea (tamaños relativos)
 *   - / * / • item   → bullet normalizado "•  "
 *
 * No depende de librerías externas. Pensado para respuestas cortas del LLM
 * (resúmenes), no como parser completo de Markdown.
 */
object MarkdownFormatter {

    fun format(raw: String): CharSequence {
        if (raw.isBlank()) return raw
        val sb = SpannableStringBuilder()
        val lines = raw.trim().split("\n")
        for ((i, originalLine) in lines.withIndex()) {
            val lineStart = sb.length
            var content = originalLine
            var headerScale: Float? = null
            var isBullet = false

            val trimmed = content.trimStart()
            when {
                trimmed.startsWith("### ") -> { content = trimmed.removePrefix("### "); headerScale = 1.08f }
                trimmed.startsWith("## ") -> { content = trimmed.removePrefix("## "); headerScale = 1.15f }
                trimmed.startsWith("# ") -> { content = trimmed.removePrefix("# "); headerScale = 1.25f }
                trimmed.startsWith("- ") -> { content = trimmed.removePrefix("- "); isBullet = true }
                trimmed.startsWith("* ") -> { content = trimmed.removePrefix("* "); isBullet = true }
                trimmed.startsWith("• ") -> { content = trimmed.removePrefix("• "); isBullet = true }
            }

            if (isBullet) sb.append("•  ")
            appendInline(sb, content)

            if (headerScale != null) {
                sb.setSpan(StyleSpan(Typeface.BOLD), lineStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(headerScale), lineStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (i < lines.lastIndex) sb.append("\n")
        }
        return sb
    }

    /**
     * Escribe `text` en `sb` aplicando spans para **bold** y *italic* inline.
     * Anidamiento soportado (p.ej. **negrita con _cursiva_**).
     */
    private fun appendInline(sb: SpannableStringBuilder, text: String) {
        var i = 0
        while (i < text.length) {
            // **bold**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val close = text.indexOf("**", i + 2)
                if (close > i + 2) {
                    val inner = text.substring(i + 2, close)
                    val start = sb.length
                    appendInline(sb, inner)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = close + 2
                    continue
                }
            }
            // *italic* o _italic_ (no tocar asteriscos aislados sin cierre)
            if ((text[i] == '*' || text[i] == '_') && i + 1 < text.length && text[i + 1] != ' ') {
                val marker = text[i]
                // No confundir con ** (ya lo habríamos manejado arriba)
                if (marker == '*' && i + 1 < text.length && text[i + 1] == '*') {
                    sb.append(text[i]); i++; continue
                }
                val close = text.indexOf(marker, i + 1)
                if (close > i + 1 && text.getOrNull(close - 1) != ' ' &&
                    (marker != '*' || text.getOrNull(close + 1) != '*')
                ) {
                    val inner = text.substring(i + 1, close)
                    if (inner.isNotBlank()) {
                        val start = sb.length
                        appendInline(sb, inner)
                        sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = close + 1
                        continue
                    }
                }
            }
            // `code` inline → negrita ligera (sin tipografía monoespacio para mantener coherencia)
            if (text[i] == '`') {
                val close = text.indexOf('`', i + 1)
                if (close > i + 1) {
                    val inner = text.substring(i + 1, close)
                    val start = sb.length
                    sb.append(inner)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = close + 1
                    continue
                }
            }
            sb.append(text[i])
            i++
        }
    }
}
