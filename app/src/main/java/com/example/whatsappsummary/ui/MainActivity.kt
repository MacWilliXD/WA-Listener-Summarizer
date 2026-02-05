package com.example.whatsappsummary.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.whatsappsummary.data.entity.Chat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.ActivityMainBinding
import com.example.whatsappsummary.ui.adapter.ChatListAdapter
import com.example.whatsappsummary.viewmodel.ChatListViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatListViewModel
    private lateinit var adapter: ChatListAdapter
    private var fullChats: List<Chat> = emptyList()
    private var chatPackageMap: Map<String, String> = emptyMap()
    private var filterStartTs: Long? = null
    private var filterEndTs: Long? = null
    private var filterText: String? = null
    private var filterPackage: String? = null
    private lateinit var filterPrefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Verificar si viene un filtro de aplicación desde el Intent ANTES de cualquier setup
        val filterPackageFromIntent = intent.getStringExtra("FILTER_PACKAGE")
        val filterAppNameFromIntent = intent.getStringExtra("FILTER_APP_NAME")
        
        // Inicializar SharedPreferences para filtros
        filterPrefs = getSharedPreferences("filter_prefs", MODE_PRIVATE)
        loadFiltersFromPrefs()
        
        // Si viene un filtro desde el intent, sobrescribir el de SharedPreferences
        if (filterPackageFromIntent != null) {
            filterPackage = filterPackageFromIntent
            // Mostrar mensaje indicando el filtro aplicado
            android.util.Log.d("FilterDebug", "Aplicando filtro desde dashboard: $filterPackageFromIntent")
            Toast.makeText(this, "Mostrando chats de: ${filterAppNameFromIntent ?: filterPackageFromIntent}", Toast.LENGTH_SHORT).show()
        }

        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupFab()
        setupToolbar()
        setupFilterFab()
        setupSummarizeFab()
        
        checkNotificationPermission()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ChatListViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(
            onChatClick = { chat ->
                // Abrir detalle del chat
                viewModel.resetUnreadCount(chat.chatId)
                val intent = Intent(this, ChatDetailActivity::class.java)
                intent.putExtra("CHAT_ID", chat.chatId)
                intent.putExtra("CHAT_NAME", chat.chatName)
                startActivity(intent)
            },
            onChatLongClick = { chat ->
                // Mostrar diálogo de eliminación
                showDeleteDialog(chat.chatId, chat.chatName)
            }
        )
        
        binding.recyclerViewChats.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupObservers() {
        // Cargar mapa de chat a package
        viewModel.getChatPackageMap { map ->
            chatPackageMap = map
            applyCurrentFilters()
        }

        viewModel.allChats.observe(this) { chats ->
            fullChats = chats
            // Recargar mapa cuando cambien los chats
            viewModel.getChatPackageMap { map ->
                chatPackageMap = map
                applyCurrentFilters()
            }

            // Mostrar mensaje si no hay chats
            if (chats.isEmpty()) {
                binding.textViewEmpty.visibility = android.view.View.VISIBLE
                binding.recyclerViewChats.visibility = android.view.View.GONE
            } else {
                binding.textViewEmpty.visibility = android.view.View.GONE
                binding.recyclerViewChats.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun applyCurrentFilters() {
        android.util.Log.d("FilterDebug", "applyCurrentFilters - filterPackage: $filterPackage, fullChats size: ${fullChats.size}")
        if (fullChats.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        // Si no hay filtros aplicados, mostrar todos
        if (filterStartTs == null && filterEndTs == null && filterText.isNullOrBlank() && filterPackage.isNullOrBlank()) {
            android.util.Log.d("FilterDebug", "No hay filtros aplicados, mostrando todos los chats")
            adapter.submitList(fullChats)
            return
        }

        // Aplicar filtros actuales
        val nq = filterText?.let { normalize(it) }
        val defaultStart = 0L
        val defaultEnd = 4102444800000L
        val s = filterStartTs ?: defaultStart
        val e = filterEndTs ?: defaultEnd

        val filtered = fullChats.filter { chat ->
            // Filtro por nombre de chat
            val matchesName = nq?.let { normalize(chat.chatName ?: "").contains(it) } ?: true
            
            // Filtro por paquete
            val matchesPackage = if (filterPackage.isNullOrBlank()) {
                true
            } else {
                val chatPkg = chatPackageMap[chat.chatId] ?: ""
                chatPkg == filterPackage
            }
            
            // Filtro temporal: verificar si hay notificaciones del chat en el rango
            // (por ahora dejamos true, ya que necesitaríamos consultar la DB)
            val matchesTime = true
            
            matchesName && matchesPackage && matchesTime
        }
        android.util.Log.d("FilterDebug", "Chats filtrados: ${filtered.size} de ${fullChats.size}")
        adapter.submitList(filtered)
    }

    private fun setupFab() {
        binding.fabSettings.setOnClickListener {
            showOptionsDialog()
        }
    }

    private fun setupFilterFab() {
        binding.fabFilter.setOnClickListener {
            showFilterDialogMain()
        }
    }

    private fun setupSummarizeFab() {
        binding.fabSummarizeAll.setOnClickListener {
            generateAggregateSummaryForToday()
        }
    }

    private fun generateAggregateSummaryForToday() {
        // Obtener chats filtrados actualmente mostrados
        val currentFilteredChats = adapter.currentList
        
        if (currentFilteredChats.isEmpty()) {
            Toast.makeText(this, "No hay chats para resumir con los filtros actuales", Toast.LENGTH_SHORT).show()
            return
        }

        // Show options dialog to collect optional length/detail/prompt
        val optsView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_summarize_options, null)
        val editLength = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editSummaryLength)
        val spinner = optsView.findViewById<android.widget.Spinner>(com.example.whatsappsummary.R.id.spinnerDetailLevel)
        val editExtra = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editExtraPrompt)
        val options = listOf("Resumido", "Intermedio", "Detallado")
        val spAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spAdapter

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opciones de resumen")
            .setView(optsView)
            .setPositiveButton("Generar") { _, _ ->
                binding.fabSummarizeAll.isEnabled = false
                
                // Construir mensaje informativo de filtros activos
                val filterParts = mutableListOf<String>()
                if (filterPackage != null) filterParts.add("aplicación")
                if (filterStartTs != null && filterEndTs != null) filterParts.add("rango de fechas")
                if (!filterText.isNullOrBlank()) filterParts.add("búsqueda de texto")
                
                val filterMsg = if (filterParts.isEmpty()) {
                    "Generando resumen general del día..."
                } else {
                    "Generando resumen con filtros: ${filterParts.joinToString(", ")}..."
                }
                
                Toast.makeText(this, filterMsg, Toast.LENGTH_SHORT).show()
                val length = editLength.text?.toString()?.trim()?.toIntOrNull()
                val detail = spinner.selectedItem as? String ?: "Intermedio"
                val extra = editExtra.text?.toString()?.takeIf { it.isNotBlank() }

                lifecycleScope.launch {
                    val db = com.example.whatsappsummary.data.AppDatabase.getDatabase(applicationContext)
                    val repo = com.example.whatsappsummary.repository.NotificationRepository(db.appDao(), db.chatDao(), db.notificationDao(), db.dailySummaryDao())
                    val generator = com.example.whatsappsummary.util.SummaryGenerator(application, repo)

                    try {
                        val chatIds = currentFilteredChats.map { it.chatId }
                        val summary = withContext(Dispatchers.IO) { 
                            generator.generateSummaryForChats(
                                chatIds, 
                                length, 
                                detail, 
                                extra,
                                filterStartTs,
                                filterEndTs,
                                filterText
                            ) 
                        }

                        // Mostrar en diálogo grande el resumen agregado
                        val filterInfo = if (filterPackage != null || filterStartTs != null || filterEndTs != null || !filterText.isNullOrBlank()) {
                            " (${currentFilteredChats.size} chats filtrados)"
                        } else {
                            " (todos los chats)"
                        }
                        val dialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Resumen general$filterInfo")
                            .setMessage(summary)
                            .setPositiveButton("Cerrar", null)
                            .create()
                        dialog.show()
                    } catch (e: Exception) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Error")
                            .setMessage("No se pudo generar el resumen: ${e.message}")
                            .setPositiveButton("Cerrar", null)
                            .show()
                    } finally {
                        binding.fabSummarizeAll.isEnabled = true
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFilterDialogMain() {
        val dialogView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_filters, null)
        val editSearch = dialogView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editSearch)
        val spinnerChatType = dialogView.findViewById<android.widget.Spinner>(com.example.whatsappsummary.R.id.spinnerChatType)
        val textChatTypeLabel = dialogView.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textChatTypeLabel)
        val btnPickStart = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonPickStart)
        val btnPickEnd = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonPickEnd)
        val textStart = dialogView.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textStartDialog)
        val textEnd = dialogView.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textEndDialog)
        val btnApply = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonApplyDialog)
        val btnClear = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonClearDialog)

        // Si el filtro viene desde el dashboard, ocultar el filtro de aplicación emisora
        val filterFromDashboard = intent.getStringExtra("FILTER_PACKAGE") != null
        if (filterFromDashboard) {
            spinnerChatType.visibility = android.view.View.GONE
            textChatTypeLabel.visibility = android.view.View.GONE
        }

        var startTs: Long? = filterStartTs
        var endTs: Long? = filterEndTs

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Filtros")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        fun updateTexts() {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            textStart.text = startTs?.let { df.format(java.util.Date(it)) } ?: "No seleccionado"
            textEnd.text = endTs?.let { df.format(java.util.Date(it)) } ?: "No seleccionado"
        }

        btnPickStart.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val datePicker = android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    val timePicker = android.app.TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                            cal.set(java.util.Calendar.MINUTE, minute)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            startTs = cal.timeInMillis
                            updateTexts()
                        },
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        true
                    )
                    timePicker.show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        btnPickEnd.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val datePicker = android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    val timePicker = android.app.TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                            cal.set(java.util.Calendar.MINUTE, minute)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            endTs = cal.timeInMillis
                            updateTexts()
                        },
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        true
                    )
                    timePicker.show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        // prefill search text and date texts from current filters
        editSearch.setText(filterText ?: "")
        updateTexts()

        // Poblar el spinner con paquetes únicos usando la lista en memoria `fullChats` (no requiere acceso directo a la DB)
        data class AppEntry(val pkg: String, val label: String)
        val pm = packageManager

        val entries = mutableListOf<AppEntry>()
        entries.add(AppEntry("", "Todas"))
        
        // Crear el adapter primero
        val spinnerAdapter = object : android.widget.ArrayAdapter<AppEntry>(this, com.example.whatsappsummary.R.layout.spinner_item_app, entries) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(com.example.whatsappsummary.R.layout.spinner_item_app, parent, false)
                val item = getItem(position)
                val img = view.findViewById<android.widget.ImageView>(com.example.whatsappsummary.R.id.imageAppIcon)
                val txt = view.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textAppLabel)
                if (item != null && item.pkg.isNotBlank()) {
                    try {
                        val ai = pm.getApplicationInfo(item.pkg, 0)
                        img.setImageDrawable(pm.getApplicationIcon(ai))
                    } catch (e: Exception) {
                        img.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
                    }
                    txt.text = item.label
                } else {
                    img.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
                    txt.text = item?.label ?: "Todas"
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                return getView(position, convertView, parent)
            }
        }

        spinnerChatType.adapter = spinnerAdapter
        
        // Cargar lista de apps desde el ViewModel
        viewModel.fetchAllPackages { packages ->
            packages.forEach { pkg ->
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(ai).toString()
                    entries.add(AppEntry(pkg, label))
                } catch (e: Exception) {
                    entries.add(AppEntry(pkg, pkg))
                }
            }
            spinnerAdapter.notifyDataSetChanged()
            
            // Seleccionar el package filtrado actualmente (solo si no viene desde dashboard)
            if (filterPackage != null && !filterFromDashboard) {
                val selectedIndex = entries.indexOfFirst { it.pkg == filterPackage }
                if (selectedIndex >= 0) {
                    spinnerChatType.setSelection(selectedIndex)
                }
            }
        }

        btnApply.setOnClickListener {
            val q = editSearch.text?.toString()?.trim()
            val sel = if (filterFromDashboard) null else (spinnerChatType.selectedItem as? AppEntry)
            val selectedPkg = if (filterFromDashboard) filterPackage else sel?.pkg?.takeIf { it.isNotBlank() }

            // persist applied filters for next dialog open
            filterStartTs = startTs
            filterEndTs = endTs
            filterText = q
            filterPackage = selectedPkg

            saveFiltersToPrefs()
            applyCurrentFilters()
            dialog.dismiss()
        }

        btnClear.setOnClickListener {
            startTs = null
            endTs = null
            filterStartTs = null
            filterEndTs = null
            filterText = null
            editSearch.setText("")
            
            // Si viene desde dashboard, mantener el filtro de aplicación
            if (!filterFromDashboard) {
                filterPackage = null
                spinnerChatType.setSelection(0)
            }
            
            saveFiltersToPrefs()
            applyCurrentFilters()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun normalize(input: String): String {
        val n = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return n.replace("""\p{M}+""".toRegex(), "").lowercase(java.util.Locale.getDefault())
    }

    private fun showOptionsDialog() {
        val prefs = getSharedPreferences("wa_listener_prefs", MODE_PRIVATE)
        val dialogView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_settings, null)
        val btnGrant = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonGrantPermission)
        val switchWhatsApp = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.whatsappsummary.R.id.switchWhatsApp)
        val switchOthers = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.whatsappsummary.R.id.switchOthers)
        val btnViewDashboard = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonViewDashboard)

        // Estado inicial
        switchWhatsApp.isChecked = prefs.getBoolean("collect_whatsapp", true)
        switchOthers.isChecked = prefs.getBoolean("collect_others", false)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        btnGrant.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        switchWhatsApp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("collect_whatsapp", isChecked).apply()
            android.widget.Toast.makeText(this, if (isChecked) "Recopilación WhatsApp habilitada" else "Recopilación WhatsApp deshabilitada", android.widget.Toast.LENGTH_SHORT).show()
        }

        switchOthers.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("collect_others", isChecked).apply()
            android.widget.Toast.makeText(this, if (isChecked) "Recopilación otras notificaciones habilitada" else "Recopilación otras notificaciones deshabilitada", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnViewDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteDialog(chatId: String, chatName: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar chat")
            .setMessage("¿Deseas eliminar el chat con $chatName? Se eliminarán todos los mensajes y resúmenes asociados.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteChat(chatId)
                Toast.makeText(this, "Chat eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkNotificationPermission() {
        val notificationListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        
        val isEnabled = notificationListeners?.contains(packageName) == true
        
        if (!isEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Permiso requerido")
                .setMessage("Esta aplicación necesita acceso a las notificaciones para funcionar. Por favor, habilita el acceso en la configuración.")
                .setPositiveButton("Ir a configuración") { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Más tarde", null)
                .show()
        }
    }

    private fun saveFiltersToPrefs() {
        filterPrefs.edit()
            .putLong("filter_start_ts", filterStartTs ?: -1L)
            .putLong("filter_end_ts", filterEndTs ?: -1L)
            .putString("filter_text", filterText)
            .putString("filter_package", filterPackage)
            .apply()
    }

    private fun loadFiltersFromPrefs() {
        filterStartTs = filterPrefs.getLong("filter_start_ts", -1L).takeIf { it != -1L }
        filterEndTs = filterPrefs.getLong("filter_end_ts", -1L).takeIf { it != -1L }
        filterText = filterPrefs.getString("filter_text", null)
        filterPackage = filterPrefs.getString("filter_package", null)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        
        // Cambiar el título si hay filtro de aplicación aplicado
        val filterAppNameFromIntent = intent.getStringExtra("FILTER_APP_NAME")
        if (filterAppNameFromIntent != null) {
            supportActionBar?.title = filterAppNameFromIntent
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        } else {
            supportActionBar?.title = "Notificator Summary"
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(com.example.whatsappsummary.R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // Volver al dashboard
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
                return true
            }
            com.example.whatsappsummary.R.id.action_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}
