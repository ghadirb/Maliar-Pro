package com.maliar.pro.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.R
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.database.Priority
import com.maliar.pro.database.Reminder
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.SmartReminderManager
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var reminderManager: ReminderManager
    private lateinit var smartReminderManager: SmartReminderManager
    private lateinit var adapter: RemindersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminders, container, false)

        reminderManager = ReminderManager(requireContext())
        smartReminderManager = SmartReminderManager(requireContext())

        val recyclerView: RecyclerView = view.findViewById(R.id.remindersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RemindersAdapter(
            onItemClick = { /* TODO edit */ },
            onDeleteClick = { entity ->
                lifecycleScope.launch {
                    val reminder = Reminder(
                        id = entity.id,
                        title = entity.title,
                        description = entity.description,
                        reminderTime = entity.triggerTime,
                        isRecurring = entity.repeatPattern != "ONCE",
                        recurringType = com.maliar.pro.database.RecurringType.valueOf(entity.repeatPattern),
                        isCompleted = entity.isCompleted,
                        completedAt = entity.completedAt,
                        linkedCheckId = entity.linkedCheckId,
                        linkedInstallmentId = entity.linkedInstallmentId,
                        category = entity.category,
                        priority = Priority.valueOf(entity.priority)
                    )
                    reminderManager.deleteReminder(reminder)
                    loadReminders()
                }
            },
            onCompleteClick = { entity ->
                lifecycleScope.launch {
                    reminderManager.markAsCompleted(entity.id)
                    loadReminders()
                }
            }
        )
        recyclerView.adapter = adapter

        loadReminders()
        return view
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = reminderManager.getAllRemindersList()
            adapter.submitList(reminders.map { it.toReminderEntity() } ) // Map if adapter expects Entity
        }
    }
}

// Extension if needed
private fun Reminder.toReminderEntity(): ReminderEntity {
    return ReminderEntity(
        id = this.id,
        title = this.title,
        description = this.description,
        triggerTime = this.reminderTime,
        repeatPattern = this.recurringType.name,
        isCompleted = this.isCompleted,
        completedAt = this.completedAt,
        linkedCheckId = this.linkedCheckId,
        linkedInstallmentId = this.linkedInstallmentId,
        category = this.category,
        priority = this.priority.name
    )
}
