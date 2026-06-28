package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentAccountingBinding
import com.maliar.pro.database.AccountingManager
import kotlinx.coroutines.launch

class AccountingFragment : Fragment() {

    private lateinit var binding: FragmentAccountingBinding
    private val accountingManager by lazy { AccountingManager(requireContext()) }

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
        loadStats()
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

    private fun loadStats() {
        lifecycleScope.launch {
            val totalIncome = accountingManager.getTotalIncome()
            val totalExpense = accountingManager.getTotalExpense()
            val balance = accountingManager.getBalance()
            val uncashedChecks = accountingManager.getUncashedChecks().size
            val activeInstallments = accountingManager.getActiveInstallments().size

            binding.incomeAmount.text = formatCurrency(totalIncome)
            binding.expenseAmount.text = formatCurrency(totalExpense)
            binding.balanceAmount.text = formatCurrency(balance)
            binding.checksCount.text = "$uncashedChecks چک"
            binding.installmentsCount.text = "$activeInstallments قسط"
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }

    private fun showAddIncomeDialog() {
        // Show income dialog
    }

    private fun showAddExpenseDialog() {
        // Show expense dialog
    }

    private fun showAddCheckDialog() {
        // Show check dialog
    }

    private fun showAddInstallmentDialog() {
        // Show installment dialog
    }
}
