package com.example.whatsappsummary.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.data.entity.ChatWithLastMessage
import com.example.whatsappsummary.databinding.FragmentChatsListBinding
import com.example.whatsappsummary.ui.adapter.ChatListAdapter
import com.example.whatsappsummary.viewmodel.ChatListViewModel
import com.example.whatsappsummary.viewmodel.NavSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsListFragment : Fragment() {

    private var _binding: FragmentChatsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatListViewModel by viewModels()
    private val navViewModel: NavSharedViewModel by activityViewModels()
    private lateinit var adapter: ChatListAdapter
    private var fullChats: List<ChatWithLastMessage> = emptyList()
    private var chatPackageMap: Map<String, String> = emptyMap()
    private var filterStartTs: Long? = null
    private var filterEndTs: Long? = null
    private var filterText: String? = null
    private var filterPackage: String? = null
    private lateinit var filterPrefs: android.content.SharedPreferences

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 9301
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        filterPrefs = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
        loadFiltersFromPrefs()

        setupRecyclerView()
        setupObservers()
        setupFab()
        setupFilterFab()
        setupSummarizeFab()
        setupHeader()
        observeNavRequests()

        checkNotificationPermission()
        autoCleanupOnceIfNeeded()
        requestPostNotificationsIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Escucha peticiones del Dashboard para aplicar filtro por app o limpiarlos.
     */
    private fun observeNavRequests() {
        navViewModel.chatFilterRequest.observe(viewLifecycleOwner) { req ->
            req ?: return@observe
            if (req.clearFilters) {
                filterPackage = null
                filterStartTs = null
                filterEndTs = null
                filterText = null
                saveFiltersToPrefs()
                binding.textHeroSubtitle.text = "Tus conversaciones, resumidas"
                applyCurrentFilters()
            } else if (req.packageName != null) {
                filterPackage = req.packageName
                saveFiltersToPrefs()
                binding.textHeroSubtitle.text = "Filtrado por: ${req.appName ?: req.packageName}"
                Toast.makeText(
                    requireContext(),
                    "Mostrando chats de: ${req.appName ?: req.packageName}",
                    Toast.LENGTH_SHORT
                ).show()
                applyCurrentFilters()
            }
            navViewModel.consumeChatFilter()
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        try {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIFICATIONS
            )
        } catch (_: Exception) { /* ignorar */ }
    }

    private fun autoCleanupOnceIfNeeded() {
        val prefs = requireContext().getSharedPreferences("wa_listener_prefs", Context.MODE_PRIVATE)
        val lastCleanup = prefs.getLong("last_cleanup_ts", 0L)
        val now = System.currentTimeMillis()
        if (now - lastCleanup < 24 * 60 * 60 * 1000L) return

        viewLifecycleOwner.lifecycleScope.launch {
            val db = com.example.whatsappsummary.data.AppDatabase.getDatabase(requireContext().applicationContext)
            val repo = com.example.whatsappsummary.repository.NotificationRepository(
                db.appDao(), db.chatDao(), db.notificationDao(), db.dailySummaryDao()
            )
            withContext(Dispatchers.IO) { runCatching { repo.cleanupGarbage() } }
            prefs.edit().putLong("last_cleanup_ts", now).apply()
        }
    }

    private fun setupHeader() {
        // Subtítulo por defecto; si hay filtro por app se actualiza en observeNavRequests
        if (filterPackage != null) {
            binding.textHeroSubtitle.text = "Filtrado por app"
        }
    }

    private fun updateHeaderStats(chats: List<ChatWithLastMessage>) {
        if (_binding == null) return
        binding.textHeroChatCount.text = chats.size.toString()
        val unread = chats.sumOf { it.unreadCount }
        binding.textHeroUnread.text = unread.toString()
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayCount = chats.count { it.lastMessageTime >= now }
        binding.textHeroToday.text = todayCount.toString()

        val activeFilters = mutableListOf<String>()
        if (filterPackage != null) activeFilters.add("app")
        if (filterStartTs != null || filterEndTs != null) activeFilters.add("fecha")
        if (!filterText.isNullOrBlank()) activeFilters.add("texto")
        if (activeFilters.isEmpty()) {
            binding.textFilterStatus.visibility = View.GONE
        } else {
            binding.textFilterStatus.visibility = View.VISIBLE
            binding.textFilterStatus.text = "Filtros: ${activeFilters.joinToString(", ")}"
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(
            onChatClick = { chat ->
                viewModel.resetUnreadCount(chat.chatId)
                val intent = Intent(requireContext(), ChatDetailActivity::class.java)
                intent.putExtra("CHAT_ID", chat.chatId)
                intent.putExtra("CHAT_NAME", chat.chatName)
                startActivity(intent)
            },
            onChatLongClick = { chat -> showDeleteDialog(chat.chatId, chat.chatName) }
        )

        binding.recyclerViewChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatsListFragment.adapter
        }
    }

    private fun setupObservers() {
        viewModel.getChatPackageMap { map ->
            chatPackageMap = map
            applyCurrentFilters()
        }

        viewModel.allChatsWithMessage.observe(viewLifecycleOwner) { chatsWithMessage ->
            fullChats = chatsWithMessage
            viewModel.getChatPackageMap { map ->
                chatPackageMap = map
                applyCurrentFilters()
            }

            updateHeaderStats(chatsWithMessage)

            if (chatsWithMessage.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerViewChats.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerViewChats.visibility = View.VISIBLE
            }
        }
    }

    private fun applyCurrentFilters() {
        if (fullChats.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        if (filterStartTs == null && filterEndTs == null && filterText.isNullOrBlank() && filterPackage.isNullOrBlank()) {
            adapter.submitList(fullChats)
            updateHeaderStats(fullChats)
            return
        }

        val nq = filterText?.let { normalize(it) }
        val filtered = fullChats.filter { chatItem ->
            val matchesName = nq?.let { normalize(chatItem.chatName).contains(it) } ?: true
            val matchesPackage = if (filterPackage.isNullOrBlank()) true
            else (chatPackageMap[chatItem.chatId] ?: "") == filterPackage
            val matchesTime = if (filterStartTs != null || filterEndTs != null) {
                val lastMsgTime = chatItem.lastMessageTime
                val isAfterStart = filterStartTs?.let { lastMsgTime >= it } ?: true
                val isBeforeEnd = filterEndTs?.let { lastMsgTime <= it } ?: true
                isAfterStart && isBeforeEnd
            } else true
            matchesName && matchesPackage && matchesTime
        }
        adapter.submitList(filtered)
        updateHeaderStats(filtered)
    }

    private fun setupFab() {
        binding.fabSettings.setOnClickListener { showOptionsDialog() }
    }

    private fun setupFilterFab() {
        binding.fabFilter.setOnClickListener { showFilterDialogMain() }
    }

    private fun setupSummarizeFab() {
        binding.fabSummarizeAll.setOnClickListener { generateAggregateSummaryForToday() }
    }

    private fun generateAggregateSummaryForToday() {
        val currentFilteredChats = adapter.currentList
        if (currentFilteredChats.isEmpty()) {
            Toast.makeText(requireContext(), "No hay chats para resumir con los filtros actuales", Toast.LENGTH_SHORT).show()
            return
        }

        val optsView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_summarize_options, null)
        val editLength = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editSummaryLength)
        val spinner = optsView.findViewById<android.widget.Spinner>(com.example.whatsappsummary.R.id.spinnerDetailLevel)
        val editExtra = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editExtraPrompt)
        val checkOnlyPriority = optsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(com.example.whatsappsummary.R.id.checkOnlyPriority)
        val options = listOf("Resumido", "Intermedio", "Detallado")
        val spAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spAdapter

        AlertDialog.Builder(requireContext())
            .setTitle("Opciones de resumen")
            .setView(optsView)
            .setPositiveButton("Generar") { _, _ ->
                val length = editLength.text?.toString()?.trim()?.toIntOrNull()
                val detail = spinner.selectedItem as? String ?: "Intermedio"
                val extra = editExtra.text?.toString()?.takeIf { it.isNotBlank() }
                val onlyPriority = checkOnlyPriority.isChecked

                val chatIds = currentFilteredChats.map { it.chatId }
                val hasFilters = filterPackage != null || filterStartTs != null ||
                        filterEndTs != null || !filterText.isNullOrBlank()
                val subtitle = buildString {
                    append("${chatIds.size} chat${if (chatIds.size == 1) "" else "s"}")
                    append(" · Nivel ")
                    append(detail.lowercase())
                    if (onlyPriority) append(" · solo pendientes")
                    if (hasFilters) append(" · filtros aplicados")
                }

                startActivity(
                    SummaryActivity.newIntent(
                        context = requireContext(),
                        chatIds = chatIds,
                        title = if (onlyPriority) "Pendientes e importantes" else "Resumen general",
                        subtitle = subtitle,
                        length = length,
                        detail = detail,
                        extraPrompt = extra,
                        startTs = filterStartTs,
                        endTs = filterEndTs,
                        filterText = filterText,
                        onlyPriority = onlyPriority
                    )
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFilterDialogMain() {
        val dialogView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_filters, null)
        val editSearch = dialogView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editSearch)
        val spinnerChatType = dialogView.findViewById<android.widget.Spinner>(com.example.whatsappsummary.R.id.spinnerChatType)
        val btnPickStart = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonPickStart)
        val btnPickEnd = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonPickEnd)
        val textStart = dialogView.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textStartDialog)
        val textEnd = dialogView.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textEndDialog)
        val btnApply = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonApplyDialog)
        val btnClear = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonClearDialog)

        var startTs: Long? = filterStartTs
        var endTs: Long? = filterEndTs

        val dialog = AlertDialog.Builder(requireContext())
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
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    android.app.TimePickerDialog(
                        requireContext(),
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
                    ).show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnPickEnd.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    android.app.TimePickerDialog(
                        requireContext(),
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
                    ).show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        editSearch.setText(filterText ?: "")
        updateTexts()

        data class AppEntry(val pkg: String, val label: String)
        val pm = requireContext().packageManager

        val entries = mutableListOf<AppEntry>()
        entries.add(AppEntry("", "Todas"))

        val spinnerAdapter = object : android.widget.ArrayAdapter<AppEntry>(requireContext(), com.example.whatsappsummary.R.layout.spinner_item_app, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(com.example.whatsappsummary.R.layout.spinner_item_app, parent, false)
                val item = getItem(position)
                val img = v.findViewById<android.widget.ImageView>(com.example.whatsappsummary.R.id.imageAppIcon)
                val txt = v.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.textAppLabel)
                if (item != null && item.pkg.isNotBlank()) {
                    try {
                        val ai = pm.getApplicationInfo(item.pkg, 0)
                        img.setImageDrawable(pm.getApplicationIcon(ai))
                    } catch (_: Exception) {
                        img.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
                    }
                    txt.text = item.label
                } else {
                    img.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
                    txt.text = item?.label ?: "Todas"
                }
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                getView(position, convertView, parent)
        }

        spinnerChatType.adapter = spinnerAdapter

        viewModel.fetchAllPackages { packages ->
            packages.forEach { pkg ->
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(ai).toString()
                    entries.add(AppEntry(pkg, label))
                } catch (_: Exception) {
                    entries.add(AppEntry(pkg, pkg))
                }
            }
            spinnerAdapter.notifyDataSetChanged()

            if (filterPackage != null) {
                val selectedIndex = entries.indexOfFirst { it.pkg == filterPackage }
                if (selectedIndex >= 0) spinnerChatType.setSelection(selectedIndex)
            }
        }

        btnApply.setOnClickListener {
            val q = editSearch.text?.toString()?.trim()
            val sel = spinnerChatType.selectedItem as? AppEntry
            filterStartTs = startTs
            filterEndTs = endTs
            filterText = q
            filterPackage = sel?.pkg?.takeIf { it.isNotBlank() }

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
            filterPackage = null
            editSearch.setText("")
            spinnerChatType.setSelection(0)

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
        val prefs = requireContext().getSharedPreferences("wa_listener_prefs", Context.MODE_PRIVATE)
        val dialogView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_settings, null)
        val btnGrant = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonGrantPermission)
        val switchWhatsApp = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.whatsappsummary.R.id.switchWhatsApp)
        val switchOthers = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.whatsappsummary.R.id.switchOthers)
        val btnViewDashboard = dialogView.findViewById<android.widget.Button>(com.example.whatsappsummary.R.id.buttonViewDashboard)

        switchWhatsApp.isChecked = prefs.getBoolean("collect_whatsapp", true)
        switchOthers.isChecked = prefs.getBoolean("collect_others", false)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Opciones")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        btnGrant.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        switchWhatsApp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("collect_whatsapp", isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "Recopilación WhatsApp habilitada" else "Recopilación WhatsApp deshabilitada", Toast.LENGTH_SHORT).show()
        }

        switchOthers.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("collect_others", isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "Recopilación otras notificaciones habilitada" else "Recopilación otras notificaciones deshabilitada", Toast.LENGTH_SHORT).show()
        }

        btnViewDashboard.setOnClickListener {
            // Ahora es cambio de página en el ViewPager, no nueva Activity
            navViewModel.pageRequest.value = 0
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteDialog(chatId: String, chatName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar chat")
            .setMessage("¿Deseas eliminar el chat con $chatName? Se eliminarán todos los mensajes y resúmenes asociados.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteChat(chatId)
                Toast.makeText(requireContext(), "Chat eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkNotificationPermission() {
        val notificationListeners = Settings.Secure.getString(
            requireContext().contentResolver,
            "enabled_notification_listeners"
        )
        val isEnabled = notificationListeners?.contains(requireContext().packageName) == true
        if (!isEnabled) {
            AlertDialog.Builder(requireContext())
                .setTitle("Permiso requerido")
                .setMessage("Esta aplicación necesita acceso a las notificaciones para funcionar. Por favor, habilita el acceso en la configuración.")
                .setPositiveButton("Ir a configuración") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
}
