package com.example.whatsappsummary.ui.adapter

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsappsummary.R
import com.example.whatsappsummary.databinding.ItemAppStatsBinding
import com.example.whatsappsummary.viewmodel.AppNotificationStats

class AppStatsAdapter(
    private val onAppClick: (AppNotificationStats) -> Unit,
    private val onAppLongClick: (AppNotificationStats) -> Unit
) : ListAdapter<AppNotificationStats, AppStatsAdapter.AppStatsViewHolder>(AppStatsDiffCallback()) {

    private lateinit var packageManager: PackageManager

    fun setPackageManager(pm: PackageManager) {
        packageManager = pm
    }

    // Función para formatear números grandes con sufijos
    private fun formatLargeNumber(value: Int): String {
        val absValue = Math.abs(value).toFloat()
        return when {
            absValue >= 1_000_000_000_000 -> String.format("%.1fT", value / 1_000_000_000_000f) // Trillones
            absValue >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000f) // Billones
            absValue >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f) // Millones
            absValue >= 1_000 -> String.format("%.1fK", value / 1_000f) // Miles
            else -> value.toString() // Valores normales sin decimales
        }
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

            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppLongClick(getItem(position))
                    true
                } else {
                    false
                }
            }
        }

        fun bind(appStats: AppNotificationStats) {
            binding.textAppName.text = appStats.appName ?: appStats.packageName
            binding.textPackageName.text = appStats.packageName
            binding.textNotificationCount.text = formatLargeNumber(appStats.notificationCount)

            // Configurar el texto de "hoy" en lugar del texto estático "notificaciones"
            val todayText = if (appStats.todayNotificationCount > 0) {
                "${formatLargeNumber(appStats.todayNotificationCount)} hoy"
            } else {
                "0 hoy"
            }
            binding.textTodayCount.text = todayText

            // Verificar si la aplicación está ignorada
            val context = binding.root.context
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isIgnored = prefs.getBoolean("ignore_${appStats.packageName}", false)

            // Cambiar apariencia visual para aplicaciones ignoradas
            if (isIgnored) {
                // Aplicar estilo completo de ignorado a toda la card
                binding.root.alpha = 0.7f
                binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorSurfaceVariant))
                binding.textAppName.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.textPackageName.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.textNotificationCount.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.textTodayCount.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.imageAppIcon.alpha = 0.6f
            } else {
                // Restaurar estilo normal
                binding.root.alpha = 1.0f
                binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorSurface))
                binding.textAppName.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                binding.textPackageName.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.textNotificationCount.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                binding.textTodayCount.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
                binding.imageAppIcon.alpha = 1.0f
            }

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
            // Siempre retornar false para forzar el rebind y verificar el estado de ignorado
            return false
        }
    }
}