package com.example.whatsappsummary.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey
    val chatId: String, // ID único del chat (número o nombre del grupo)
    val chatName: String, // Nombre del contacto o grupo
    val lastMessage: String, // Último mensaje recibido
    val lastMessageTime: Long, // Timestamp del último mensaje
    val unreadCount: Int = 0 // Cantidad de mensajes no leídos
)
