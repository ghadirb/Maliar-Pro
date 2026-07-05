package com.maliar.pro.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.maliar.pro.MainActivity
import com.maliar.pro.MaliarProApplication
import com.maliar.pro.R

/**
 * A minimal foreground service whose only job is to keep the app process alive in the
 * background with a persistent, low-priority ("silent") notification, so that:
 *  - smart reminder alarms are more reliable on OEMs that aggressively kill background apps
 *  - the assistant tab can keep processing/finishing a request even if the user switches away
 *
 * It intentionally does very little work itself; scheduling still goes through
 * AlarmManager (SmartReminderManager) so reminders still fire even if this service gets
 * killed by the OS - this service is a *best effort* reliability improvement, not the
 * only mechanism reminders depend on.
 */
class MaliarBackgroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.maliar.pro.action.START_BACKGROUND"
        const val ACTION_STOP = "com.maliar.pro.action.STOP_BACKGROUND"

        /** Starts (or is a no-op if already running) the background keep-alive service. */
        fun start(context: Context) {
            val intent = Intent(context, MaliarBackgroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MaliarBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        // If the OS kills the process to reclaim memory, restart the service once
        // resources are available again so background reliability keeps working.
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // "Run a real command from the notification" - the specific capability that was
        // missing: tapping this opens a tiny recorder that transcribes a spoken command
        // (e.g. "به علی زنگ بزن"), matches it to a saved contact, asks for confirmation,
        // and only then places a real call - all without opening the full app first.
        val voiceCommandIntent = Intent(this, com.maliar.pro.ui.assistant.VoiceCommandActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val voiceCommandPendingIntent = PendingIntent.getActivity(
            this,
            1,
            voiceCommandIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MaliarProApplication.BACKGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("مالیار پرو در حال اجراست")
            .setContentText("یادآوری‌های هوشمند و دستیار در پس‌زمینه فعال هستند")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_notification, "🎙️ دستور صوتی", voiceCommandPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
