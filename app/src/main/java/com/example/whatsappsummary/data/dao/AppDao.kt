package com.example.whatsappsummary.data.dao

import androidx.room.*
import com.example.whatsappsummary.data.entity.App

@Dao
interface AppDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: App): Long
    
    @Update
    suspend fun updateApp(app: App)
    
    @Query("SELECT * FROM apps WHERE id = :appId")
    suspend fun getAppById(appId: Long): App?
    
    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): App?
    
    @Query("SELECT * FROM apps ORDER BY appName ASC")
    suspend fun getAllApps(): List<App>
    
    @Query("SELECT COUNT(*) FROM apps")
    suspend fun getAppsCount(): Int
    
    @Delete
    suspend fun deleteApp(app: App)
    
    @Query("DELETE FROM apps WHERE id = :appId")
    suspend fun deleteAppById(appId: Long)
}
