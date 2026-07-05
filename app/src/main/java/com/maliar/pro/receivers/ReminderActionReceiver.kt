package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
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

        // Any real action taken on a smart reminder (done/snooze/dismiss) means the person
        // has responded - stop the repeating TTS voice immediately regardless of which
        // action they picked.
        if (action == "complete" || action == "snooze" || action == "dismiss") {
            SmartReminderTtsService.stop(reminderId)
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