package com.example.whatsappsummary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = App::class,
            parentColumns = ["id"],
            childColumns = ["app_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["chatId"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["app_id"]),
        Index(value = ["chat_id"]),
        Index(value = ["timestamp"])
    ]
)
data class Notification(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @ColumnInfo(name = "app_id") 
    val appId: Long,                        // ID de la aplicación (referencia a tabla apps)
    
    @ColumnInfo(name = "chat_id") 
    val chatId: String?,                    // ID del chat (referencia a tabla chats)
    
    @ColumnInfo(name = "title") 
    val title: String?,                     // Título de la notificación (nombre del chat)
    
    @ColumnInfo(name = "text") 
    val text: String,                       // Contenido del mensaje/notificación
    
    @ColumnInfo(name = "timestamp") 
    val timestamp: Long,                    // Timestamp de recepción
    
    @ColumnInfo(name = "sender") 
    val sender: String? = null,             // Remitente del mensaje (si disponible)
    
    @ColumnInfo(name = "is_from_me") 
    val isFromMe: Boolean = false,          // Si el mensaje es del usuario
    
    @ColumnInfo(name = "is_group") 
    val isGroup: Boolean = false,           // Si es un chat grupal
    
    @ColumnInfo(name = "extras_json") 
    val extrasJson: String? = null          // Datos extras en JSON
)
