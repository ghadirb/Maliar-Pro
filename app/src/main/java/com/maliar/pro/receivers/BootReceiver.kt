package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.maliar.pro.database.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers every still-active reminder after boot, an app update, and after the user
 * grants exact alarm access. Replacing an installed APK can remove runtime alarm state;
 * Android 10 therefore needs the package-replaced path even though it does not need the
 * Android 12 exact-alarm permission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            Log.d("BootReceiver", "Rescheduling active reminders after ${intent.action}")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmartReminderManager(context.applicationContext).rescheduleAllActiveReminders()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule active reminders", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
