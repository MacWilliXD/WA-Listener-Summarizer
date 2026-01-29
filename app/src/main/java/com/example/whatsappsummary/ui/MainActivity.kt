package com.example.whatsappsummary.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.ActivityMainBinding
import com.example.whatsappsummary.ui.adapter.ChatListAdapter
import com.example.whatsappsummary.viewmodel.ChatListViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatListViewModel
    private lateinit var adapter: ChatListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupFab()
        
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
            // Abrir configuración de acceso a notificaciones
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
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
