package com.maliar.pro.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.maliar.pro.MaliarProApplication
import com.maliar.pro.R
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.ui.reminders.FullScreenAlarmActivity
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.runBlocking

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val title = intent.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent.getStringExtra("reminder_description") ?: ""
        val alertType = intent.getStringExtra("alert_type") ?: AlertType.NOTIFICATION.name
        val useAlarm = intent.getBooleanExtra("use_alarm", false)
        val priority = intent.getStringExtra("reminder_priority") ?: "MEDIUM"

        val prefs = PreferencesManager(context)
        val notificationMode = prefs.getNotificationMode()

        // If notification mode is "none", skip all notifications
        if (notificationMode == "none" && !useAlarm) {
            return
        }

        if (useAlarm || alertType == AlertType.FULL_SCREEN.name || alertType == AlertType.SMART.name) {
            // Open full screen alarm activity
            val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminder_id", reminderId)
                putExtra("reminder_title", title)
                putExtra("reminder_description", description)
                putExtra("reminder_priority", priority)
                putExtra("alert_type", alertType)
            }
            context.startActivity(alarmIntent)
        } else {
            // Show notification based on mode
            showNotification(context, title, description, reminderId, notificationMode)
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        message: String,
        reminderId: Long,
        notificationMode: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", message)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, MaliarProApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        // Add action buttons for "action" mode
        if (notificationMode == "action") {
            val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("action", "complete")
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt() + 1000,
                completeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_check, "✅ انجام شد", completePendingIntent)

            val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("action", "snooze")
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt() + 2000,
                snoozeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_snooze, "⏰ ۱۰ دقیقه بعد", snoozePendingIntent)
        }

        notificationManager.notify(reminderId.toInt(), builder.build())
    }
}