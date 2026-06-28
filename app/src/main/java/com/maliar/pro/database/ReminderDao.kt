package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders ORDER BY reminderTime ASC")
    fun getAllReminders(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders ORDER BY reminderTime ASC")
    suspend fun getAllRemindersList(): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY reminderTime ASC")
    fun getActiveReminders(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY reminderTime ASC")
    suspend fun getActiveRemindersList(): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE reminderTime <= :timestamp AND isCompleted = 0")
    suspend fun getDueReminders(timestamp: Long): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE linkedCheckId = :checkId")
    suspend fun getRemindersForCheck(checkId: Long): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE linkedInstallmentId = :installmentId")
    suspend fun getRemindersForInstallment(installmentId: Long): List<Reminder>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReminders(reminders: List<Reminder>)
    
    @Update
    suspend fun updateReminder(reminder: Reminder)
    
    @Delete
    suspend fun deleteReminder(reminder: Reminder)
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)
    
    @Query("DELETE FROM reminders WHERE isCompleted = 1")
    suspend fun deleteCompletedReminders()
    
    @Query("UPDATE reminders SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markAsCompleted(id: Long, completedAt: Long = System.currentTimeMillis())
}
