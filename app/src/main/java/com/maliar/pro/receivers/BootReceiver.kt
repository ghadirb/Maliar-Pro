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
            // MaliarBackgroundService (the opt-in "پایداری یادآوری" foreground service - see
            // its own class doc) is never auto-started; it previously only ever came back
            // to life via the Settings switch, even after a reboot wiped it out entirely.
            // A device reboot is exactly one more way it can go missing without the person
            // noticing, so restore it here too when they'd already turned it on. Starting a
            // foreground service from a BOOT_COMPLETED receiver is one of the exemptions
            // Android still allows post-O, so this is safe on every supported API level.
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
                if (com.maliar.pro.utils.PreferencesManager(context.applicationContext).isBackgroundServiceEnabled()) {
                    com.maliar.pro.services.MaliarBackgroundService.start(context.applicationContext)
                }
            }
        }
    }
}
