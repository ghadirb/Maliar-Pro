package com.maliar.pro.ui.reminders

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.maliar.pro.MaliarProApplication
import com.maliar.pro.R
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.Priority
import com.maliar.pro.database.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var reminderId: Long = -1
    private var isSmartAlarm = false

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
        playAlarmSound()

        // Vibrate
        vibrate()

        findViewById<View>(R.id.dismissButton).setOnClickListener {
            dismissAlarm()
        }

        findViewById<View>(R.id.snoozeButton).setOnClickListener {
            snoozeAlarm()
        }

        findViewById<View>(R.id.completeButton).setOnClickListener {
            completeReminder()
        }

        // For smart alarm, auto-dismiss after some time
        if (isSmartAlarm) {
            findViewById<TextView>(R.id.alarmTypeHint).text = "یادآوری هوشمند"
            findViewById<TextView>(R.id.alarmTypeHint).visibility = View.VISIBLE
        }
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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

    private fun dismissAlarm() {
        stopAlarm()
        CoroutineScope(Dispatchers.IO).launch {
            if (reminderId > 0) {
                SmartReminderManager(this@FullScreenAlarmActivity).markAsCompleted(reminderId)
            }
        }
        finish()
    }

    private fun snoozeAlarm() {
        stopAlarm()
        CoroutineScope(Dispatchers.IO).launch {
            if (reminderId > 0) {
                SmartReminderManager(this@FullScreenAlarmActivity).snoozeReminder(reminderId, 10)
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
        fun createIntent(context: Context, title: String, description: String): android.content.Intent {
            return android.content.Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminder_title", title)
                putExtra("reminder_description", description)
            }
        }
    }
}