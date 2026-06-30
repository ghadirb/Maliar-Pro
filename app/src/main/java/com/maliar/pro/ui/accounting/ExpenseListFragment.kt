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

class ExpenseListFragment : Fragment() {
    private lateinit var binding: FragmentExpenseListBinding
    private lateinit var adapter: ExpenseAdapter
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
        loadExpenses()
    }

    private fun showEditExpenseDialog(expense: com.maliar.pro.database.Expense) {
        EditExpenseDialog(requireContext(), viewModel, expense).show()
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            viewModel.expenseList.collect { adapter.submitList(it) }
        }
    }

    private fun deleteExpense(expense: com.maliar.pro.database.Expense) {
        lifecycleScope.launch { viewModel.deleteExpense(expense); loadExpenses() }
    }
}
