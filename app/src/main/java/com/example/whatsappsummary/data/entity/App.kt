package com.example.whatsappsummary.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "apps",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class App(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val packageName: String,           // Nombre del paquete de la app (com.whatsapp)
    val appName: String? = null        // Nombre legible de la app (WhatsApp)
)
