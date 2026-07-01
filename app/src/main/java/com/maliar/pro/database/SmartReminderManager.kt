package com.maliar.pro.database

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.maliar.pro.receivers.ReminderReceiver
import com.maliar.pro.ui.reminders.FullScreenAlarmActivity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class SmartReminderManager(private val context: Context) {

    companion object {
        private const val TAG = "SmartReminder"
    }

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.reminderEntityDao()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // CRUD Operations
    fun getAllReminders(): Flow<List<ReminderEntity>> = dao.getAllReminders()
    
    suspend fun getAllRemindersList(): List<ReminderEntity> = dao.getAllRemindersList()
    
    fun getActiveReminders(): Flow<List<ReminderEntity>> = dao.getActiveReminders()
    
    suspend fun getActiveRemindersList(): List<ReminderEntity> = dao.getActiveRemindersList()
    
    suspend fun getReminderById(id: Long): ReminderEntity? = dao.getReminderById(id)

    suspend fun addReminder(reminder: ReminderEntity): Long {
        val id = dao.insertReminder(reminder)
        if (reminder.triggerTime > 0) {
            scheduleAlarm(reminder.copy(id = id))
        }
        return id
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        dao.updateReminder(reminder)
        cancelAlarm(reminder.id)
        if (reminder.triggerTime > 0) {
            scheduleAlarm(reminder)
        }
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        dao.deleteReminder(reminder)
        cancelAlarm(reminder.id)
    }

    suspend fun markAsCompleted(id: Long) {
        dao.markAsCompleted(id)
        cancelAlarm(id)
    }

    suspend fun completeReminder(id: Long) {
        val reminder = dao.getReminderById(id) ?: return
        if (reminder.repeatPattern == RepeatPattern.ONCE.name) {
            dao.markAsCompleted(id)
            cancelAlarm(id)
        } else {
            // Calculate next trigger for recurring
            val nextTime = calculateNextTriggerTime(
                reminder.triggerTime,
                RepeatPattern.valueOf(reminder.repeatPattern),
                parseCustomDays(reminder.customRepeatDays)
            )
            val updated = reminder.copy(
                triggerTime = nextTime,
                isCompleted = false
            )
            dao.updateReminder(updated)
            scheduleAlarm(updated)
        }
    }

    suspend fun snoozeReminder(id: Long, minutes: Int = 10) {
        val reminder = dao.getReminderById(id) ?: return
        val newTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val updated = reminder.copy(
            triggerTime = newTime,
            snoozeCount = reminder.snoozeCount + 1,
            lastSnoozed = System.currentTimeMillis()
        )
        dao.updateReminder(updated)
        scheduleAlarm(updated)
    }

    // Stats
    suspend fun getTotalCount(): Int = dao.getTotalCount()
    suspend fun getActiveCount(): Int = dao.getActiveCount()
    suspend fun getCompletedCount(): Int = dao.getCompletedCount()
    suspend fun getTodayCount(): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis
        return dao.getTodayCount(start, end)
    }

    // Filtering
    suspend fun getRemindersByType(type: ReminderType): List<ReminderEntity> =
        dao.getRemindersByType(type.name)

    suspend fun getRecurringReminders(): List<ReminderEntity> = dao.getRecurringReminders()
    suspend fun getHighPriorityReminders(): List<ReminderEntity> = dao.getHighPriorityReminders()

    suspend fun getDueReminders(): List<ReminderEntity> {
        val now = Calendar.getInstance()
        val endOfDay = now.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        return dao.getRemindersBetween(now.timeInMillis, endOfDay.timeInMillis)
    }

    // Alarm Scheduling
    private fun scheduleAlarm(reminder: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms - permission needed")
                return
            }
        }

        val useAlarm = reminder.alertType == AlertType.FULL_SCREEN.name ||
                      reminder.alertType == AlertType.SMART.name

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_description", reminder.description)
            putExtra("reminder_priority", reminder.priority)
            putExtra("alert_type", reminder.alertType)
            putExtra("use_alarm", useAlarm)
            putExtra("repeat_pattern", reminder.repeatPattern)
            putExtra("reminder_type", reminder.reminderType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            var triggerTime = reminder.triggerTime
            val now = System.currentTimeMillis()

            if (triggerTime < now && reminder.repeatPattern != RepeatPattern.ONCE.name) {
                triggerTime = calculateNextTriggerTime(
                    triggerTime,
                    RepeatPattern.valueOf(reminder.repeatPattern),
                    parseCustomDays(reminder.customRepeatDays)
                )
            } else if (triggerTime < now) {
                triggerTime = now + 1000
            }

            if (useAlarm) {
                val showIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("reminder_id", reminder.id)
                    putExtra("reminder_title", reminder.title)
                    putExtra("reminder_description", reminder.description)
                    putExtra("reminder_priority", reminder.priority)
                    putExtra("alert_type", reminder.alertType)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    reminder.id.toInt() + 5000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }
            Log.d(TAG, "✅ Alarm scheduled for: ${reminder.title} at $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling alarm", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, reminder.triggerTime, pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Error scheduling fallback alarm", e2)
            }
        }
    }

    fun rescheduleAllActiveReminders() {
        kotlinx.coroutines.runBlocking {
            val reminders = dao.getActiveRemindersList()
            reminders.forEach { reminder ->
                cancelAlarm(reminder.id)
                scheduleAlarm(reminder)
            }
            Log.d(TAG, "✅ Rescheduled ${reminders.size} reminders")
        }
    }

    fun cancelAlarm(reminderId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // Utility Methods
    fun calculateNextTriggerTime(currentTime: Long, pattern: RepeatPattern, customDays: List<Int> = emptyList()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime

        return when (pattern) {
            RepeatPattern.DAILY -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            RepeatPattern.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.timeInMillis
            }
            RepeatPattern.MONTHLY -> {
                calendar.add(Calendar.MONTH, 1)
                calendar.timeInMillis
            }
            RepeatPattern.YEARLY -> {
                calendar.add(Calendar.YEAR, 1)
                calendar.timeInMillis
            }
            RepeatPattern.WEEKDAYS -> {
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } while (calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.FRIDAY, Calendar.SATURDAY))
                calendar.timeInMillis
            }
            RepeatPattern.WEEKENDS -> {
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } while (calendar.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.FRIDAY, Calendar.SATURDAY))
                calendar.timeInMillis
            }
            RepeatPattern.CUSTOM -> {
                if (customDays.isNotEmpty()) {
                    do {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 1) % 7 // Convert to 0-6 where 0=Saturday
                    } while (dayOfWeek !in customDays)
                } else {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                calendar.timeInMillis
            }
            RepeatPattern.ONCE -> currentTime
        }
    }

    private fun parseCustomDays(daysStr: String): List<Int> {
        if (daysStr.isBlank()) return emptyList()
        return daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun parseCustomDaysToString(days: List<Int>): String = days.joinToString(",")

    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val completedReminders: Int,
        val todayReminders: Int
    )

    suspend fun getReminderStats(): ReminderStats {
        return ReminderStats(
            totalReminders = getTotalCount(),
            activeReminders = getActiveCount(),
            completedReminders = getCompletedCount(),
            todayReminders = getTodayCount()
        )
    }
}