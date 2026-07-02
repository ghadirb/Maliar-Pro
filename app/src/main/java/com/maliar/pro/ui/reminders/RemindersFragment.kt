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
import com.maliar.pro.database.Reminder
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.ReminderManager
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var reminderManager: ReminderManager
    private lateinit var adapter: RemindersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminders, container, false)

        reminderManager = ReminderManager(requireContext())

        val recyclerView: RecyclerView = view.findViewById(R.id.remindersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RemindersAdapter(
            onItemClick = { /* edit */ },
            onDeleteClick = { entity ->
                lifecycleScope.launch {
                    val reminder = Reminder(
                        id = entity.id,
                        title = entity.title,
                        description = entity.description,
                        reminderTime = entity.triggerTime,
                        isRecurring = false,
                        recurringType = com.maliar.pro.database.RecurringType.NONE,
                        isCompleted = entity.isCompleted,
                        completedAt = entity.completedAt,
                        linkedCheckId = entity.linkedCheckId,
                        linkedInstallmentId = entity.linkedInstallmentId,
                        category = entity.category,
                        priority = com.maliar.pro.database.Priority.MEDIUM
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
            adapter.submitList(reminders)
        }
    }
}
