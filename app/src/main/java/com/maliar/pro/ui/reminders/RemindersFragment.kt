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
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.SmartReminderManager
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
            onItemClick = { entity -> /* TODO: open edit dialog for ReminderEntity */ },
            onDeleteClick = { entity ->
                lifecycleScope.launch {
                    smartReminderManager.deleteReminder(entity)
                    loadReminders()
                }
            },
            onCompleteClick = { entity ->
                lifecycleScope.launch {
                    smartReminderManager.markAsCompleted(entity.id)
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
            val reminders: List<ReminderEntity> = smartReminderManager.getAllRemindersList()
            adapter.submitList(reminders)
        }
    }
}