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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter() // Use default or adjust constructor
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.remindersRecyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.reminders.collect { reminders ->
                adapter.submitList(reminders)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}