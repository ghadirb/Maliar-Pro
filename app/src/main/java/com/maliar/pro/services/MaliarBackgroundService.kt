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
 * Keeps the app process alive in the background with a persistent, low-priority notification
 * so smart reminder alarms remain reliable on OEMs that aggressively kill background apps.
 */
class MaliarBackgroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.maliar.pro.action.START_BACKGROUND"
        const val ACTION_STOP = "com.maliar.pro.action.STOP_BACKGROUND"

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
            else -> startForeground(NOTIFICATION_ID, buildNotification())
        }
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

        return NotificationCompat.Builder(this, MaliarProApplication.BACKGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("مالیار پرو در حال اجراست")
            .setContentText("یادآوری‌های هوشمند در پس‌زمینه فعال هستند")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
