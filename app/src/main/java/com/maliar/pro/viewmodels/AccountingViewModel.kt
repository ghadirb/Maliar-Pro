package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.Check
import com.maliar.pro.database.Expense
import com.maliar.pro.database.Income
import com.maliar.pro.database.Installment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountingViewModel(private val accountingManager: AccountingManager) : ViewModel() {

    private val _totalIncome = MutableStateFlow(0.0)
    val totalIncome: StateFlow<Double> = _totalIncome.asStateFlow()

    private val _totalExpense = MutableStateFlow(0.0)
    val totalExpense: StateFlow<Double> = _totalExpense.asStateFlow()

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance.asStateFlow()

    private val _uncashedChecksCount = MutableStateFlow(0)
    val uncashedChecksCount: StateFlow<Int> = _uncashedChecksCount.asStateFlow()

    private val _activeInstallmentsCount = MutableStateFlow(0)
    val activeInstallmentsCount: StateFlow<Int> = _activeInstallmentsCount.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val income = accountingManager.getTotalIncome()
            val expense = accountingManager.getTotalExpense()
            val bal = accountingManager.getBalance()
            val checks = accountingManager.getUncashedChecks()
            val installments = accountingManager.getActiveInstallments()

            _totalIncome.value = income
            _totalExpense.value = expense
            _balance.value = bal
            _uncashedChecksCount.value = checks.size
            _activeInstallmentsCount.value = installments.size
        }
    }

    fun addIncome(income: Income) {
        viewModelScope.launch {
            accountingManager.addIncome(income)
            loadStats()
        }
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            accountingManager.addExpense(expense)
            loadStats()
        }
    }

    fun addCheck(check: Check) {
        viewModelScope.launch {
            accountingManager.addCheck(check)
            loadStats()
        }
    }

    fun addInstallment(installment: Installment) {
        viewModelScope.launch {
            accountingManager.addInstallment(installment)
            loadStats()
        }
    }
}
