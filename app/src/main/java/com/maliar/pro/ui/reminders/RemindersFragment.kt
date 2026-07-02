package com.maliar.pro.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.R
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.databinding.FragmentRemindersBinding
import com.maliar.pro.viewmodels.RemindersViewModel
import com.maliar.pro.viewmodels.RemindersViewModelFactory
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RemindersAdapter
    private val viewModel: RemindersViewModel by viewModels { 
        RemindersViewModelFactory(SmartReminderManager(requireContext())) 
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            onComplete = { reminder -> viewModel.completeReminder(reminder) },
            onDelete = { reminder -> viewModel.deleteReminder(reminder) },
            onSnooze = { /* snooze logic */ }
        )
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.remindersRecyclerView.adapter = adapter
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filterType = when {
                checkedIds.contains(R.id.chipAll) -> "all"
                checkedIds.contains(R.id.chipTimeBased) -> "time"
                checkedIds.contains(R.id.chipRecurring) -> "recurring"
                checkedIds.contains(R.id.chipHighPriority) -> "high_priority"
                else -> "all"
            }
            viewModel.setFilter(filterType)
        }
    }

    private fun setupFab() {
        binding.addReminderFab.setOnClickListener {
            AddReminderDialogFragment().show(childFragmentManager, "add_reminder")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.reminders.collect { reminders ->
                adapter.submitList(reminders)
                updateStats()
                binding.emptyState.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateStats() {
        lifecycleScope.launch {
            val manager = SmartReminderManager(requireContext())
            val stats = manager.getReminderStats()
            binding.totalRemindersText.text = stats.totalReminders.toString()
            binding.activeRemindersText.text = stats.activeReminders.toString()
            binding.completedRemindersText.text = stats.completedReminders.toString()
            binding.todayRemindersText.text = stats.todayReminders.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}