package com.example.whatsappsummary.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_summaries",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("date")]
)
data class DailySummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String, // ID del chat
    val date: String, // Fecha en formato YYYY-MM-DD
    val messageCount: Int, // Cantidad de mensajes ese día
    val summary: String, // Resumen de los mensajes del día
    val timestamp: Long // Timestamp para ordenar
)
