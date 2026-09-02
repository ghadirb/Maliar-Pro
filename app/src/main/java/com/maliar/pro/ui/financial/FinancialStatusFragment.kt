package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.maliar.pro.R
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
        // هر بخش با لمس، صفحه‌ی فهرست همان بخش را باز می‌کند؛ فهرست خودش
        // دکمه‌ی «+» برای افزودن مورد جدید و امکان مشاهده‌ی موارد قبلی را دارد.
        binding.assetsCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_assetListFragment)
        }
        binding.debtsCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_debtListFragment)
        }
        binding.goalsCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_goalListFragment)
        }
        binding.incomeCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_fixedIncomeListFragment)
        }
        binding.preferencesCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_financialPreferencesFragment)
        }
        binding.aiSettingsCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_apiKeysFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // اطلاعات خلاصه (دارایی/بدهی/خالص ارزش/درصد تکمیل) پس از بازگشت از هر
        // زیرصفحه به‌روز می‌شود تا تغییرات ثبت‌شده بلافاصله دیده شوند.
        viewModel.refreshData()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.totalAssets.collect { amount ->
                binding.totalAssets.text = formatCurrency(amount)
            }
        }
        lifecycleScope.launch {
            viewModel.totalDebts.collect { amount ->
                binding.totalDebts.text = formatCurrency(amount)
            }
        }
        lifecycleScope.launch {
            viewModel.netWorth.collect { amount ->
                binding.netWorth.text = formatCurrency(amount)
            }
        }
        lifecycleScope.launch {
            viewModel.completionPercentage.collect { percent ->
                binding.completionPercentage.text = "$percent%"
                binding.completionProgress.progress = percent
            }
        }
    }

    private fun formatCurrency(amount: Double): String = com.maliar.pro.utils.CurrencyFormatter.format(amount)
}
