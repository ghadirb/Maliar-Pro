package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentFinancialStatusBinding
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.viewmodels.FinancialStatusViewModel
import com.maliar.pro.viewmodels.FinancialStatusViewModelFactory
import kotlinx.coroutines.launch

class FinancialStatusFragment : Fragment() {

    private lateinit var binding: FragmentFinancialStatusBinding
    private val viewModel: FinancialStatusViewModel by viewModels {
        FinancialStatusViewModelFactory(FinancialStatusManager(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFinancialStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSections()
        observeViewModel()
    }

    private fun setupSections() {
        binding.assetsCard.setOnClickListener {
            // Navigate to assets section
        }

        binding.debtsCard.setOnClickListener {
            // Navigate to debts section
        }

        binding.goalsCard.setOnClickListener {
            // Navigate to goals section
        }

        binding.incomeCard.setOnClickListener {
            // Navigate to fixed income section
        }

        binding.preferencesCard.setOnClickListener {
            // Navigate to preferences section
        }

        binding.aiSettingsCard.setOnClickListener {
            // Navigate to AI settings section
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.totalAssets.collect { assets ->
                binding.totalAssets.text = formatCurrency(assets)
            }
        }
        lifecycleScope.launch {
            viewModel.totalDebts.collect { debts ->
                binding.totalDebts.text = formatCurrency(debts)
            }
        }
        lifecycleScope.launch {
            viewModel.netWorth.collect { netWorth ->
                binding.netWorth.text = formatCurrency(netWorth)
            }
        }
        lifecycleScope.launch {
            viewModel.completionPercentage.collect { completion ->
                binding.completionProgress.progress = completion
                binding.completionPercentage.text = "$completion%"
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }
}
