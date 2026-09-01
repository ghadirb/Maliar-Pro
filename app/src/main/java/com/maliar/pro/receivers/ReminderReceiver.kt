package com.maliar.pro.receivers

import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maliar.pro.MaliarProApplication
import com.maliar.pro.R
import com.maliar.pro.database.AlertType
import com.maliar.pro.ui.reminders.FullScreenAlarmActivity
import com.maliar.pro.utils.PreferencesManager
import com.maliar.pro.utils.ReminderSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
 *    notification for a NOTIFICATION-type reminder looks*: "بدون نوتیفیکیشن" suppresses
 *    it completely, "ساده" shows a plain banner with no buttons, "با اکشن" adds the
 *    انجام‌شد/تعویق/تماس buttons. Each of the three now genuinely looks/behaves
 *    differently - they don't just collapse into "shows vs. doesn't show".
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val title = intent.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent.getStringExtra("reminder_description") ?: ""
        val alertType = intent.getStringExtra("alert_type") ?: AlertType.NOTIFICATION.name
        val priority = intent.getStringExtra("reminder_priority") ?: ""
        val contactName = intent.getStringExtra("contact_name") ?: ""
        val contactPhone = intent.getStringExtra("contact_phone") ?: ""
        val soundValue = intent.getStringExtra("sound_uri") ?: ReminderSound.DEFAULT_ALARM

        // A recurring reminder (DAILY/WEEKLY/CUSTOM/...) must be advanced to its next
        // occurrence and rescheduled the moment it actually fires - not only when the
        // person happens to tap a notification action - otherwise it silently fires once
        // and never again. This has to survive the process potentially being frozen
        // right after onReceive() returns, hence goAsync().
        if (reminderId >= 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.maliar.pro.database.SmartReminderManager(context.applicationContext).onFired(reminderId)
                } catch (e: Exception) {
                    android.util.Log.e("ReminderReceiver", "Error rescheduling recurring reminder", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }

        val prefs = PreferencesManager(context)

        // Quiet hours: only downgrades non-urgent reminders (HIGH priority always alerts
        // normally - e.g. a check due today shouldn't stay silent just because it's late).
        // See PreferencesManager.isWithinQuietHoursNow for why this is a plain local time
        // check rather than anything touching Android's Do Not Disturb / notification-
        // listener APIs.
        if (prefs.isWithinQuietHoursNow() && !priority.contains("HIGH", ignoreCase = true)) {
            showQuietNotification(context, title, description, reminderId)
            return
        }

        when (alertType) {
            // Previously FULL_SCREEN silently fell through to the exact same plain
            // actionable notification as SMART, so choosing "تمام صفحه" when creating a
            // reminder never actually produced a true full-screen wake-the-lock-screen
            // alarm - showFullScreenIntentNotification() existed but nothing ever called
            // it. SMART no longer has a distinct spoken-TTS behavior to fall back to
            // (that service was removed for Play Protect safety), so it still uses the
            // same actionable notification as before.
            AlertType.FULL_SCREEN.name ->
                showFullScreenIntentNotification(context, reminderId, title, description, soundValue)
            AlertType.SMART.name ->
                showPlainNotification(context, title, description, reminderId, "action", contactName, contactPhone, soundValue)
            else -> {
                val notificationMode = prefs.getNotificationMode()
                if (notificationMode == "none") {
                    // The whole point of this mode: no notification UI at all.
                    return
                }
                showPlainNotification(context, title, description, reminderId, notificationMode, contactName, contactPhone, soundValue)
            }
        }
    }

    /** Quiet-hours fallback: a minimal, genuinely silent notification (own low-importance
     *  channel, no sound/vibration, no full-screen takeover) - the person still sees it
     *  when they check their phone, without being woken up. */
    private fun showQuietNotification(context: Context, title: String, message: String, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reminder_quiet_hours"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager.getNotificationChannel(channelId) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "یادآوری‌ها (ساعات سکوت)", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "یادآوری‌هایی که در بازه سکوت شبانه بی‌صدا نمایش داده می‌شوند"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", message)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        notificationManager.notify(reminderId.toInt(), notification)
    }

    /**
     * A reliable full-screen alarm has to handle two very different situations:
     *
     * - App already open (an Activity is visible): a BroadcastReceiver CAN start an
     *   Activity directly here with no restriction at all, since the process already has
     *   a visible window - going through a notification first (and waiting for a tap)
     *   was pure unnecessary delay in this case, not a platform requirement.
     * - App in the background: directly calling startActivity() here would be silently
     *   blocked by Android's background-activity-launch restriction, so
     *   NotificationCompat.setFullScreenIntent is used instead - the sanctioned bypass
     *   real alarm-clock apps rely on. Android itself auto-promotes this straight to the
     *   full-screen Activity *when the device is locked*; if the phone happens to be
     *   unlocked but the app isn't open, Android intentionally only shows a heads-up
     *   notification instead of taking over the screen - this is a deliberate platform
     *   restriction (since Android 10) that no regular third-party app can bypass, to
     *   stop apps from hijacking the screen while someone is actively using their phone.
     */
    private fun showFullScreenIntentNotification(
        context: Context,
        reminderId: Long,
        title: String,
        description: String,
        soundValue: String
    ) {
        val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", description)
            putExtra("alert_type", AlertType.FULL_SCREEN.name)
            putExtra("sound_uri", soundValue)
        }

        if (MaliarProApplication.isAppInForeground()) {
            context.startActivity(alarmIntent)
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt(), alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelIdFor(context, reminderId, soundValue))
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
        contactPhone: String,
        soundValue: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", message)
            putExtra("sound_uri", soundValue)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Swiping the notification away should still mark it handled instead of leaving
        // the reminder's alarm state dangling with nothing telling the app it was seen.
        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("action", "dismiss")
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt() + 4000, dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = ReminderSound.toUri(context, soundValue)
        val builder = NotificationCompat.Builder(context, channelIdFor(context, reminderId, soundValue))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setSound(soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        if (notificationMode == "action") {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)

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

            // If a contact was attached to this reminder, offer a direct one-tap call
            // button that dials that exact contact.
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
        } else {
            // "ساده" (simple): just the title/text above, tap to open - deliberately no
            // buttons at all, and a plain priority so it doesn't behave like an alarm.
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        notificationManager.notify(reminderId.toInt(), builder.build())
    }

    /** Android 8+ locks a channel's sound the moment it's created and never lets it change
     *  afterwards - not even the OS itself can update an existing channel's sound. Folding
     *  [soundValue] into the channel ID means picking a different sound for this reminder
     *  (e.g. editing it later) naturally produces a fresh channel instead of silently
     *  reusing the old, now-stale one with yesterday's sound still locked in. */
    private fun channelIdFor(context: Context, reminderId: Long, soundValue: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return MaliarProApplication.REMINDER_CHANNEL_ID
        val channelId = "reminder_${reminderId}_${soundValue.hashCode()}"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) == null) {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            manager.createNotificationChannel(
                NotificationChannel(channelId, "یادآوری: $reminderId", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "صدای انتخاب شده برای این یادآوری"
                    setSound(ReminderSound.toUri(context, soundValue), attributes)
                    enableVibration(true)
                }
            )
            // Best-effort cleanup: remove this reminder's previous sound-specific channel(s)
            // now that a fresh one exists, so Settings > App notifications doesn't slowly
            // accumulate one stale channel per sound the person has ever tried for it.
            try {
                val prefix = "reminder_${reminderId}_"
                manager.notificationChannels
                    .filter { it.id.startsWith(prefix) && it.id != channelId }
                    .forEach { manager.deleteNotificationChannel(it.id) }
            } catch (e: Exception) {
                // Non-fatal: worst case a harmless old channel lingers.
            }
        }
        return channelId
    }
}
