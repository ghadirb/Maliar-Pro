package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.maliar.pro.database.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val action = intent.getStringExtra("action") ?: return

        if (reminderId < 0) return

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