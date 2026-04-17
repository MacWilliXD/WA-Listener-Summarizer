package com.example.whatsappsummary.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = App::class,
            parentColumns = ["id"],
            childColumns = ["appId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["appId"])]
)
data class Chat(
    @PrimaryKey
    val chatId: String,              // ID único del chat (número o nombre del grupo)
    val chatName: String,            // Nombre del contacto o grupo
    val appId: Long,                 // ID de la aplicación (referencia a tabla apps)
    val isGroup: Boolean = false,    // Si es un chat grupal
    val unreadCount: Int = 0         // Cantidad de mensajes no leídos
)

// Clase auxiliar para combinar Chat con lastMessageTime, último mensaje y metadata de app
data class ChatWithLastMessage(
    val chat: Chat,
    val lastMessageTime: Long = 0L,
    val lastMessagePreview: String = "",
    val packageName: String = "",
    val appName: String = "",
    val isSocial: Boolean = false
) {
    val chatId: String get() = chat.chatId
    val chatName: String get() = chat.chatName
    val appId: Long get() = chat.appId
    val isGroup: Boolean get() = chat.isGroup
    val unreadCount: Int get() = chat.unreadCount

    /** "app:<pkg>" → agrupación por app, no por chat. */
    val isAppBucket: Boolean get() = chat.chatId.startsWith("app:")
}
