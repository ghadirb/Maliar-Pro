package com.maliar.pro.receivers

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.maliar.pro.MaliarProApplication
import com.maliar.pro.R
import com.maliar.pro.ui.reminders.FullScreenAlarmActivity
import com.maliar.pro.utils.AIHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Speaks a "smart" reminder aloud the *instant* its alarm fires - via a foreground
 * service, not the FullScreenAlarmActivity's onCreate() - so it works even when the
 * activity never actually gets shown on top. The spoken phrase repeats every
 * REPEAT_INTERVAL_MS until the person taps "انجام شد" (done) - on the notification
 * itself or inside the full-screen screen - up to a MAX_REPEATS safety cap so a
 * forgotten reminder doesn't loop forever.
 *
 * Uses foregroundServiceType="specialUse" (the same type MaliarBackgroundService
 * already uses successfully in this app) rather than "mediaPlayback", since the latter
 * has stricter, less predictable enforcement across OEMs/Android versions and isn't
 * worth the risk here. Every step is wrapped defensively and logged under tag
 * "SmartReminderTts" so a failure shows up in logcat instead of silently doing nothing.
 */
class SmartReminderTtsService : Service() {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0
    private var speakJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReminderId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra("reminder_id", -1) ?: -1
        val title = intent?.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent?.getStringExtra("reminder_description") ?: ""
        Log.d(TAG, "onStartCommand reminderId=$reminderId title=$title")

        if (reminderId <= 0) {
            Log.w(TAG, "Invalid reminderId, stopping")
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        try {
            // A different reminder fired while one was already speaking - restart clean
            // instead of talking over the previous phrase.
            if (currentReminderId != -1L && currentReminderId != reminderId) {
                stopSpeakingLoop()
            }
            currentReminderId = reminderId
            repeatCount = 0

            startForeground(NOTIFICATION_ID_BASE + reminderId.toInt(), buildNotification(reminderId, title))
            Log.d(TAG, "startForeground() succeeded for reminderId=$reminderId")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground() failed - stopping service", e)
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        speakJob?.cancel()
        speakJob = scope.launch {
            val fallback = if (description.isBlank()) "یادآوری: $title" else "یادآوری: $title. $description"
            val naturalText = try {
                // Capped at 4s so the voice starts quickly no matter how slow/unavailable
                // the network is - some OEMs are quick to kill a foreground service that
                // appears to sit doing nothing for too long right after it starts.
                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                    AIHelper.generateText(
                        this@SmartReminderTtsService,
                        systemPrompt = "یک جمله کوتاه، طبیعی و محاوره‌ای فارسی برای یادآوری صوتی بساز. فقط همان یک جمله را بنویس، بدون هیچ توضیح یا علامت اضافه.",
                        userPrompt = "عنوان یادآوری: $title" + if (description.isNotBlank()) "\nتوضیحات: $description" else ""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AIHelper.generateText failed, using fallback text", e)
                null
            }
            val textToSay = naturalText?.takeIf { it.isNotBlank() } ?: fallback
            Log.d(TAG, "Speaking text: $textToSay")
            initTtsAndSpeak(textToSay)
        }

        return START_STICKY
    }

    private fun initTtsAndSpeak(text: String) {
        try {
            tts = TextToSpeech(this) { status ->
                Log.d(TAG, "TextToSpeech init callback status=$status")
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("fa", "IR"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.US)
                    }
                    tts?.setSpeechRate(0.95f)
                    speakOnce(text)
                } else {
                    Log.e(TAG, "TextToSpeech init failed with status=$status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "initTtsAndSpeak threw", e)
        }
    }

    private fun speakOnce(text: String) {
        if (currentReminderId == -1L) return
        if (repeatCount >= MAX_REPEATS) {
            Log.d(TAG, "Reached MAX_REPEATS, stopping")
            stopSelfCompletely()
            return
        }
        repeatCount++
        Log.d(TAG, "speakOnce #$repeatCount")
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smart_reminder_$currentReminderId")
        } catch (e: Exception) {
            Log.e(TAG, "tts.speak() threw", e)
        }
        handler.postDelayed({
            if (currentReminderId != -1L) speakOnce(text)
        }, REPEAT_INTERVAL_MS)
    }

    private fun buildNotification(reminderId: Long, title: String): Notification {
        val fullScreenIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("alert_type", "SMART")
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, reminderId.toInt(), fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val doneIntent = Intent(this, ReminderActionReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("action", "complete")
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            this, reminderId.toInt() + 9000, doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MaliarProApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 $title")
            .setContentText("در حال پخش صوتی یادآوری… برای توقف «انجام شد» را بزنید")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.drawable.ic_check, "✅ انجام شد", donePendingIntent)
            .build()
    }

    private fun stopSpeakingLoop() {
        handler.removeCallbacksAndMessages(null)
        speakJob?.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping/shutting down TTS (safe to ignore)", e)
        }
        tts = null
        repeatCount = 0
    }

    private fun stopSelfCompletely() {
        Log.d(TAG, "stopSelfCompletely reminderId=$currentReminderId")
        stopSpeakingLoop()
        currentReminderId = -1
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground threw (safe to ignore)", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopSpeakingLoop()
        if (runningInstance === this) runningInstance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmartReminderTts"
        private const val NOTIFICATION_ID_BASE = 90000
        private const val REPEAT_INTERVAL_MS = 15000L
        private const val MAX_REPEATS = 8

        @Volatile
        private var runningInstance: SmartReminderTtsService? = null

        /** Starts (or restarts, for a new reminder) the speak-and-repeat loop. */
        fun start(context: Context, reminderId: Long, title: String, description: String) {
            Log.d(TAG, "start() called for reminderId=$reminderId title=$title")
            try {
                val intent = Intent(context, SmartReminderTtsService::class.java).apply {
                    putExtra("reminder_id", reminderId)
                    putExtra("reminder_title", title)
                    putExtra("reminder_description", description)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SmartReminderTtsService", e)
            }
        }

        /**
         * Stops the loop for [reminderId] (or unconditionally, if -1). Safe to call even
         * when nothing is currently speaking - it's just a no-op then.
         */
        fun stop(reminderId: Long = -1) {
            val instance = runningInstance ?: return
            if (reminderId == -1L || instance.currentReminderId == reminderId) {
                instance.stopSelfCompletely()
            }
        }
    }
}
