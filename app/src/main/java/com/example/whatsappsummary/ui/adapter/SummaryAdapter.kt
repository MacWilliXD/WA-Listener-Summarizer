package com.example.whatsappsummary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.data.entity.DailySummary
import com.example.whatsappsummary.databinding.ItemSummaryBinding

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
        holder.bind(getItem(position))
    }

    inner class SummaryViewHolder(
        private val binding: ItemSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isExpanded = false

        fun bind(summary: DailySummary) {
            binding.textViewDate.text = summary.date
            binding.textViewMessageCount.text = "${summary.messageCount} mensajes"
            binding.textViewSummary.text = summary.summary
            
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
