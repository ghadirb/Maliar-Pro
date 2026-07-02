package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maliar.pro.R
import com.maliar.pro.databinding.FragmentFinancialStatusBinding
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.utils.PersianCalendarHelper
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
        binding.incomeCard.setOnClickListener { showFixedIncomeDialog() }
        binding.preferencesCard.setOnClickListener { showFinancialPreferencesDialog() }
        binding.aiSettingsCard.setOnClickListener {
            findNavController().navigate(R.id.action_financialStatusFragment_to_apiKeysFragment)
        }
    }

    private fun showAddAssetDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "نام دارایی" }
        val amountInput = EditText(requireContext()).apply { 
            hint = "مبلغ (تومان)"; 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(amountInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن دارایی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) viewModel.addAsset(name, amount)
            }
            .show()
    }

    private fun showAddDebtDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "نام بدهی" }
        val amountInput = EditText(requireContext()).apply { 
            hint = "مبلغ (تومان)"; 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(amountInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن بدهی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) viewModel.addDebt(name, amount)
            }
            .show()
    }

    private fun showAddGoalDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "نام هدف" }
        val amountInput = EditText(requireContext()).apply { 
            hint = "مبلغ هدف (تومان)"; 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(amountInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن هدف مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) viewModel.addFinancialGoal(name, amount)
            }
            .show()
    }

    private fun showFixedIncomeDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "نام درآمد ثابت" }
        val amountInput = EditText(requireContext()).apply { 
            hint = "مبلغ (تومان)"; 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(amountInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن درآمد ثابت")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) viewModel.addFixedIncome(name, amount)
            }
            .show()
    }

    private fun showFinancialPreferencesDialog() {
        val emergencyInput = EditText(requireContext()).apply { 
            hint = "هدف صندوق اضطراری (تومان)" 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val savingInput = EditText(requireContext()).apply { 
            hint = "هدف پس‌انداز ماهانه (تومان)" 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(emergencyInput)
            addView(savingInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val emergency = emergencyInput.text.toString().toDoubleOrNull() ?: 0.0
                val saving = savingInput.text.toString().toDoubleOrNull() ?: 0.0
                viewModel.setFinancialPreferences(emergency, saving)
            }
            .show()
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

    private fun formatCurrency(amount: Double): String = String.format("%,.0f تومان", amount)
}