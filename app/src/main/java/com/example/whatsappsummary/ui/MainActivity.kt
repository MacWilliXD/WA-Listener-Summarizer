package com.example.whatsappsummary.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
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
    private var filterStartTs: Long? = null
    private var filterEndTs: Long? = null
    private var filterText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupFab()
        setupFilterFab()
        
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
        viewModel.allChats.observe(this) { chats ->
            fullChats = chats
            adapter.submitList(chats)
            
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
        fullChats.map { it.packageName }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { pkg ->
                val label = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString() + " (" + pkg + ")"
                } catch (e: Exception) {
                    pkg
                }
                entries.add(AppEntry(pkg, label))
            }

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

        btnApply.setOnClickListener {
            val q = editSearch.text?.toString()?.trim()
            val nq = q?.let { normalize(it) }
            val sel = spinnerChatType.selectedItem as? AppEntry
            val selectedPkg = sel?.pkg?.takeIf { it.isNotBlank() }

            val defaultStart = 0L
            val defaultEnd = 4102444800000L
            val s = startTs ?: defaultStart
            val e = endTs ?: defaultEnd

            val filtered = fullChats.filter { chat ->
                val inRange = chat.lastMessageTime in s..e
                val matches = nq?.let { normalize(chat.chatName ?: "").contains(it) } ?: true
                val pkgMatch = selectedPkg?.let { chat.packageName == it } ?: true
                inRange && matches && pkgMatch
            }
            adapter.submitList(filtered)
            // persist applied filters for next dialog open
            filterStartTs = startTs
            filterEndTs = endTs
            filterText = q
            dialog.dismiss()
        }

        btnClear.setOnClickListener {
            startTs = null
            endTs = null
            filterStartTs = null
            filterEndTs = null
            filterText = null
            editSearch.setText("")
            spinnerChatType.setSelection(0)
            adapter.submitList(fullChats)
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
}
