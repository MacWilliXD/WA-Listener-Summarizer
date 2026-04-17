package com.example.whatsappsummary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.databinding.ItemSummaryBinding
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SummaryAdapter(
    private val onSummaryClick: (DailySummary) -> Unit
) : ListAdapter<DailySummary, SummaryAdapter.SummaryViewHolder>(SummaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val binding = ItemSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        val summary = getItem(position)
        val showHeader = if (position == 0) true else {
            val prev = getItem(position - 1)
            prev.date != summary.date
        }
        holder.bind(summary, showHeader)
    }

    inner class SummaryViewHolder(
        private val binding: ItemSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isExpanded = false

        fun bind(summary: DailySummary, showHeader: Boolean) {
            val header = binding.root.findViewById<android.widget.TextView>(com.example.whatsappsummary.R.id.headerDate)
            if (showHeader) {
                // summary.date expected format: yyyy-MM-dd
                val headerText = try {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val d = fmt.parse(summary.date)
                    val calMsg = Calendar.getInstance().apply { time = d }
                    val today = Calendar.getInstance()
                    val yesterday = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() - 24*60*60*1000 }
                        val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val dateStr = dateFmt.format(d)
                        when {
                            calMsg.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calMsg.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hoy - $dateStr"
                            calMsg.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && calMsg.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Ayer - $dateStr"
                            else -> dateStr
                        }
                } catch (e: Exception) {
                    summary.date
                }
                header.text = headerText
                header.visibility = android.view.View.VISIBLE
            } else {
                header.visibility = android.view.View.GONE
            }
            binding.textViewDate.text = summary.date
            // Show summary type
            val typeLabel = when (summary.type.lowercase(Locale.getDefault())) {
                "automatic", "automático", "automatico" -> "Automático"
                else -> "Manual"
            }
            try {
                binding.textViewType.text = typeLabel
                val ctx = binding.root.context
                val isAuto = typeLabel == "Automático"
                binding.textViewType.setBackgroundResource(
                    if (isAuto) com.example.whatsappsummary.R.drawable.bg_chip_auto
                    else com.example.whatsappsummary.R.drawable.bg_chip_manual
                )
                binding.textViewType.setTextColor(
                    if (isAuto) android.graphics.Color.parseColor("#8A4F1C")
                    else androidx.core.content.ContextCompat.getColor(ctx, com.example.whatsappsummary.R.color.brand_emerald_900)
                )
            } catch (_: Exception) {}
            binding.textViewMessageCount.text = "${summary.messageCount} mensajes"
            binding.textViewSummary.text = com.example.whatsappsummary.util.MarkdownFormatter.format(summary.summary)
            
            // Inicialmente colapsado
            binding.textViewSummary.maxLines = 3
            binding.buttonExpand.text = "Ver más"
            isExpanded = false
            
            // Toggle expandir/colapsar
            binding.root.setOnClickListener {
                isExpanded = !isExpanded
                if (isExpanded) {
                    binding.textViewSummary.maxLines = Integer.MAX_VALUE
                    binding.buttonExpand.text = "Ver menos"
                } else {
                    binding.textViewSummary.maxLines = 3
                    binding.buttonExpand.text = "Ver más"
                }
            }
            
            binding.buttonExpand.setOnClickListener {
                binding.root.performClick()
            }
        }
    }

    class SummaryDiffCallback : DiffUtil.ItemCallback<DailySummary>() {
        override fun areItemsTheSame(oldItem: DailySummary, newItem: DailySummary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DailySummary, newItem: DailySummary): Boolean {
            return oldItem == newItem
        }
    }
}
