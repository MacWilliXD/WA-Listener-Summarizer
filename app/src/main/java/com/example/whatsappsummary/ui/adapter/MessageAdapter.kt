package com.example.whatsappsummary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.data.entity.Message
import com.example.whatsappsummary.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val showHeader = if (position == 0) true else {
            val prev = getItem(position - 1)
            !isSameDay(prev.timestamp, message.timestamp)
        }
        holder.bind(message, showHeader)
    }

    class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message, showHeader: Boolean) {
            val header = binding.root.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.headerDate)
            if (showHeader) {
                val cal = Calendar.getInstance()
                val today = Calendar.getInstance()
                today.timeInMillis = System.currentTimeMillis()
                val yesterday = Calendar.getInstance()
                yesterday.timeInMillis = System.currentTimeMillis() - 24*60*60*1000

                val msgCal = Calendar.getInstance().apply { timeInMillis = message.timestamp }
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(message.timestamp))
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
            binding.textViewSender.text = message.senderName
            binding.textViewMessage.text = message.messageText
            
            // Formatear timestamp (mostrar hora)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.textViewTime.text = timeFmt.format(Date(message.timestamp))
        }
    }

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
