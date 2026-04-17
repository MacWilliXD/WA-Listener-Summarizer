package com.example.whatsappsummary

import android.app.Application
import androidx.work.Configuration
import com.example.whatsappsummary.service.SummaryReminderWorker

class WhatsAppSummaryApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Programa los recordatorios diarios de resumen (mañana 07:00 y noche 21:00).
        // Usa `ExistingPeriodicWorkPolicy.UPDATE` internamente, así que re-programarlos
        // en cada arranque es seguro y además los re-ajusta si el usuario cambió la hora.
        try {
            SummaryReminderWorker.scheduleAll(this)
        } catch (e: Exception) {
            android.util.Log.e("Notirizer", "No se pudieron programar recordatorios", e)
        }
    }
}