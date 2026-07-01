package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "advanced_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val reminderType: String = ReminderType.SIMPLE.name,
    val priority: String = Priority.MEDIUM.name,
    val alertType: String = AlertType.NOTIFICATION.name,
    val triggerTime: Long,
    val repeatPattern: String = RepeatPattern.ONCE.name,
    val customRepeatDays: String = "", // comma-separated: "0,1,2,3,4,5,6"
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationRadius: Int = 100,
    val locationName: String = "",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    val relatedPerson: String = "",
    val snoozeCount: Int = 0,
    val lastSnoozed: Long? = null,
    val notes: String = "",
    val category: String = "",
    val linkedCheckId: Long? = null,
    val linkedInstallmentId: Long? = null
)

enum class ReminderType {
    SIMPLE, RECURRING, LOCATION_BASED, BIRTHDAY, ANNIVERSARY, BILL_PAYMENT, MEDICINE, TASK, CONDITIONAL
}

enum class AlertType {
    NONE, NOTIFICATION, FULL_SCREEN, SMART
}

enum class RepeatPattern {
    ONCE, DAILY, WEEKLY, MONTHLY, YEARLY, WEEKDAYS, WEEKENDS, CUSTOM
}