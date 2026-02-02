package com.example.whatsappsummary.ui.adapter

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.databinding.ItemAppStatsBinding
import com.example.whatsappsummary.viewmodel.AppNotificationStats

class AppStatsAdapter(
    private val onAppClick: (AppNotificationStats) -> Unit
) : ListAdapter<AppNotificationStats, AppStatsAdapter.AppStatsViewHolder>(AppStatsDiffCallback()) {

    private lateinit var packageManager: PackageManager

    fun setPackageManager(pm: PackageManager) {
        packageManager = pm
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppStatsViewHolder {
        val binding = ItemAppStatsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppStatsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppStatsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppStatsViewHolder(
        private val binding: ItemAppStatsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(position))
                }
            }
        }

        fun bind(appStats: AppNotificationStats) {
            binding.textAppName.text = appStats.appName ?: appStats.packageName
            binding.textPackageName.text = appStats.packageName
            binding.textNotificationCount.text = appStats.notificationCount.toString()

            // Intentar cargar el icono de la aplicación
            try {
                if (::packageManager.isInitialized) {
                    val appInfo = packageManager.getApplicationInfo(appStats.packageName, 0)
                    binding.imageAppIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                } else {
                    binding.imageAppIcon.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
                }
            } catch (e: Exception) {
                binding.imageAppIcon.setImageResource(com.example.whatsappsummary.R.mipmap.ic_launcher)
            }
        }
    }

    class AppStatsDiffCallback : DiffUtil.ItemCallback<AppNotificationStats>() {
        override fun areItemsTheSame(
            oldItem: AppNotificationStats,
            newItem: AppNotificationStats
        ): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(
            oldItem: AppNotificationStats,
            newItem: AppNotificationStats
        ): Boolean {
            return oldItem == newItem
        }
    }
}