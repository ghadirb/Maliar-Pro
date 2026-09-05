package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maliar.pro.database.BudgetManager
import com.maliar.pro.database.Expense
import com.maliar.pro.database.MonthlyBudget
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.databinding.FragmentBudgetBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.maliar.pro.utils.FoodCatalog

class BudgetFragment : Fragment() {
    private lateinit var binding: FragmentBudgetBinding
    private val accounting by lazy { AccountingManager(requireContext()) }
    private val budgets by lazy { BudgetManager(requireContext()) }
    private val vm: BudgetViewModel by viewModels {
        BudgetViewModel.Factory(budgets, accounting)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addBudgetButton.setOnClickListener { showAddBudgetDialog() }
        binding.budgetMonthFilterButton.setOnClickListener { showMonthPicker() }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.selectedMonth.collect { (year, month) ->
                binding.budgetMonthLabel.text = "بودجه و مصرف ماه $month/$year"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.rows.collect { rows ->
                binding.budgetRows.removeAllViews()
                if (rows.isEmpty()) {
                    binding.emptyBudgetText.visibility = View.VISIBLE
                    binding.budgetSummaryText.text = "برای این ماه بودجه‌ای ثبت نشده است."
                } else {
                    binding.emptyBudgetText.visibility = View.GONE
                    val total = rows.sumOf { it.budget.amount }
                    val spent = rows.sumOf { it.spent }
                    binding.budgetSummaryText.text =
                        "بودجه کل: ${CurrencyFormatter.format(total)} · مصرف‌شده: ${CurrencyFormatter.format(spent)} · باقی‌مانده: ${CurrencyFormatter.format((total - spent).coerceAtLeast(0.0))}"
                    rows.forEach { row -> addRow(row) }
                }
            }
        }
    }

    private fun showMonthPicker() {
        val (currentYear, currentMonth) = vm.selectedMonth.value
        val year = EditText(requireContext()).apply {
            hint = "سال شمسی"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentYear.toString())
        }
        val month = EditText(requireContext()).apply {
            hint = "ماه ۱ تا ۱۲"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMonth.toString())
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(year)
            addView(month)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب ماه و سال شمسی")
            .setView(box)
            .setPositiveButton("نمایش") { _, _ ->
                val y = year.text.toString().toIntOrNull()
                val m = month.text.toString().toIntOrNull()
                if (y != null && m != null && m in 1..12) vm.selectMonth(y, m)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun addRow(row: BudgetRow) {
        val ratio = if (row.budget.amount <= 0.0) 0 else ((row.spent / row.budget.amount) * 100).toInt()
        val status = when {
            ratio >= 100 -> "عبور از سقف"
            ratio >= row.budget.hardThreshold -> "هشدار جدی"
            ratio >= row.budget.softThreshold -> "نزدیک به سقف"
            else -> "عادی"
        }
        val text = android.widget.TextView(requireContext()).apply {
            text = "${row.budget.category}\nبودجه: ${CurrencyFormatter.format(row.budget.amount)} · مصرف: ${CurrencyFormatter.format(row.spent)} · باقی‌مانده: ${CurrencyFormatter.format((row.budget.amount - row.spent).coerceAtLeast(0.0))}\nمصرف: $ratio٪ · وضعیت: $status"
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
        binding.budgetRows.addView(text)
    }

    private fun showAddBudgetDialog() {
        val category = EditText(requireContext()).apply { hint = "دسته هزینه، مثلاً خوراک" }
        val amount = EditText(requireContext()).apply {
            hint = "مبلغ بودجه (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(category)
            addView(amount)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن بودجه ماه شمسی")
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = category.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotBlank() && value > 0) vm.save(name, value)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}

data class BudgetRow(val budget: MonthlyBudget, val spent: Double)

class BudgetViewModel(
    private val budgetManager: BudgetManager,
    private val accountingManager: AccountingManager
) : androidx.lifecycle.ViewModel() {
    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    val selectedMonth = MutableStateFlow(today.first to today.second)
    val rows = combine(
        selectedMonth.flatMapLatest { (year, month) -> budgetManager.getForMonth(year, month) },
        accountingManager.getAllExpenses()
    ) { budgets, expenses ->
        budgets.map { budget ->
            BudgetRow(
                budget,
                expenses.filter { expense ->
                    categoryMatches(expense, budget.category) &&
                        PersianCalendarHelper.gregorianMillisToJalali(expense.date).let { it.first == budget.year && it.second == budget.month }
                }.sumOf(Expense::amount)
            )
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(category: String, amount: Double) {
        viewModelScope.launch {
            val (year, month) = selectedMonth.value
            budgetManager.save(
                MonthlyBudget(
                    year = year,
                    month = month,
                    category = category,
                    amount = amount
                )
            )
        }
    }

    private fun categoryMatches(expense: Expense, budgetCategory: String): Boolean {
        val budgetKey = budgetCategory.trim().lowercase()
        val expenseCategory = expense.category.trim().lowercase()
        if (expenseCategory == budgetKey || expenseCategory.replace("ي", "ی").replace("ك", "ک") == budgetKey) return true
        // Food budgets include itemised grocery entries (potato, tomato, etc.) even
        // when the user left the expense category as the default/blank value.
        if (budgetKey == "خوراک" || budgetKey.contains("غذا") || budgetKey.contains("مواد غذایی")) {
            return FoodCatalog.findMatch(expense.description) != null ||
                expenseCategory in setOf("سوپر", "فروشگاه", "خواربار", "مواد غذایی")
        }
        return false
    }

    fun selectMonth(year: Int, month: Int) {
        if (year in 1300..1600 && month in 1..12) selectedMonth.value = year to month
    }

    class Factory(
        private val manager: BudgetManager,
        private val accounting: AccountingManager
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            BudgetViewModel(manager, accounting) as T
    }
}
