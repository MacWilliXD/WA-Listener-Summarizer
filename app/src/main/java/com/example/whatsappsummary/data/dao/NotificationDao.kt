package com.example.whatsappsummary.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.whatsappsummary.data.entity.Notification

@Dao
interface NotificationDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: Notification): Long

    // Queries para obtener notificaciones/mensajes
    @Query("SELECT * FROM notifications WHERE chat_id = :chatId ORDER BY timestamp ASC")
    suspend fun getNotificationsByChatId(chatId: String): List<Notification>
    
    @Query("SELECT * FROM notifications WHERE chat_id = :chatId AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getNotificationsByChatIdAndRange(chatId: String, start: Long, end: Long): List<Notification>

    @Query("""
        SELECT notifications.* FROM notifications
        INNER JOIN apps ON notifications.app_id = apps.id
        WHERE apps.packageName = :pkg AND timestamp BETWEEN :start AND :end 
        ORDER BY timestamp ASC
    """)
    suspend fun getNotificationsByPackageAndRange(pkg: String, start: Long, end: Long): List<Notification>

    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getNotificationsByRange(start: Long, end: Long): List<Notification>

    @Query("SELECT COUNT(*) FROM notifications WHERE chat_id = :chatId AND text = :text AND timestamp BETWEEN :since AND :now")
    suspend fun countExactNotification(chatId: String, text: String, since: Long, now: Long): Int

    /**
     * Cuenta notificaciones con el mismo texto (case-insensitive) en la misma app
     * dentro de la ventana [since, now]. Usado para dedup cross-chat cuando una
     * misma notificación se reparte en varios chats por parsing de título (p.ej.
     * WhatsApp en grupos: una con "Grupo: Sender" y otra con solo "Sender").
     */
    @Query("""
        SELECT COUNT(*) FROM notifications
        WHERE app_id = :appId
          AND LOWER(text) = LOWER(:text)
          AND timestamp BETWEEN :since AND :now
    """)
    suspend fun countSameTextInAppRecent(appId: Long, text: String, since: Long, now: Long): Int

    @Query("""
        SELECT COUNT(*) FROM notifications 
        WHERE chat_id = :chatId AND text = :text 
        AND datetime(timestamp/1000, 'unixepoch') >= datetime(date('now', '-1 day'))
    """)
    suspend fun countDuplicateByMinute(chatId: String, text: String): Int

    // Queries de conteo y estadísticas
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalNotifications(): Int

    @Query("SELECT DISTINCT apps.packageName FROM notifications INNER JOIN apps ON notifications.app_id = apps.id")
    suspend fun getUniqueApps(): List<String>

    @Query("SELECT * FROM notifications WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getNotificationsSince(since: Long): List<Notification>

    @Query("""
        SELECT notifications.* FROM notifications
        INNER JOIN apps ON notifications.app_id = apps.id
        WHERE apps.packageName = :packageName 
        ORDER BY timestamp DESC
    """)
    suspend fun getNotificationsByPackage(packageName: String): List<Notification>
    
    @Query("SELECT * FROM notifications WHERE app_id = :appId ORDER BY timestamp DESC")
    suspend fun getNotificationsByAppId(appId: Long): List<Notification>

    // Queries de eliminación
    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteNotificationsByIds(ids: List<Long>)

    @Query("""
        DELETE FROM notifications WHERE app_id IN (
            SELECT id FROM apps WHERE packageName = :packageName
        )
    """)
    suspend fun deleteNotificationsByPackage(packageName: String)
    
    @Query("DELETE FROM notifications WHERE chat_id = :chatId")
    suspend fun deleteNotificationsByChatId(chatId: String)
    
    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM notifications WHERE chat_id = :chatId")
    suspend fun getLastMessageTimeForChat(chatId: String): Long

    @Query("SELECT text FROM notifications WHERE chat_id = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageTextForChat(chatId: String): String?

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsLive(): LiveData<List<Notification>>

    // ---- Limpieza ----

    @Query("DELETE FROM notifications WHERE text LIKE '(Notificaci%n autom%tica)%'")
    suspend fun deleteAutoPlaceholderRows(): Int

    @Query("""
        UPDATE notifications SET chat_id = :newChatId
        WHERE chat_id IN (SELECT chatId FROM chats WHERE appId = :appId AND chatId != :newChatId)
    """)
    suspend fun reassignNotificationsToBucket(appId: Long, newChatId: String)
}
