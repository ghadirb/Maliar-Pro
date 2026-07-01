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
        // دارایی‌ها - Navigate to assets section
        binding.assetsCard.setOnClickListener {
            showAddAssetDialog()
        }

        // بدهی‌ها - Navigate to debts section
        binding.debtsCard.setOnClickListener {
            showAddDebtDialog()
        }

        // اهداف مالی
        binding.goalsCard.setOnClickListener {
            showAddGoalDialog()
        }

        // درآمد ثابت - Navigate to fixed income section
        binding.incomeCard.setOnClickListener {
            showFixedIncomeDialog()
        }

        // ترجیحات مالی
        binding.preferencesCard.setOnClickListener {
            showFinancialPreferencesDialog()
        }

        // تنظیمات هوش مصنوعی - Navigate to API keys section
        binding.aiSettingsCard.setOnClickListener {
            findNavController().navigate(com.maliar.pro.R.id.action_financialStatusFragment_to_apiKeysFragment)
        }
    }

    private fun showAddAssetDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "مبلغ دارایی (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "نام دارایی (مثال: ملک، خودرو، طلا)"
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(nameInput)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("➕ افزودن دارایی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && amount > 0) {
                    viewModel.addAsset(name, amount)
                    Toast.makeText(requireContext(), "✅ دارایی اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddDebtDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "مبلغ بدهی (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "نام بدهی (مثال: وام، قرض)"
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(nameInput)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("➕ افزودن بدهی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && amount > 0) {
                    viewModel.addDebt(name, amount)
                    Toast.makeText(requireContext(), "✅ بدهی اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddGoalDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "مبلغ هدف (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "نام هدف (مثال: خرید خانه)"
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(nameInput)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎯 افزودن هدف مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && amount > 0) {
                    viewModel.addFinancialGoal(name, amount)
                    Toast.makeText(requireContext(), "✅ هدف مالی اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showFixedIncomeDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "مبلغ درآمد ثابت ماهانه (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "منبع درآمد (مثال: حقوق)"
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(nameInput)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("💰 درآمد ثابت")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && amount > 0) {
                    viewModel.addFixedIncome(name, amount)
                    Toast.makeText(requireContext(), "✅ درآمد ثابت ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showFinancialPreferencesDialog() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val emergencyFundInput = android.widget.EditText(requireContext()).apply {
            hint = "هدف صندوق اضطراری (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val savingGoalInput = android.widget.EditText(requireContext()).apply {
            hint = "هدف پس‌انداز ماهانه (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        container.addView(emergencyFundInput)
        container.addView(savingGoalInput)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚙️ ترجیحات مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val emergencyFund = emergencyFundInput.text.toString().toDoubleOrNull() ?: 0.0
                val savingGoal = savingGoalInput.text.toString().toDoubleOrNull() ?: 0.0
                viewModel.setFinancialPreferences(emergencyFund, savingGoal)
                Toast.makeText(requireContext(), "✅ ترجیحات مالی ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
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