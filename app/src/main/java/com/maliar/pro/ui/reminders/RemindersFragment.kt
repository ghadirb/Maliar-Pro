package com.maliar.pro.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maliar.pro.R
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.database.Reminder
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
            onItemClick = { /* TODO: open edit */ },
            onDeleteClick = { reminder ->
                lifecycleScope.launch {
                    reminderManager.deleteReminder(reminder)
                    loadReminders()
                }
            },
            onCompleteClick = { reminder ->
                lifecycleScope.launch {
                    reminderManager.markAsCompleted(reminder.id)
                    loadReminders()
                }
            }
        )
        recyclerView.adapter = adapter

        // FAB
        view.findViewById<FloatingActionButton>(R.id.addReminderFab).setOnClickListener {
            // TODO: show add dialog
            android.util.Log.d("RemindersFragment", "Add reminder FAB clicked")
        }

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