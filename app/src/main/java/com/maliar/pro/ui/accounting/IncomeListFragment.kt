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
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

class IncomeListFragment : Fragment() {
    private lateinit var binding: FragmentIncomeListBinding
    private lateinit var adapter: IncomeAdapter
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
        loadIncomes()
    }

    private fun showEditIncomeDialog(income: com.maliar.pro.database.Income) {
        EditIncomeDialog(requireContext(), viewModel, income).show()
    }

    private fun loadIncomes() {
        lifecycleScope.launch {
            viewModel.incomeList.collect { adapter.submitList(it) }
        }
    }

    private fun deleteIncome(income: com.maliar.pro.database.Income) {
        lifecycleScope.launch { viewModel.deleteIncome(income); loadIncomes() }
    }
}
