package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.services.MaliarBackgroundService

/**
 * Without this receiver, all AlarmManager alarms scheduled via SmartReminderManager
 * are wiped by the OS on every device reboot, and reminders silently stop firing
 * until the app is manually reopened. Re-registers every still-active reminder and
 * restarts the background keep-alive service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Device rebooted, rescheduling active reminders")
            try {
                SmartReminderManager(context.applicationContext).rescheduleAllActiveReminders()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule reminders after boot", e)
            }
            try {
                MaliarBackgroundService.start(context.applicationContext)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Could not restart background service after boot: ${e.message}")
            }
        }
    }
}
