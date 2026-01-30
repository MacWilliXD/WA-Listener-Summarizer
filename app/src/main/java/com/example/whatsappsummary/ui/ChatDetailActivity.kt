package com.example.whatsappsummary.ui

import android.os.Bundle
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.whatsappsummary.R
import com.example.whatsappsummary.databinding.ActivityChatDetailBinding
import com.example.whatsappsummary.ui.fragment.MessagesFragment
import com.example.whatsappsummary.ui.fragment.SummariesFragment
import com.example.whatsappsummary.viewmodel.ChatDetailViewModel
import com.google.android.material.tabs.TabLayout

class ChatDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatDetailBinding
    private lateinit var viewModel: ChatDetailViewModel
    private var chatId: String? = null
    private var chatName: String? = null
    private var filterStartTs: Long? = null
    private var filterEndTs: Long? = null
    private var filterText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Obtener datos del intent
        chatId = intent.getStringExtra("CHAT_ID")
        chatName = intent.getStringExtra("CHAT_NAME")
        
        if (chatId == null) {
            finish()
            return
        }
        
        try {
            setupToolbar()
            setupViewModel()
            setupTabs()
            // FAB to open filter dialog
            binding.fabFilter.setOnClickListener { showFilterDialog() }
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error inicializando vista de detalle", e)
            finish()
            return
        }
        
        // Cargar fragmento inicial
        if (savedInstanceState == null) {
            try {
                showFragment(MessagesFragment.newInstance(chatId!!))
            } catch (e: Exception) {
                android.util.Log.e("ChatDetailActivity", "Error al cargar fragmento inicial", e)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = chatName ?: "Chat"
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ChatDetailViewModel::class.java]
        try {
            chatId?.let { viewModel.loadChatData(it) }
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error cargando datos del chat", e)
        }
    }
    
    private fun showFilterDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_filters, null)
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()

            var startTs: Long? = filterStartTs
            var endTs: Long? = filterEndTs

            val textStart = dialogView.findViewById<android.widget.TextView>(R.id.textStartDialog)
            val textEnd = dialogView.findViewById<android.widget.TextView>(R.id.textEndDialog)
            val btnPickStart = dialogView.findViewById<android.widget.Button>(R.id.buttonPickStart)
            val btnPickEnd = dialogView.findViewById<android.widget.Button>(R.id.buttonPickEnd)
            val btnApply = dialogView.findViewById<android.widget.Button>(R.id.buttonApplyDialog)
            val btnClear = dialogView.findViewById<android.widget.Button>(R.id.buttonClearDialog)
            val editSearch = dialogView.findViewById<android.widget.EditText>(R.id.editSearch)

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

            fun updateTexts() {
                textStart.text = startTs?.let { dateFormat.format(java.util.Date(it)) } ?: "No seleccionado"
                textEnd.text = endTs?.let { dateFormat.format(java.util.Date(it)) } ?: "No seleccionado"
            }

            // prefill search text and date texts from current filters
            editSearch.setText(filterText ?: "")
            updateTexts()

            // hide chat-type selector in the chat-detail view (only visible in main activity)
            try {
                val spinnerChatType = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerChatType)
                val textChatTypeLabel = dialogView.findViewById<android.widget.TextView>(R.id.textChatTypeLabel)
                spinnerChatType?.visibility = android.view.View.GONE
                textChatTypeLabel?.visibility = android.view.View.GONE
            } catch (e: Exception) {
                // no-op: if IDs don't exist for some reason, ignore and continue
            }

            btnPickStart.setOnClickListener {
                val cal = Calendar.getInstance()
                val datePicker = android.app.DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        cal.set(year, month, dayOfMonth)
                        val timePicker = android.app.TimePickerDialog(
                            this,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                startTs = cal.timeInMillis
                                updateTexts()
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        )
                        timePicker.show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                datePicker.show()
            }

            btnPickEnd.setOnClickListener {
                val cal = Calendar.getInstance()
                val datePicker = android.app.DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        cal.set(year, month, dayOfMonth)
                        val timePicker = android.app.TimePickerDialog(
                            this,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                endTs = cal.timeInMillis
                                updateTexts()
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        )
                        timePicker.show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                datePicker.show()
            }

            btnApply.setOnClickListener {
                // If a side is not selected, apply a very low / very high default so the range covers all
                val defaultStart = 0L // epoch start (covers all older messages)
                val defaultEnd = 4102444800000L // 2100-01-01 in ms (far future)

                val startToApply = startTs ?: defaultStart
                val endToApply = endTs ?: defaultEnd

                filterStartTs = startToApply
                filterEndTs = endToApply
                val q = editSearch.text?.toString()?.trim()
                filterText = q
                viewModel.setTextFilter(q)
                viewModel.setFilterByTimestamps(startToApply, endToApply)
                dialog.dismiss()
            }

            btnClear.setOnClickListener {
                // Apply defaults so the range covers all messages (behave as if filters are empty)
                val defaultStart = 0L
                val defaultEnd = 4102444800000L

                startTs = null
                endTs = null
                filterStartTs = null
                filterEndTs = null
                filterText = null
                // clear text filter as well
                editSearch.setText("")
                viewModel.setTextFilter(null)
                updateTexts()
                viewModel.setFilterByTimestamps(defaultStart, defaultEnd)
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error mostrando diálogo de filtros", e)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Mensajes"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Resúmenes"))
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showFragment(MessagesFragment.newInstance(chatId!!))
                    1 -> showFragment(SummariesFragment.newInstance(chatId!!))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error mostrando fragmento", e)
        }
    }
}
