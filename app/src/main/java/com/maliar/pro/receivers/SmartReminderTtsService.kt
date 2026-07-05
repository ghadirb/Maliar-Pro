package com.maliar.pro.receivers

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * activity never actually gets shown on top (background-activity-start restrictions on
 * modern Android mean a plain notification tap was previously the only reliable way in).
 * The spoken phrase repeats every REPEAT_INTERVAL_MS until the person taps "انجام شد"
 * (done) - on the notification itself or inside the full-screen screen - up to a
 * MAX_REPEATS safety cap so a forgotten reminder doesn't loop forever.
 *
 * After each spoken phrase, it also briefly listens (voice recognition) for a natural
 * spoken response like "انجام شد", "خوردم" or "بیدار شدم" - so the person can dismiss the
 * reminder just by answering it out loud, without touching the phone at all.
 */
class SmartReminderTtsService : Service() {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0
    private var speakJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReminderId: Long = -1
    private var currentTitle: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra("reminder_id", -1) ?: -1
        val title = intent?.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent?.getStringExtra("reminder_description") ?: ""

        if (reminderId <= 0) {
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        // A different reminder fired while one was already speaking - restart clean
        // instead of talking over the previous phrase.
        if (currentReminderId != -1L && currentReminderId != reminderId) {
            stopSpeakingLoop()
        }
        currentReminderId = reminderId
        currentTitle = title
        repeatCount = 0

        startForeground(NOTIFICATION_ID_BASE + reminderId.toInt(), buildNotification(reminderId, title))

        speakJob?.cancel()
        speakJob = scope.launch {
            val fallback = if (description.isBlank()) "یادآوری: $title" else "یادآوری: $title. $description"
            val naturalText = try {
                AIHelper.generateText(
                    this@SmartReminderTtsService,
                    systemPrompt = "یک جمله کوتاه، طبیعی و محاوره‌ای فارسی برای یادآوری صوتی بساز. فقط همان یک جمله را بنویس، بدون هیچ توضیح یا علامت اضافه.",
                    userPrompt = "عنوان یادآوری: $title" + if (description.isNotBlank()) "\nتوضیحات: $description" else ""
                )
            } catch (e: Exception) {
                null
            }
            val textToSay = naturalText?.takeIf { it.isNotBlank() } ?: fallback
            initTtsAndSpeak(textToSay)
        }

        return START_STICKY
    }

    private fun initTtsAndSpeak(text: String) {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(0.95f)
                // CRITICAL: without this, TTS plays on the default (media/notification)
                // stream, which is silent on most phones whenever ringer volume is turned
                // down or the device is on vibrate/silent - exactly why the notification
                // and its "beep" (from the voice-response listener starting up) were
                // audible but the actual spoken sentence never was. USAGE_ALARM plays on
                // the alarm volume, the same one real alarm clocks use, which most people
                // keep up and which bypasses silent/vibrate mode.
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attributes)

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.requestAudioFocus(
                    null,
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                // If the person has the alarm stream muted/very low, force it up enough to
                // actually be heard - a reminder that plays at inaudible volume is
                // functionally the same as one that never played at all.
                val maxAlarmVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                val currentAlarmVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                if (currentAlarmVolume < maxAlarmVolume / 2) {
                    try {
                        audioManager.setStreamVolume(
                            android.media.AudioManager.STREAM_ALARM,
                            maxAlarmVolume * 2 / 3,
                            0
                        )
                    } catch (e: Exception) { /* some OEMs restrict this; best effort only */ }
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // TTS callbacks fire on a non-main thread; SpeechRecognizer needs
                        // the main looper.
                        handler.post {
                            if (currentReminderId != -1L) listenForVoiceResponse(text)
                        }
                    }
                })
                speakOnce(text)
            } else {
                // No TTS engine/voice available on this device - the notification (with
                // its "انجام شد" button) is still showing, so the reminder isn't silently
                // lost, it just can't be spoken aloud.
                stopSelfIfMaxRepeats()
            }
        }
    }

    private fun speakOnce(text: String) {
        if (currentReminderId == -1L) return
        if (repeatCount >= MAX_REPEATS) {
            stopSelfCompletely()
            return
        }
        repeatCount++
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "smart_reminder_$currentReminderId")
    }

    /**
     * Listens briefly for a natural spoken response ("انجام شد", "خوردم", "بیدار شدم", ...).
     * If nothing recognizable comes back within the timeout, falls back to the normal
     * repeat-after-a-pause behavior so the reminder still repeats reliably even on
     * devices/situations where voice recognition isn't available (no mic access, no
     * internet for on-device recognizer, etc.).
     */
    private fun listenForVoiceResponse(spokenText: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            scheduleNextRepeat(spokenText)
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        scheduleNextRepeat(spokenText)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null && matches.any { isDoneResponse(it) }) {
                            handleVoiceConfirmed()
                        } else {
                            scheduleNextRepeat(spokenText)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            }
            speechRecognizer?.startListening(recognizerIntent)
            // Safety timeout in case the recognizer never calls back at all on some OEMs.
            handler.postDelayed({
                if (currentReminderId != -1L) scheduleNextRepeat(spokenText)
            }, LISTEN_TIMEOUT_MS)
        } catch (e: Exception) {
            scheduleNextRepeat(spokenText)
        }
    }

    private fun isDoneResponse(text: String): Boolean {
        val normalized = text.trim()
        return DONE_PHRASES.any { normalized.contains(it) }
    }

    private fun handleVoiceConfirmed() {
        handler.removeCallbacksAndMessages(null)
        val reminderId = currentReminderId
        if (reminderId > 0) {
            // Route through the same broadcast the "انجام شد" notification button uses,
            // so completion logic stays in exactly one place.
            val intent = Intent(this, ReminderActionReceiver::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("action", "complete")
            }
            sendBroadcast(intent)
        }
        stopSelfCompletely()
    }

    private fun scheduleNextRepeat(previousText: String) {
        if (repeatCount >= MAX_REPEATS) {
            stopSelfCompletely()
            return
        }
        handler.postDelayed({
            if (currentReminderId != -1L) speakOnce(previousText)
        }, REPEAT_INTERVAL_MS)
    }

    private fun stopSelfIfMaxRepeats() {
        if (repeatCount >= MAX_REPEATS) stopSelfCompletely()
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
            .setContentText("در حال پخش صوتی یادآوری… بگویید «انجام شد» یا دکمه را بزنید")
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
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // best-effort cleanup only
        }
        speechRecognizer = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // best-effort cleanup only
        }
        tts = null
        repeatCount = 0
    }

    private fun stopSelfCompletely() {
        stopSpeakingLoop()
        currentReminderId = -1
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSpeakingLoop()
        if (runningInstance === this) runningInstance = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 90000
        private const val REPEAT_INTERVAL_MS = 15000L
        private const val LISTEN_TIMEOUT_MS = 6000L
        private const val MAX_REPEATS = 8

        /** Natural Persian phrases that mean "I got it, stop reminding me". */
        private val DONE_PHRASES = listOf(
            "انجام شد", "انجام دادم", "انجامش دادم", "خوردم", "خوردمش",
            "بیدار شدم", "بیدارم", "باشه", "قبول", "تمام", "شد دیگه",
            "متوجه شدم", "فهمیدم", "اوکی", "او کی"
        )

        @Volatile
        private var runningInstance: SmartReminderTtsService? = null

        /** Starts (or restarts, for a new reminder) the speak-and-repeat loop. */
        fun start(context: Context, reminderId: Long, title: String, description: String) {
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
