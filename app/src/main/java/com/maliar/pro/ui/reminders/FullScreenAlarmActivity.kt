package com.maliar.pro.ui.reminders

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.ReminderSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var reminderId: Long = -1
    private var isSmartAlarm = false
    private var smartSpeechRepeats = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make activity full screen and turn on screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        setContentView(R.layout.activity_full_screen_alarm)

        reminderId = intent.getLongExtra("reminder_id", -1)
        val title = intent.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent.getStringExtra("reminder_description") ?: ""
        val alertType = intent.getStringExtra("alert_type") ?: AlertType.NOTIFICATION.name
        
        isSmartAlarm = alertType == AlertType.SMART.name

        findViewById<TextView>(R.id.alarmTitle).text = title
        findViewById<TextView>(R.id.alarmDescription).text = description

        // Wake up device
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "MaliarPro:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max

        // Play sound
        if (isSmartAlarm) {
            findViewById<TextView>(R.id.alarmTypeHint).text = "🔊 یادآوری هوشمند"
            findViewById<TextView>(R.id.alarmTypeHint).visibility = View.VISIBLE
            val precomputedAudioPath = intent.getStringExtra("tts_audio_path")
            val alreadySpoken = intent.getBooleanExtra("already_spoken", false)
            val precomputedAudio = precomputedAudioPath?.let { java.io.File(it) }?.takeIf { it.exists() }
            when {
                // ReminderReceiver already played this exact sentence as the notification's
                // own sound (see its ttsContentUri/smartAudioChannelId) before the person
                // tapped through to this screen - speaking it again here would just repeat
                // the same sentence a second time, so go straight to the normal alarm tone.
                alreadySpoken -> playAlarmSound(intent.getStringExtra("sound_uri"))
                // Device was locked, so Android auto-promoted straight to this screen
                // without ReminderReceiver needing to precompute anything - but it may
                // still have generated audio in time (e.g. app-foreground direct launch
                // racing a fast network reply); reuse it instead of a redundant AI call.
                precomputedAudio != null -> playGeneratedSpeech(precomputedAudio, intent.getStringExtra("sound_uri"))
                else -> speakSmartReminder(title, description, intent.getStringExtra("sound_uri"))
            }
        } else {
            playAlarmSound(intent.getStringExtra("sound_uri"))
        }

        // Vibrate
        vibrate()

        findViewById<View>(R.id.completeButton).setOnClickListener {
            completeReminder()
        }
        findViewById<View>(R.id.snoozeOptionsButton).setOnClickListener {
            showSnoozeChoices()
        }

        setupSlideGesture()
    }

    /**
     * A "smart" reminder speaks its title/description out loud instead of (or before) the
     * normal alarm tone, using GapGPT's cloud TTS - the device's own local TextToSpeech
     * engine isn't reliable here since most phones don't have a Persian voice pack
     * installed, which used to be the actual reason spoken reminders stayed silent even
     * though everything else (permissions, audio routing, volume) was correct.
     *
     * This used to be driven by a separate always-on background Service
     * (SmartReminderTtsService) that generated and played the voice independently of
     * whether this screen ever actually appeared. That service was removed for running a
     * risky, hard-to-justify background pattern - but nothing was put in its place, so
     * SMART reminders silently stopped speaking at all. Doing the same work here instead,
     * scoped to this foreground Activity's own lifecycle (lifecycleScope automatically
     * cancels this if the alarm is dismissed/snoozed/completed before it finishes), avoids
     * that problem entirely: there's no separate background component, nothing keeps
     * running once this screen is gone, and it only ever does anything while a real
     * full-screen alarm is legitimately on screen.
     *
     * Never leaves the person with total silence: if there's no active GapGPT key, the
     * network call fails, or playback of the generated audio fails for any reason, this
     * falls straight back to the normal looping alarm tone. On success, the alarm tone
     * still starts automatically right after the spoken sentence finishes, so the alarm
     * keeps going as backup in case the person didn't notice the one-time announcement.
     */
    private fun speakSmartReminder(title: String, description: String, soundUri: String?) {
        lifecycleScope.launch {
            // Shared with ReminderReceiver's precompute path (see AIHelper.
            // synthesizeReminderSpeech) so the exact same phrasing logic is used whichever
            // path ends up generating the audio.
            val audioFile = AIHelper.synthesizeReminderSpeech(this@FullScreenAlarmActivity, title, description)
            if (isFinishing || isDestroyed) return@launch
            if (audioFile != null) {
                playGeneratedSpeech(audioFile, soundUri)
            } else {
                playAlarmSound(soundUri)
            }
        }
    }

    /** Plays the generated speech once, then hands off to the normal looping alarm tone
     *  as soon as it finishes - so the alarm keeps demanding attention afterward exactly
     *  like a non-smart reminder would. */
    private fun playGeneratedSpeech(audioFile: java.io.File, soundUri: String?) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                isLooping = false
                setOnCompletionListener { finished ->
                    finished.release()
                    if (mediaPlayer === finished) mediaPlayer = null
                    if (!isFinishing && !isDestroyed && smartSpeechRepeats < 2) {
                        smartSpeechRepeats += 1
                        playGeneratedSpeech(audioFile, soundUri)
                    } else if (!isFinishing && !isDestroyed) {
                        playAlarmSound(soundUri)
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlarm", "Error playing generated speech", e)
            playAlarmSound(soundUri)
        }
    }

    private fun playAlarmSound(soundValue: String?) {
        try {
            val alarmUri = ReminderSound.toUri(this, soundValue)
                ?: throw IllegalStateException("No alarm sound is available")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@FullScreenAlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@FullScreenAlarmActivity, notificationUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                android.util.Log.e("FullScreenAlarm", "Error playing sound", e2)
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 500, 500, 1000, 500),
                    intArrayOf(0, 255, 0, 255, 255, 0),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 500, 500), 0)
        }
    }

    /**
     * A phone-call-style slide gesture instead of separate تعویق/توقف buttons: drag the
     * handle to the physical right edge of the screen to stop the alarm, or to the left
     * edge to snooze it. Deliberately uses raw screen-left/right (not RTL start/end) since
     * a drag gesture has to match where the user's finger actually is on screen, regardless
     * of the app's Persian RTL layout direction.
     */
    private fun setupSlideGesture() {
        val track = findViewById<FrameLayout>(R.id.slideTrack)
        val handle = findViewById<MaterialCardView>(R.id.slideHandle)

        var maxDrag = 0f
        var downRawX = 0f
        var startTranslation = 0f

        track.post {
            maxDrag = (track.width - handle.width) / 2f - dp(12f)
        }

        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    startTranslation = view.translationX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - downRawX
                    val target = (startTranslation + delta).coerceIn(-maxDrag, maxDrag)
                    view.translationX = target
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val threshold = maxDrag * 0.62f
                    when {
                        maxDrag <= 0f -> view.animate().translationX(0f).setDuration(150).start()
                        view.translationX >= threshold -> finishSlide(view, maxDrag) { dismissAlarm() }
                        view.translationX <= -threshold -> finishSlide(view, -maxDrag) { showSnoozeChoices() }
                        else -> view.animate()
                            .translationX(0f)
                            .setInterpolator(OvershootInterpolator())
                            .setDuration(220)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Slides the handle the rest of the way to the edge, then runs [onArrived]. */
    private fun finishSlide(view: View, target: Float, onArrived: () -> Unit) {
        view.animate()
            .translationX(target)
            .setDuration(120)
            .withEndAction { onArrived() }
            .start()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun dismissAlarm() {
        stopAlarm()
        CoroutineScope(Dispatchers.IO).launch {
            if (reminderId > 0) {
                SmartReminderManager(this@FullScreenAlarmActivity).markAsCompleted(reminderId)
            }
        }
        finish()
    }

    private fun showSnoozeChoices() {
        val options = arrayOf("۵ دقیقه", "۱۰ دقیقه", "۳۰ دقیقه", "۱ ساعت")
        val minutes = intArrayOf(5, 10, 30, 60)
        AlertDialog.Builder(this)
            .setTitle("تعویق یادآوری")
            .setItems(options) { _, which -> snoozeAlarm(minutes[which]) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun snoozeAlarm(minutes: Int) {
        stopAlarm()
        CoroutineScope(Dispatchers.IO).launch {
            if (reminderId > 0) {
                SmartReminderManager(this@FullScreenAlarmActivity).snoozeReminder(reminderId, minutes)
            }
        }
        finish()
    }

    private fun completeReminder() {
        stopAlarm()
        CoroutineScope(Dispatchers.IO).launch {
            if (reminderId > 0) {
                SmartReminderManager(this@FullScreenAlarmActivity).completeReminder(reminderId)
            }
        }
        finish()
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlarm", "Error stopping alarm", e)
        }

        try {
            wakeLock?.apply {
                if (isHeld) release()
            }
            wakeLock = null
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlarm", "Error releasing wake lock", e)
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing the alarm
        // User must press dismiss/snooze/complete
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    companion object {
        fun createIntent(context: Context, title: String, description: String): Intent {
            return Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminder_title", title)
                putExtra("reminder_description", description)
            }
        }
    }
}
