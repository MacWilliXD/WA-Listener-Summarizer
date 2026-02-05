package com.example.whatsappsummary

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class WhatsAppSummaryApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager se inicializa automáticamente, pero podemos configurarlo aquí si es necesario
    }
}