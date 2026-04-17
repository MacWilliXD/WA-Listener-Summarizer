package com.example.whatsappsummary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.R
import com.example.whatsappsummary.data.entity.ChatWithLastMessage
import com.example.whatsappsummary.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private val onChatClick: (ChatWithLastMessage) -> Unit,
    private val onChatLongClick: (ChatWithLastMessage) -> Unit
) : ListAdapter<ChatWithLastMessage, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun submitList(list: List<ChatWithLastMessage>?) {
        // Orden: sociales primero (por último mensaje), luego no-sociales (app buckets)
        val sorted = list.orEmpty().sortedWith(
            compareByDescending<ChatWithLastMessage> { it.isSocial }
                .thenByDescending { it.lastMessageTime }
        )
        super.submitList(sorted)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = getItem(position)
        val prev = if (position > 0) getItem(position - 1) else null
        val sectionLabel = when {
            position == 0 && chat.isSocial -> "Mensajería"
            position == 0 && !chat.isSocial -> "Otras apps"
            prev != null && prev.isSocial && !chat.isSocial -> "Otras apps"
            prev != null && !prev.isSocial && chat.isSocial -> "Mensajería"
            else -> null
        }
        holder.bind(chat, sectionLabel)
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatWithLastMessage, sectionHeader: String?) {
            val header = binding.root.findViewById<android.widget.TextView>(R.id.headerDate)
            if (sectionHeader != null) {
                header.visibility = View.VISIBLE
                header.text = sectionHeader
            } else {
                header.visibility = View.GONE
            }

            binding.textViewChatName.text = chat.chatName.ifBlank {
                chat.appName.ifBlank { "Chat" }
            }

            // Preview: si es app-bucket y hay más de un no leído, mostrar "N notificaciones"
            val preview = when {
                chat.lastMessagePreview.isNotBlank() -> chat.lastMessagePreview
                chat.isAppBucket -> chat.appName
                else -> ""
            }
            binding.textViewLastMessage.text = preview
            binding.textViewLastMessage.visibility =
                if (preview.isBlank()) View.GONE else View.VISIBLE

            // Timestamp: si es hoy "HH:mm", si es ayer "Ayer", si es esta semana día, sino dd/MM
            binding.textViewTime.text = formatRelativeTime(chat.lastMessageTime)

            if (chat.unreadCount > 0) {
                binding.textViewUnreadCount.visibility = View.VISIBLE
                binding.textViewUnreadCount.text =
                    if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
            } else {
                binding.textViewUnreadCount.visibility = View.GONE
            }

            // Icono: priorizar packageName enriquecido; fallback a heurística antigua
            val ctx = binding.root.context
            val pm = ctx.packageManager
            val pkg = when {
                chat.packageName.isNotBlank() -> chat.packageName
                chat.chatId.startsWith("app:") -> chat.chatId.removePrefix("app:")
                chat.chatId.contains("|") -> chat.chatId.substringBefore("|")
                else -> "com.whatsapp"
            }
            val iconDrawable = try {
                pm.getApplicationIcon(pkg)
            } catch (_: Exception) {
                try { pm.getApplicationIcon("com.whatsapp") }
                catch (_: Exception) { ctx.getDrawable(R.mipmap.ic_launcher) }
            }
            binding.imageViewAppIcon.setImageDrawable(iconDrawable)

            binding.root.setOnClickListener { onChatClick(chat) }
            binding.root.setOnLongClickListener {
                onChatLongClick(chat)
                true
            }
        }

        private fun formatRelativeTime(timestamp: Long): String {
            if (timestamp <= 0L) return ""
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = timestamp }
            val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
            if (sameDay) {
                return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            // Ayer
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val isYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
            if (isYesterday) return "Ayer"
            // Esta semana → día
            val diffDays = ((now.timeInMillis - timestamp) / (1000L * 60 * 60 * 24)).toInt()
            if (diffDays in 0..6) {
                return SimpleDateFormat("EEE", Locale.getDefault())
                    .format(Date(timestamp))
                    .replaceFirstChar { it.uppercase() }
            }
            return SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatWithLastMessage>() {
        override fun areItemsTheSame(oldItem: ChatWithLastMessage, newItem: ChatWithLastMessage): Boolean =
            oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: ChatWithLastMessage, newItem: ChatWithLastMessage): Boolean =
            oldItem == newItem
    }
}
