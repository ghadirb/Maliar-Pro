package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ReminderManager(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val reminderDao = database.reminderDao()
    
    fun getAllReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllReminders()
    }
    
    suspend fun getAllRemindersList(): List<Reminder> {
        return reminderDao.getAllRemindersList()
    }
    
    fun getActiveReminders(): Flow<List<Reminder>> {
        return reminderDao.getActiveReminders()
    }
    
    suspend fun getActiveRemindersList(): List<Reminder> {
        return reminderDao.getActiveRemindersList()
    }
    
    suspend fun getDueReminders(timestamp: Long = System.currentTimeMillis()): List<Reminder> {
        return reminderDao.getDueReminders(timestamp)
    }
    
    suspend fun addReminder(reminder: Reminder): Long {
        return reminderDao.insertReminder(reminder)
    }
    
    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }
    
    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }
    
    suspend fun markAsCompleted(id: Long) {
        reminderDao.markAsCompleted(id)
    }
    
    suspend fun deleteCompletedReminders() {
        reminderDao.deleteCompletedReminders()
    }
    
    // Integration with Accounting
    suspend fun createReminderForCheck(checkId: Long, checkTitle: String, dueDate: Long): Long {
        val reminder = Reminder(
            title = "سررسید چک: $checkTitle",
            description = "چک به سررسید رسیده است",
            reminderTime = dueDate,
            linkedCheckId = checkId,
            priority = Priority.HIGH
        )
        return addReminder(reminder)
    }
    
    suspend fun createReminderForInstallment(installmentId: Long, installmentTitle: String, paymentDate: Long): Long {
        val reminder = Reminder(
            title = "پرداخت قسط: $installmentTitle",
            description = "زمان پرداخت قسط فرا رسیده است",
            reminderTime = paymentDate,
            linkedInstallmentId = installmentId,
            priority = Priority.HIGH
        )
        return addReminder(reminder)
    }
}
