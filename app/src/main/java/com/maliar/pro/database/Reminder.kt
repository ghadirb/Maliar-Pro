package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val reminderTime: Long,
    val isRecurring: Boolean = false,
    val recurringType: RecurringType = RecurringType.NONE,
    val recurringInterval: Int = 1, // For recurring reminders
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val linkedCheckId: Long? = null, // Link to accounting checks
    val linkedInstallmentId: Long? = null, // Link to accounting installments
    val category: String = "",
    val priority: Priority = Priority.MEDIUM,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RecurringType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}
