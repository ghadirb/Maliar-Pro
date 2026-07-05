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
import com.maliar.pro.ui.reminders.FullScreenAlarmActivity
import com.maliar.pro.utils.PreferencesManager

/**
 * Fires when a reminder's alarm time arrives.
 *
 * There are really only two independent things going on here, and they used to be
 * tangled together in a way that made the notification-mode setting feel broken:
 *
 * 1. alertType decides *what kind of alarm this is* (a plain reminder, a full-screen
 *    alarm, or a "smart" spoken reminder) - this is chosen per-reminder and is NOT
 *    affected by the global notification-mode setting.
 * 2. notificationMode (none / simple / action, from Settings) decides *how the plain
 *    notification for a NOTIFICATION-type reminder looks* - whether it shows at all,
 *    and whether it has action buttons. It no longer gets silently overridden for any
 *    alertType, so switching the setting always has a visible, real effect.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val title = intent.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent.getStringExtra("reminder_description") ?: ""
        val alertType = intent.getStringExtra("alert_type") ?: AlertType.NOTIFICATION.name
        val contactName = intent.getStringExtra("contact_name") ?: ""
        val contactPhone = intent.getStringExtra("contact_phone") ?: ""

        when (alertType) {
            AlertType.SMART.name -> {
                // Speaks the reminder aloud immediately and repeats until dismissed -
                // independent of whatever the FullScreenAlarmActivity does visually.
                com.maliar.pro.receivers.SmartReminderTtsService.start(context, reminderId, title, description)
            }
            AlertType.FULL_SCREEN.name -> {
                showFullScreenIntentNotification(context, reminderId, title, description)
            }
            else -> {
                val prefs = PreferencesManager(context)
                val notificationMode = prefs.getNotificationMode()
                if (notificationMode == "none") {
                    // The whole point of this mode: no notification UI at all.
                    return
                }
                showPlainNotification(context, title, description, reminderId, notificationMode, contactName, contactPhone)
            }
        }
    }

    /**
     * A reliable full-screen alarm: uses NotificationCompat.setFullScreenIntent instead of
     * calling context.startActivity() directly from the receiver. Directly starting an
     * Activity from a background BroadcastReceiver is blocked on modern Android unless the
     * app is already in the foreground - which is exactly why this used to only work when
     * the person tapped a plain notification first. A full-screen-intent notification is
     * the sanctioned bypass real alarm-clock apps use.
     */
    private fun showFullScreenIntentNotification(
        context: Context,
        reminderId: Long,
        title: String,
        description: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", description)
            putExtra("alert_type", AlertType.FULL_SCREEN.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt(), alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, MaliarProApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(reminderId.toInt(), notification)
    }

    private fun showPlainNotification(
        context: Context,
        title: String,
        message: String,
        reminderId: Long,
        notificationMode: String,
        contactName: String,
        contactPhone: String
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

        // "با اکشن" (action) mode: real buttons, not just a tap-to-open notification.
        if (notificationMode == "action") {
            val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("action", "complete")
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context, reminderId.toInt() + 1000, completeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_check, "✅ انجام شد", completePendingIntent)

            val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("action", "snooze")
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context, reminderId.toInt() + 2000, snoozeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_snooze, "⏰ ۱۰ دقیقه بعد", snoozePendingIntent)

            // The whole point of "با اکشن" mode is running a real command from the
            // notification - if a contact was attached to this reminder, offer a direct
            // one-tap call button that dials that exact contact.
            if (contactPhone.isNotBlank()) {
                val callIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    putExtra("reminder_id", reminderId)
                    putExtra("action", "call")
                    putExtra("phone_number", contactPhone)
                }
                val callPendingIntent = PendingIntent.getBroadcast(
                    context, reminderId.toInt() + 3000, callIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val label = if (contactName.isNotBlank()) "📞 تماس با $contactName" else "📞 تماس"
                builder.addAction(R.drawable.ic_notification, label, callPendingIntent)
            }
        }
        // "ساده" (simple) mode: just the title/text above, tap to open - no buttons at all.

        notificationManager.notify(reminderId.toInt(), builder.build())
    }
}
