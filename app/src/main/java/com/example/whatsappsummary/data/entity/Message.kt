package com.example.whatsappsummary.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("timestamp")]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String, // ID del chat al que pertenece
    val senderName: String, // Nombre del remitente
    val messageText: String, // Contenido del mensaje
    val timestamp: Long, // Timestamp del mensaje
    val isGroupMessage: Boolean = false // Si es mensaje de grupo
)
