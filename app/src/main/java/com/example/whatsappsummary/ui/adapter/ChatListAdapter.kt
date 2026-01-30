package com.example.whatsappsummary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.data.entity.Chat
import com.example.whatsappsummary.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatListAdapter(
    private val onChatClick: (Chat) -> Unit,
    private val onChatLongClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = getItem(position)
        val showHeader = if (position == 0) true else {
            val prev = getItem(position - 1)
            !isSameDay(prev.lastMessageTime, chat.lastMessageTime)
        }
        holder.bind(chat, showHeader)
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat, showHeader: Boolean) {
            val header = binding.root.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.headerDate)
            if (showHeader) {
                val cal = Calendar.getInstance()
                val today = Calendar.getInstance()
                today.timeInMillis = System.currentTimeMillis()
                val yesterday = Calendar.getInstance()
                yesterday.timeInMillis = System.currentTimeMillis() - 24*60*60*1000

                val msgCal = Calendar.getInstance().apply { timeInMillis = chat.lastMessageTime }
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(chat.lastMessageTime))
                val headerText = when {
                    msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hoy - $dateStr"
                    msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Ayer - $dateStr"
                    else -> dateStr
                }
                header.text = headerText
                header.visibility = android.view.View.VISIBLE
            } else {
                header.visibility = android.view.View.GONE
            }
            
            binding.textViewChatName.text = chat.chatName
            binding.textViewLastMessage.text = chat.lastMessage
            
            // Formatear tiempo
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = dateFormat.format(Date(chat.lastMessageTime))
            binding.textViewTime.text = time
            
            // Mostrar contador de no leídos
            if (chat.unreadCount > 0) {
                binding.textViewUnreadCount.visibility = android.view.View.VISIBLE
                binding.textViewUnreadCount.text = chat.unreadCount.toString()
            } else {
                binding.textViewUnreadCount.visibility = android.view.View.GONE
            }

            // Show app icon: for 'other' notifications chatId has format "package|id"
            val ctx = binding.root.context
            val pm = ctx.packageManager
            val iconDrawable = try {
                if (chat.chatId.contains("|")) {
                    val pkg = chat.chatId.substringBefore("|")
                    pm.getApplicationIcon(pkg)
                } else {
                    // try WhatsApp icons, fallback to app launcher icon
                    try {
                        pm.getApplicationIcon("com.whatsapp")
                    } catch (e: Exception) {
                        try {
                            pm.getApplicationIcon("com.whatsapp.w4b")
                        } catch (e2: Exception) {
                            ctx.getDrawable(com.example.whatsappsummary.R.mipmap.ic_launcher)
                        }
                    }
                }
            } catch (e: Exception) {
                ctx.getDrawable(com.example.whatsappsummary.R.mipmap.ic_launcher)
            }
            binding.imageViewAppIcon.setImageDrawable(iconDrawable)
            
            // Click listeners
            binding.root.setOnClickListener {
                onChatClick(chat)
            }
            
            binding.root.setOnLongClickListener {
                onChatLongClick(chat)
                true
            }
        }
    }

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}
