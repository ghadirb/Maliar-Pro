package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.ExpenseAdapter
import com.maliar.pro.databinding.FragmentExpenseListBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.dialogs.AddExpenseDialog
import com.maliar.pro.dialogs.EditExpenseDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch
import com.maliar.pro.utils.PersianCalendarHelper

class ExpenseListFragment : Fragment() {
    private lateinit var binding: FragmentExpenseListBinding
    private lateinit var adapter: ExpenseAdapter
    private var allExpenses: List<com.maliar.pro.database.Expense> = emptyList()
    private var selectedMonth: Pair<Int, Int>? = null
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExpenseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ExpenseAdapter(onItemClick = { showEditExpenseDialog(it) }, onDeleteClick = { deleteExpense(it) })
        binding.expenseRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.expenseRecyclerView.adapter = adapter
        binding.addExpenseFab.setOnClickListener { AddExpenseDialog(requireContext(), viewModel).show() }
        binding.expenseMonthFilterButton.setOnClickListener { showMonthPicker() }
        loadExpenses()
    }

    private fun showEditExpenseDialog(expense: com.maliar.pro.database.Expense) {
        EditExpenseDialog(requireContext(), viewModel, expense).show()
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            viewModel.expenseList.collect {
                allExpenses = it
                adapter.submitList(selectedMonth?.let { (y, m) -> it.filter { item -> PersianCalendarHelper.gregorianMillisToJalali(item.date).let { d -> d.first == y && d.second == m } } } ?: it)
            }
        }
    }

    private fun showMonthPicker() {
        val year = android.widget.EditText(requireContext()).apply { hint = "سال شمسی"; inputType = 2 }
        val month = android.widget.EditText(requireContext()).apply { hint = "ماه ۱ تا ۱۲"; inputType = 2 }
        val box = android.widget.LinearLayout(requireContext()).apply { orientation = 1; addView(year); addView(month) }
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("انتخاب ماه و سال")
            .setView(box).setNegativeButton("پاک کردن فیلتر") { _, _ -> selectedMonth = null; binding.expenseFilterLabel.text = "همه هزینه‌ها"; adapter.submitList(allExpenses) }
            .setPositiveButton("نمایش") { _, _ ->
                val y = year.text.toString().toIntOrNull(); val m = month.text.toString().toIntOrNull()
                if (y == null || m !in 1..12) return@setPositiveButton
                selectedMonth = y to m; binding.expenseFilterLabel.text = "هزینه‌های $m/$y"
                adapter.submitList(allExpenses.filter { PersianCalendarHelper.gregorianMillisToJalali(it.date).let { d -> d.first == y && d.second == m } })
            }.show()
    }

    private fun deleteExpense(expense: com.maliar.pro.database.Expense) {
        lifecycleScope.launch { viewModel.deleteExpense(expense); loadExpenses() }
    }
}
