package com.example.whatsappsummary.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import com.example.whatsappsummary.R
import com.example.whatsappsummary.databinding.DialogAppOptionsBinding
import com.example.whatsappsummary.viewmodel.AppNotificationStats

class AppOptionsDialog(
    private val context: Context,
    private val appStats: AppNotificationStats,
    private val onDelete: () -> Unit,
    private val onIgnoreChanged: (Boolean) -> Unit
) {

    private val binding: DialogAppOptionsBinding = DialogAppOptionsBinding.inflate(
        LayoutInflater.from(context)
    )

    private val dialog: Dialog = Dialog(context, R.style.AppOptionsDialogTheme).apply {
        setContentView(binding.root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
            attributes?.dimAmount = 0.5f
        }
        setCancelable(true)
        setCanceledOnTouchOutside(true)
    }

    init {
        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.textAppName.text = appStats.appName ?: appStats.packageName
        binding.textPackageName.text = appStats.packageName

        // Cargar el estado del switch desde SharedPreferences
        // Por defecto todas las aplicaciones están permitidas (isIgnored = false)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isIgnored = prefs.getBoolean("ignore_${appStats.packageName}", false)
        binding.switchIgnoreNotifications.isChecked = !isIgnored // Invertido: si no está ignorado, switch activado

        // Intentar cargar el icono de la aplicación
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(appStats.packageName, 0)
            binding.imageAppIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
        } catch (e: Exception) {
            binding.imageAppIcon.setImageResource(R.mipmap.ic_launcher)
        }
    }

    private fun setupListeners() {
        binding.buttonDelete.setOnClickListener {
            onDelete()
            dialog.dismiss()
        }

        binding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.switchIgnoreNotifications.setOnCheckedChangeListener { _, isChecked ->
            // Switch activado = permitir notificaciones (isIgnored = false)
            // Switch desactivado = no permitir notificaciones (isIgnored = true)
            val isIgnored = !isChecked

            // Guardar el estado en SharedPreferences
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("ignore_${appStats.packageName}", isIgnored).apply()

            onIgnoreChanged(isIgnored)
        }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}