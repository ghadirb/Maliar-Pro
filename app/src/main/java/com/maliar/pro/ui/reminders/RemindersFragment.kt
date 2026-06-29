package com.maliar.pro.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.databinding.FragmentRemindersBinding
import com.maliar.pro.database.Reminder
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.viewmodels.RemindersViewModel
import com.maliar.pro.viewmodels.RemindersViewModelFactory
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var binding: FragmentRemindersBinding
    private lateinit var adapter: RemindersAdapter
    private val viewModel: RemindersViewModel by viewModels {
        RemindersViewModelFactory(ReminderManager(requireContext()))
    }

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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            onItemClick = { reminder -> /* Handle click */ },
            onDeleteClick = { reminder -> viewModel.deleteReminder(reminder) },
            onCompleteClick = { reminder -> viewModel.markAsCompleted(reminder) }
        )
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.remindersRecyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.addReminderFab.setOnClickListener {
            showAddReminderDialog()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.reminders.collect { reminders ->
                adapter.submitList(reminders)
            }
        }
    }

    private fun showAddReminderDialog() {
        // Show dialog to add reminder
    }
}
