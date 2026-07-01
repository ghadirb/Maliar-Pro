package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderEntityDao {
    
    @Query("SELECT * FROM advanced_reminders ORDER BY triggerTime ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM advanced_reminders ORDER BY triggerTime ASC")
    suspend fun getAllRemindersList(): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 ORDER BY triggerTime ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 ORDER BY triggerTime ASC")
    suspend fun getActiveRemindersList(): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 AND triggerTime <= :timestamp ORDER BY triggerTime ASC")
    suspend fun getDueReminders(timestamp: Long): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 AND triggerTime BETWEEN :start AND :end ORDER BY triggerTime ASC")
    suspend fun getRemindersBetween(start: Long, end: Long): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long
    
    @Update
    suspend fun updateReminder(reminder: ReminderEntity)
    
    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)
    
    @Query("DELETE FROM advanced_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)
    
    @Query("UPDATE advanced_reminders SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markAsCompleted(id: Long, completedAt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM advanced_reminders WHERE isCompleted = 1")
    suspend fun deleteCompletedReminders()
    
    @Query("SELECT COUNT(*) FROM advanced_reminders")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM advanced_reminders WHERE isCompleted = 0")
    suspend fun getActiveCount(): Int
    
    @Query("SELECT COUNT(*) FROM advanced_reminders WHERE isCompleted = 1")
    suspend fun getCompletedCount(): Int
    
    @Query("SELECT COUNT(*) FROM advanced_reminders WHERE isCompleted = 0 AND triggerTime BETWEEN :start AND :end")
    suspend fun getTodayCount(start: Long, end: Long): Int
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 AND reminderType = :type ORDER BY triggerTime ASC")
    suspend fun getRemindersByType(type: String): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 AND repeatPattern != 'ONCE' ORDER BY triggerTime ASC")
    suspend fun getRecurringReminders(): List<ReminderEntity>
    
    @Query("SELECT * FROM advanced_reminders WHERE isCompleted = 0 AND (priority = 'HIGH' OR priority = 'URGENT') ORDER BY triggerTime ASC")
    suspend fun getHighPriorityReminders(): List<ReminderEntity>
}