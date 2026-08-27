package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val action = intent.getStringExtra("action") ?: return

        if (reminderId < 0) return

        // "call" runs immediately on the receiving thread (no DB access needed) so the
        // dialer/call intent fires the moment the notification button is tapped.
        if (action == "call") {
            val phoneNumber = intent.getStringExtra("phone_number").orEmpty()
            if (phoneNumber.isNotBlank()) {
                VoiceCallHelper.makeCallWithResult(context, phoneNumber)
            }
            return
        }

        // Any real action taken on a reminder (done/snooze/dismiss) must actually remove
        // the notification banner from the status bar - setAutoCancel(true) only clears a
        // notification when its *main body* is tapped, never when an action button is
        // tapped, so without this explicit cancel() the notification used to just sit
        // there forever no matter which button was pressed.
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(reminderId.toInt())
        // A smart reminder's speaking loop posts its own separate notification (a
        // different ID, offset by 90000) - clear that one too so nothing lingers.
        notificationManager.cancel(90000 + reminderId.toInt())

        // Any real action taken on a smart reminder (done/snooze/dismiss) means the person
        // has responded - stop the repeating TTS voice immediately regardless of which
        // action they picked.
        if (action == "complete" || action == "snooze" || action == "dismiss") {
        }

        val manager = SmartReminderManager(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "complete" -> {
                        manager.completeReminder(reminderId)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "✅ یادآوری انجام شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "snooze" -> {
                        manager.snoozeReminder(reminderId, 10)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "⏰ یادآوری ۱۰ دقیقه به تعویق افتاد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "dismiss" -> {
                        manager.markAsCompleted(reminderId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReminderAction", "Error handling action", e)
            }
        }
    }
}
