package com.example.whatsappsummary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String?,
    @ColumnInfo(name = "chat_id") val chatId: String?,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "is_group") val isGroup: Boolean = false,
    @ColumnInfo(name = "extras_json") val extrasJson: String? = null
)
