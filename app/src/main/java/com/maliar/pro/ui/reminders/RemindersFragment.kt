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
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.dialogs.AddReminderDialog
import com.maliar.pro.dialogs.EditReminderDialog
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var smartReminderManager: SmartReminderManager
    private lateinit var adapter: RemindersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminders, container, false)

        smartReminderManager = SmartReminderManager(requireContext())

        val recyclerView: RecyclerView = view.findViewById(R.id.remindersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RemindersAdapter(
            onItemClick = { reminder ->
                EditReminderDialog(requireContext(), smartReminderManager, reminder) {
                    loadReminders()
                }.show()
            },
            onDeleteClick = { reminder ->
                lifecycleScope.launch {
                    smartReminderManager.deleteReminder(reminder)
                    loadReminders()
                }
            },
            onCompleteClick = { reminder ->
                lifecycleScope.launch {
                    smartReminderManager.completeReminder(reminder.id)
                    loadReminders()
                }
            }
        )
        recyclerView.adapter = adapter

        val fab: FloatingActionButton = view.findViewById(R.id.addReminderButton)
        fab.setOnClickListener {
            AddReminderDialog(requireContext(), smartReminderManager) {
                loadReminders()
            }.show()
        }

        loadReminders()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = smartReminderManager.getAllRemindersList()
            adapter.submitList(reminders)
        }
    }
}
