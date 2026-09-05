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

    /** Repairs stale recurring rows after midnight, reboot, or a missed alarm. */
    suspend fun reconcileRecurringReminders(): List<ReminderEntity> {
        val now = System.currentTimeMillis()
        val active = dao.getActiveRemindersList()
        active.filter { it.repeatPattern != RepeatPattern.ONCE.name && it.triggerTime <= now }
            .forEach { stale ->
                val fixed = ensureFutureTriggerTime(stale)
                cancelAlarm(fixed.id)
                scheduleAlarm(fixed)
            }
        return dao.getActiveRemindersList()
    }
    
    suspend fun getReminderById(id: Long): ReminderEntity? = dao.getReminderById(id)

    suspend fun addReminder(reminder: ReminderEntity): Long {
        val id = dao.insertReminder(reminder)
        val saved = ensureFutureTriggerTime(reminder.copy(id = id))
        if (saved.triggerTime > 0) {
            scheduleAlarm(saved)
        }
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
        return id
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        val saved = ensureFutureTriggerTime(reminder)
        dao.updateReminder(saved)
        cancelAlarm(saved.id)
        // No manual notification-channel cleanup needed here: ReminderReceiver.channelIdFor()
        // now folds the sound value into the channel ID itself, so a changed sound
        // automatically gets its own fresh channel (and prunes this reminder's old
        // channel(s)) the next time it actually fires - see that function for why.
        if (saved.triggerTime > 0) {
            scheduleAlarm(saved)
        }
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        dao.deleteReminder(reminder)
        cancelAlarm(reminder.id)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
    }

    suspend fun markAsCompleted(id: Long) {
        dao.markAsCompleted(id)
        cancelAlarm(id)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
    }

    suspend fun completeReminder(id: Long) {
        val reminder = dao.getReminderById(id) ?: return
        if (reminder.repeatPattern == RepeatPattern.ONCE.name) {
            dao.markAsCompleted(id)
            cancelAlarm(id)
            com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
        }
        // Recurring reminders no longer need advancing here: ReminderReceiver.onFired()
        // already advances + reschedules the very moment the alarm goes off (regardless
        // of whether the person ever taps a notification action), so doing it again here
        // would double-advance and skip an occurrence. See onFired() for details.
    }

    /**
     * Called by [com.maliar.pro.receivers.ReminderReceiver] every time a reminder's alarm
     * actually fires - this is what makes recurring reminders (DAILY/WEEKLY/CUSTOM/...)
     * keep firing on schedule at all. Previously nothing advanced a recurring reminder's
     * triggerTime unless the person explicitly tapped "انجام شد" on the notification,
     * so a DAILY reminder that was simply ignored (swiped away, or the notification just
     * dismissed itself) would only ever fire once, on its very first triggerTime, and
     * never again.
     *
     * ONCE reminders are left untouched here - they still only complete/cancel via an
     * explicit person action (or stay in the active list forever if ignored), matching
     * existing behavior.
     */
    suspend fun onFired(id: Long) {
        val reminder = dao.getReminderById(id) ?: return
        if (reminder.repeatPattern == RepeatPattern.ONCE.name) return

        val nextTime = calculateNextTriggerTime(
            reminder.triggerTime,
            RepeatPattern.valueOf(reminder.repeatPattern),
            parseCustomDays(reminder.customRepeatDays), reminder.repeatIntervalDays, reminder.repeatIntervalMinutes
        )
        val updated = reminder.copy(triggerTime = nextTime, isCompleted = false)
        dao.updateReminder(updated)
        scheduleAlarm(updated)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
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
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context.applicationContext)
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

    /**
     * A repeating reminder (DAILY/WEEKLY/...) whose triggerTime has already passed - e.g.
     * it's created after today's time-of-day already went by, or its alarm never actually
     * fired (device off, OS killed the app in the background, etc.) - has to be advanced
     * to its next real future occurrence *and that has to be written back to the database*,
     * not just used locally when arming AlarmManager. Previously this catch-up only
     * happened inside scheduleAlarm() as a local variable that was handed to AlarmManager
     * and then thrown away: the actual AlarmManager alarm ended up correctly scheduled for
     * tomorrow, but the database (and therefore the reminders list, which reads
     * triggerTime straight from the database) kept showing the original stale/past time
     * forever - a DAILY reminder would sit permanently under "سررسید گذشته" and never
     * appear under "امروز"/"فردا" again, even though it was still silently ringing on
     * schedule underneath. Called before every scheduleAlarm() so the two can never drift
     * apart.
     */
    private suspend fun ensureFutureTriggerTime(reminder: ReminderEntity): ReminderEntity {
        if (reminder.repeatPattern == RepeatPattern.ONCE.name) return reminder
        if (reminder.triggerTime >= System.currentTimeMillis()) return reminder

        val corrected = reminder.copy(
            triggerTime = calculateNextTriggerTime(
                reminder.triggerTime,
                RepeatPattern.valueOf(reminder.repeatPattern),
                parseCustomDays(reminder.customRepeatDays), reminder.repeatIntervalDays, reminder.repeatIntervalMinutes
            )
        )
        dao.updateReminder(corrected)
        return corrected
    }

    private fun scheduleAlarm(reminder: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms - permission needed")
                return
            }
        }

        // FULL_SCREEN and SMART reminders both need to fire at the exact instant they're
        // due (an alarm-clock style alarm survives Doze better than a plain exact alarm),
        // regardless of priority - ReminderReceiver is what actually decides what each
        // alertType looks like/does when it fires, not this scheduling choice.
        // A normal exact alarm plus a high-priority notification is reliable enough for
        // reminders and avoids full-screen intent behavior reserved for alarm/call apps.
        val useAlarm = false

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_description", reminder.description)
            putExtra("reminder_priority", reminder.priority)
            putExtra("alert_type", reminder.alertType)
            putExtra("use_alarm", useAlarm)
            putExtra("repeat_pattern", reminder.repeatPattern)
            putExtra("reminder_type", reminder.reminderType)
            putExtra("contact_name", reminder.contactName)
            putExtra("contact_phone", reminder.contactPhoneNumber)
            putExtra("sound_uri", reminder.soundUri)
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

            // Repeating reminders arrive here already advanced to a future time by
            // ensureFutureTriggerTime() (called from every caller of this function), so
            // the only case left to defend against is a ONCE reminder whose time somehow
            // ended up in the past (e.g. device clock changed) - fire it almost
            // immediately rather than silently never arming an alarm for it.
            if (triggerTime < now) {
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
                    putExtra("sound_uri", reminder.soundUri)
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
                scheduleAlarm(ensureFutureTriggerTime(reminder))
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
    /**
     * Returns the next occurrence of a recurring reminder that is strictly after "now".
     * The previous version only advanced by a single cycle (e.g. +1 day), so a DAILY
     * reminder that hadn't fired in 3 days (app closed, phone off, etc.) would still land
     * in the past and re-fire immediately/incorrectly. This now keeps stepping forward
     * until the result is actually in the future.
     */
    fun calculateNextTriggerTime(currentTime: Long, pattern: RepeatPattern, customDays: List<Int> = emptyList(), repeatIntervalDays: Int = 0, repeatIntervalMinutes: Int = 0): Long {
        if (pattern == RepeatPattern.ONCE) return currentTime
        var next = advanceOnce(currentTime, pattern, customDays, repeatIntervalDays, repeatIntervalMinutes)
        var guard = 0
        while (next <= System.currentTimeMillis() && guard < 1000) {
            next = advanceOnce(next, pattern, customDays, repeatIntervalDays, repeatIntervalMinutes)
            guard += 1
        }
        return next
    }

    private fun advanceOnce(currentTime: Long, pattern: RepeatPattern, customDays: List<Int>, repeatIntervalDays: Int, repeatIntervalMinutes: Int): Long {
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
                } while (((calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7) !in listOf(6, 0, 1, 2, 3))
                calendar.timeInMillis
            }
            RepeatPattern.WEEKENDS -> {
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } while (((calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7) !in listOf(4, 5))
                calendar.timeInMillis
            }
            RepeatPattern.CUSTOM -> {
                if (customDays.isNotEmpty()) {
                    do {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7 // 0=Sunday ... 6=Saturday
                    } while (dayOfWeek !in customDays)
                } else {
                    calendar.add(Calendar.DAY_OF_MONTH, repeatIntervalDays.coerceAtLeast(1))
                }
                calendar.timeInMillis
            }
            RepeatPattern.CUSTOM_INTERVAL -> {
                calendar.add(Calendar.MINUTE, repeatIntervalMinutes.coerceAtLeast(1))
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
