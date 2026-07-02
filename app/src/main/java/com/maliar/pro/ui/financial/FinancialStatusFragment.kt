package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
        binding.assetsCard.setOnClickListener { showAddAssetDialog() }
        binding.debtsCard.setOnClickListener { showAddDebtDialog() }
        binding.goalsCard.setOnClickListener { showAddGoalDialog() }
        binding.incomeCard.setOnClickListener { showFixedIncomeDialog() }  // Fixed: opens fixed income, not accounting
        binding.preferencesCard.setOnClickListener { showFinancialPreferencesDialog() }
        binding.aiSettingsCard.setOnClickListener { 
            // Fixed: to API keys, not assistant
            findNavController().navigate(com.maliar.pro.R.id.action_financialStatusFragment_to_apiKeysFragment)
        }
    }

    // ... (rest of dialogs and observe same as before, with Jalali support note)
    private fun formatCurrency(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }

    // Add Jalali date helper if needed
}