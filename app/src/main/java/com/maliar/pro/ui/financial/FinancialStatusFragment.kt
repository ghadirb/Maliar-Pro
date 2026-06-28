package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentFinancialStatusBinding
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.launch

class FinancialStatusFragment : Fragment() {

    private lateinit var binding: FragmentFinancialStatusBinding
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }

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
        setupCompletionProgress()
        setupSections()
        loadFinancialData()
    }

    private fun setupCompletionProgress() {
        lifecycleScope.launch {
            val completion = financialManager.getCompletionPercentage()
            binding.completionProgress.progress = completion
            binding.completionPercentage.text = "$completion%"
        }
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

    private fun loadFinancialData() {
        lifecycleScope.launch {
            val totalAssets = financialManager.getTotalAssets()
            val totalDebts = financialManager.getTotalUnpaidDebts()
            val netWorth = totalAssets - totalDebts

            binding.totalAssets.text = formatCurrency(totalAssets)
            binding.totalDebts.text = formatCurrency(totalDebts)
            binding.netWorth.text = formatCurrency(netWorth)
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }
}
