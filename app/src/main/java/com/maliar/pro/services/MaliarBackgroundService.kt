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
 * Optional foreground service for users whose phone vendor aggressively delays alarms.
 * It is never started automatically and has no network or data-collection behavior.
 */
class MaliarBackgroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_START = "com.maliar.pro.action.START_REMINDER_RELIABILITY"
        private const val ACTION_STOP = "com.maliar.pro.action.STOP_REMINDER_RELIABILITY"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MaliarBackgroundService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MaliarBackgroundService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        // Do not restart by itself; the user controls this option.
        return START_NOT_STICKY
    }

    private fun buildNotification() = NotificationCompat.Builder(this, MaliarProApplication.BACKGROUND_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("پایداری یادآوری فعال است")
        .setContentText("این حالت از بخش تنظیمات و با انتخاب شما فعال شده است")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    override fun onBind(intent: Intent?) = null
}
