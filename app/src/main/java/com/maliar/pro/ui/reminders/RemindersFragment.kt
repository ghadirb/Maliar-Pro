package com.maliar.pro.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentRemindersBinding
import com.maliar.pro.database.ReminderManager
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var binding: FragmentRemindersBinding
    private val reminderManager by lazy { ReminderManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        loadReminders()
    }

    private fun setupRecyclerView() {
        // Setup adapter for reminders
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFab() {
        binding.addReminderFab.setOnClickListener {
            showAddReminderDialog()
        }
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = reminderManager.getActiveRemindersList()
            // Update adapter
        }
    }

    private fun showAddReminderDialog() {
        // Show dialog to add reminder
    }
}
