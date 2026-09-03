package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.IncomeAdapter
import com.maliar.pro.databinding.FragmentIncomeListBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.dialogs.AddIncomeDialog
import com.maliar.pro.dialogs.EditIncomeDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch
import com.maliar.pro.utils.PersianCalendarHelper

class IncomeListFragment : Fragment() {
    private lateinit var binding: FragmentIncomeListBinding
    private lateinit var adapter: IncomeAdapter
    private var allIncomes: List<com.maliar.pro.database.Income> = emptyList()
    private var selectedMonth: Pair<Int, Int>? = null
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentIncomeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = IncomeAdapter(onItemClick = { showEditIncomeDialog(it) }, onDeleteClick = { deleteIncome(it) })
        binding.incomeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.incomeRecyclerView.adapter = adapter
        binding.addIncomeFab.setOnClickListener { AddIncomeDialog(requireContext(), viewModel).show() }
        binding.incomeMonthFilterButton.setOnClickListener { showMonthPicker() }
        loadIncomes()
    }

    private fun showEditIncomeDialog(income: com.maliar.pro.database.Income) {
        EditIncomeDialog(requireContext(), viewModel, income).show()
    }

    private fun loadIncomes() {
        lifecycleScope.launch {
            viewModel.incomeList.collect {
                allIncomes = it
                adapter.submitList(selectedMonth?.let { (y, m) -> it.filter { item -> PersianCalendarHelper.gregorianMillisToJalali(item.date).let { d -> d.first == y && d.second == m } } } ?: it)
            }
        }
    }

    private fun showMonthPicker() {
        val year = android.widget.EditText(requireContext()).apply { hint = "سال شمسی"; inputType = 2 }
        val month = android.widget.EditText(requireContext()).apply { hint = "ماه ۱ تا ۱۲"; inputType = 2 }
        val box = android.widget.LinearLayout(requireContext()).apply { orientation = 1; addView(year); addView(month) }
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("انتخاب ماه و سال")
            .setView(box).setNegativeButton("پاک کردن فیلتر") { _, _ -> selectedMonth = null; binding.incomeFilterLabel.text = "همه درآمدها"; adapter.submitList(allIncomes) }
            .setPositiveButton("نمایش") { _, _ ->
                val y = year.text.toString().toIntOrNull(); val m = month.text.toString().toIntOrNull()
                if (y == null || m == null || m !in 1..12) return@setPositiveButton
                selectedMonth = y to m; binding.incomeFilterLabel.text = "درآمدهای $m/$y"
                adapter.submitList(allIncomes.filter { PersianCalendarHelper.gregorianMillisToJalali(it.date).let { d -> d.first == y && d.second == m } })
            }.show()
    }

    private fun deleteIncome(income: com.maliar.pro.database.Income) {
        lifecycleScope.launch { viewModel.deleteIncome(income); loadIncomes() }
    }
}
