package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentAccountingBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.dialogs.AddIncomeDialog
import com.maliar.pro.dialogs.AddExpenseDialog
import com.maliar.pro.dialogs.AddCheckDialog
import com.maliar.pro.dialogs.AddInstallmentDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

class AccountingFragment : Fragment() {

    private lateinit var binding: FragmentAccountingBinding
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccountingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.incomeCard.setOnClickListener {
            // Navigate to income list
        }

        binding.expenseCard.setOnClickListener {
            // Navigate to expense list
        }

        binding.checksCard.setOnClickListener {
            // Navigate to checks list
        }

        binding.installmentsCard.setOnClickListener {
            // Navigate to installments list
        }

        binding.addIncomeButton.setOnClickListener {
            showAddIncomeDialog()
        }

        binding.addExpenseButton.setOnClickListener {
            showAddExpenseDialog()
        }

        binding.addCheckButton.setOnClickListener {
            showAddCheckDialog()
        }

        binding.addInstallmentButton.setOnClickListener {
            showAddInstallmentDialog()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.totalIncome.collect { income ->
                binding.incomeAmount.text = formatCurrency(income)
            }
        }
        lifecycleScope.launch {
            viewModel.totalExpense.collect { expense ->
                binding.expenseAmount.text = formatCurrency(expense)
            }
        }
        lifecycleScope.launch {
            viewModel.balance.collect { balance ->
                binding.balanceAmount.text = formatCurrency(balance)
            }
        }
        lifecycleScope.launch {
            viewModel.uncashedChecksCount.collect { count ->
                binding.checksCount.text = "$count چک"
            }
        }
        lifecycleScope.launch {
            viewModel.activeInstallmentsCount.collect { count ->
                binding.installmentsCount.text = "$count قسط"
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }

    private fun showAddIncomeDialog() {
        AddIncomeDialog(requireContext(), viewModel).show()
    }

    private fun showAddExpenseDialog() {
        AddExpenseDialog(requireContext(), viewModel).show()
    }

    private fun showAddCheckDialog() {
        AddCheckDialog(requireContext(), viewModel).show()
    }

    private fun showAddInstallmentDialog() {
        AddInstallmentDialog(requireContext(), viewModel).show()
    }
}
