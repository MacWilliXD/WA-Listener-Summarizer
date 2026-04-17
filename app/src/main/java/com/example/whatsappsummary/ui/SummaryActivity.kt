package com.example.whatsappsummary.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.databinding.ActivitySummaryBinding
import com.example.whatsappsummary.repository.NotificationRepository
import com.example.whatsappsummary.util.SummaryGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vista dedicada al resumen generado por IA.
 * Muestra 3 estados: cargando, resultado, error.
 *
 * Se invoca con `newIntent(...)`. Llama a `SummaryGenerator.generateSummaryForChats`
 * con los parámetros recibidos y publica el resultado en pantalla.
 */
class SummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySummaryBinding
    private var lastSummary: String = ""

    private val chatIds by lazy { intent.getStringArrayExtra(EXTRA_CHAT_IDS)?.toList().orEmpty() }
    private val summaryLength by lazy { intent.getIntExtra(EXTRA_LENGTH, -1).takeIf { it > 0 } }
    private val detailLevel by lazy { intent.getStringExtra(EXTRA_DETAIL) ?: "Intermedio" }
    private val extraPrompt by lazy { intent.getStringExtra(EXTRA_EXTRA_PROMPT) }
    private val filterStartTs by lazy { intent.getLongExtra(EXTRA_START_TS, -1L).takeIf { it > 0 } }
    private val filterEndTs by lazy { intent.getLongExtra(EXTRA_END_TS, -1L).takeIf { it > 0 } }
    private val filterText by lazy { intent.getStringExtra(EXTRA_FILTER_TEXT) }
    private val titleText by lazy { intent.getStringExtra(EXTRA_TITLE) ?: "Resumen" }
    private val subtitleText by lazy { intent.getStringExtra(EXTRA_SUBTITLE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = titleText
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.textHeroTitle.text = titleText
        if (subtitleText.isNotBlank()) {
            binding.textHeroSubtitle.text = subtitleText
            binding.textHeroSubtitle.visibility = View.VISIBLE
        } else {
            binding.textHeroSubtitle.visibility = View.GONE
        }

        binding.buttonRetry.setOnClickListener { startGeneration() }
        binding.buttonCopy.setOnClickListener { copyToClipboard() }
        binding.buttonShare.setOnClickListener { shareSummary() }

        if (chatIds.isEmpty()) {
            showError("No hay chats para resumir.")
        } else {
            startGeneration()
        }
    }

    private fun startGeneration() {
        showLoading()
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = NotificationRepository(
                db.appDao(), db.chatDao(), db.notificationDao(), db.dailySummaryDao()
            )
            val generator = SummaryGenerator(application, repo)

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    generator.generateSummaryForChats(
                        chatIds,
                        summaryLength,
                        detailLevel,
                        extraPrompt,
                        filterStartTs,
                        filterEndTs,
                        filterText
                    )
                }
            }
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message ?: "Error desconocido")
                return@launch
            }
            val text = result.getOrNull().orEmpty()
            if (text.isBlank() || text.startsWith("ERROR", ignoreCase = true)) {
                showError(text.ifBlank { "Respuesta vacía del modelo." })
            } else {
                showResult(text)
            }
        }
    }

    private fun showLoading() {
        binding.stateLoading.visibility = View.VISIBLE
        binding.stateResult.visibility = View.GONE
        binding.stateError.visibility = View.GONE
        binding.actionBar.visibility = View.GONE
    }

    private fun showResult(text: String) {
        lastSummary = text
        binding.stateLoading.visibility = View.GONE
        binding.stateResult.visibility = View.VISIBLE
        binding.stateError.visibility = View.GONE
        binding.actionBar.visibility = View.VISIBLE
        binding.textSummary.text = com.example.whatsappsummary.util.MarkdownFormatter.format(text)
    }

    private fun showError(message: String) {
        binding.stateLoading.visibility = View.GONE
        binding.stateResult.visibility = View.GONE
        binding.stateError.visibility = View.VISIBLE
        binding.actionBar.visibility = View.GONE
        binding.textError.text = message
    }

    private fun copyToClipboard() {
        if (lastSummary.isBlank()) return
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Resumen", lastSummary))
        Toast.makeText(this, "Resumen copiado", Toast.LENGTH_SHORT).show()
    }

    private fun shareSummary() {
        if (lastSummary.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, titleText)
            putExtra(Intent.EXTRA_TEXT, lastSummary)
        }
        startActivity(Intent.createChooser(send, "Compartir resumen"))
    }

    companion object {
        private const val EXTRA_CHAT_IDS = "extra_chat_ids"
        private const val EXTRA_LENGTH = "extra_length"
        private const val EXTRA_DETAIL = "extra_detail"
        private const val EXTRA_EXTRA_PROMPT = "extra_extra_prompt"
        private const val EXTRA_START_TS = "extra_start_ts"
        private const val EXTRA_END_TS = "extra_end_ts"
        private const val EXTRA_FILTER_TEXT = "extra_filter_text"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"

        fun newIntent(
            context: Context,
            chatIds: List<String>,
            title: String,
            subtitle: String? = null,
            length: Int? = null,
            detail: String = "Intermedio",
            extraPrompt: String? = null,
            startTs: Long? = null,
            endTs: Long? = null,
            filterText: String? = null
        ): Intent = Intent(context, SummaryActivity::class.java).apply {
            putExtra(EXTRA_CHAT_IDS, chatIds.toTypedArray())
            putExtra(EXTRA_TITLE, title)
            if (subtitle != null) putExtra(EXTRA_SUBTITLE, subtitle)
            if (length != null) putExtra(EXTRA_LENGTH, length)
            putExtra(EXTRA_DETAIL, detail)
            if (extraPrompt != null) putExtra(EXTRA_EXTRA_PROMPT, extraPrompt)
            if (startTs != null) putExtra(EXTRA_START_TS, startTs)
            if (endTs != null) putExtra(EXTRA_END_TS, endTs)
            if (filterText != null) putExtra(EXTRA_FILTER_TEXT, filterText)
        }
    }
}
