package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ReminderManager(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val reminderEntityDao = database.reminderEntityDao()
    
    fun getAllReminders(): Flow<List<ReminderEntity>> {
        return reminderEntityDao.getAllReminders()
    }
    
    suspend fun getAllRemindersList(): List<ReminderEntity> {
        return reminderEntityDao.getAllRemindersList()
    }
    
    fun getActiveReminders(): Flow<List<ReminderEntity>> {
        return reminderEntityDao.getActiveReminders()
    }
    
    suspend fun getActiveRemindersList(): List<ReminderEntity> {
        return reminderEntityDao.getActiveRemindersList()
    }
    
    suspend fun getDueReminders(timestamp: Long = System.currentTimeMillis()): List<ReminderEntity> {
        return reminderEntityDao.getDueReminders(timestamp)
    }
    
    suspend fun addReminder(reminder: ReminderEntity): Long {
        return reminderEntityDao.insertReminder(reminder)
    }
    
    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderEntityDao.updateReminder(reminder)
    }
    
    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderEntityDao.deleteReminder(reminder)
    }
    
    suspend fun deleteReminderById(id: Long) {
        reminderEntityDao.deleteReminderById(id)
    }
    
    suspend fun markAsCompleted(id: Long) {
        reminderEntityDao.markAsCompleted(id)
    }
    
    suspend fun deleteCompletedReminders() {
        reminderEntityDao.deleteCompletedReminders()
    }
    
    // Integration with Accounting
    suspend fun createReminderForCheck(checkId: Long, checkTitle: String, dueDate: Long): Long {
        val reminder = ReminderEntity(
            title = "سررسید چک: $checkTitle",
            description = "چک به سررسید رسیده است",
            triggerTime = dueDate,
            linkedCheckId = checkId,
            priority = Priority.HIGH.name,
            reminderType = ReminderType.BILL_PAYMENT.name
        )
        return addReminder(reminder)
    }
    
    suspend fun createReminderForInstallment(installmentId: Long, installmentTitle: String, paymentDate: Long): Long {
        val reminder = ReminderEntity(
            title = "پرداخت قسط: $installmentTitle",
            description = "زمان پرداخت قسط فرا رسیده است",
            triggerTime = paymentDate,
            linkedInstallmentId = installmentId,
            priority = Priority.HIGH.name,
            reminderType = ReminderType.BILL_PAYMENT.name
        )
        return addReminder(reminder)
    }
}
