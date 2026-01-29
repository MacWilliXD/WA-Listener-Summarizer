package com.example.whatsappsummary.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.whatsappsummary.data.entity.DailySummary

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summaries WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getSummariesByChatId(chatId: String): LiveData<List<DailySummary>>
    
    @Query("SELECT * FROM daily_summaries WHERE chatId = :chatId AND date >= :startDate AND date <= :endDate ORDER BY timestamp ASC")
    suspend fun getSummariesByDateRange(chatId: String, startDate: String, endDate: String): List<DailySummary>
    
    @Query("SELECT * FROM daily_summaries WHERE chatId = :chatId AND date = :date")
    suspend fun getSummaryByDate(chatId: String, date: String): DailySummary?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: DailySummary)
    
    @Update
    suspend fun updateSummary(summary: DailySummary)
    
    @Delete
    suspend fun deleteSummary(summary: DailySummary)
    
    @Query("DELETE FROM daily_summaries WHERE chatId = :chatId")
    suspend fun deleteSummariesByChatId(chatId: String)
}
