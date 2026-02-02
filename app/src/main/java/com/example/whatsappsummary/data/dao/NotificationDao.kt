package com.example.whatsappsummary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.whatsappsummary.data.entity.Notification

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: Notification): Long

    @Query("SELECT * FROM notifications WHERE chat_id = :chatId AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getNotificationsByChatIdAndRange(chatId: String, start: Long, end: Long): List<Notification>

    @Query("SELECT * FROM notifications WHERE package_name = :pkg AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getNotificationsByPackageAndRange(pkg: String, start: Long, end: Long): List<Notification>

    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getNotificationsByRange(start: Long, end: Long): List<Notification>

    @Query("SELECT COUNT(*) FROM notifications WHERE chat_id = :chatId AND text = :text AND timestamp BETWEEN :since AND :now")
    suspend fun countExactNotification(chatId: String, text: String, since: Long, now: Long): Int

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalNotifications(): Int

    @Query("SELECT DISTINCT package_name FROM notifications")
    suspend fun getUniqueApps(): List<String>

    @Query("SELECT * FROM notifications WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getNotificationsSince(since: Long): List<Notification>

    @Query("SELECT * FROM notifications WHERE package_name = :packageName ORDER BY timestamp DESC")
    suspend fun getNotificationsByPackage(packageName: String): List<Notification>

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteNotificationsByIds(ids: List<Long>)
}
