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
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
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

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}
