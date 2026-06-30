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

    private val _monthlyIncome = MutableStateFlow(0.0)
    val monthlyIncome: StateFlow<Double> = _monthlyIncome.asStateFlow()

    private val _monthlyExpense = MutableStateFlow(0.0)
    val monthlyExpense: StateFlow<Double> = _monthlyExpense.asStateFlow()

    private val _incomeList = MutableStateFlow(emptyList<com.maliar.pro.database.Income>())
    val incomeList: StateFlow<List<com.maliar.pro.database.Income>> = _incomeList.asStateFlow()

    private val _expenseList = MutableStateFlow(emptyList<com.maliar.pro.database.Expense>())
    val expenseList: StateFlow<List<com.maliar.pro.database.Expense>> = _expenseList.asStateFlow()

    private val _checkList = MutableStateFlow(emptyList<com.maliar.pro.database.Check>())
    val checkList: StateFlow<List<com.maliar.pro.database.Check>> = _checkList.asStateFlow()

    private val _installmentList = MutableStateFlow(emptyList<com.maliar.pro.database.Installment>())
    val installmentList: StateFlow<List<com.maliar.pro.database.Installment>> = _installmentList.asStateFlow()

    init {
        loadStats()
        loadIncomeList()
        loadExpenseList()
        loadCheckList()
        loadInstallmentList()
    }

    private fun loadIncomeList() {
        viewModelScope.launch {
            accountingManager.getAllIncomes().collect { _incomeList.value = it }
        }
    }

    private fun loadExpenseList() {
        viewModelScope.launch {
            accountingManager.getAllExpenses().collect { _expenseList.value = it }
        }
    }

    private fun loadCheckList() {
        viewModelScope.launch {
            accountingManager.getAllChecks().collect { _checkList.value = it }
        }
    }

    private fun loadInstallmentList() {
        viewModelScope.launch {
            accountingManager.getAllInstallments().collect { _installmentList.value = it }
        }
    }

    fun deleteIncome(income: com.maliar.pro.database.Income) {
        viewModelScope.launch {
            accountingManager.deleteIncome(income)
            loadStats()
        }
    }

    fun deleteExpense(expense: com.maliar.pro.database.Expense) {
        viewModelScope.launch {
            accountingManager.deleteExpense(expense)
            loadStats()
        }
    }

    fun deleteCheck(check: com.maliar.pro.database.Check) {
        viewModelScope.launch {
            accountingManager.deleteCheck(check)
            loadStats()
        }
    }

    fun deleteInstallment(installment: com.maliar.pro.database.Installment) {
        viewModelScope.launch {
            accountingManager.deleteInstallment(installment)
            loadStats()
        }
    }

    fun updateIncome(income: com.maliar.pro.database.Income) {
        viewModelScope.launch {
            accountingManager.updateIncome(income)
            loadStats()
        }
    }

    fun updateExpense(expense: com.maliar.pro.database.Expense) {
        viewModelScope.launch {
            accountingManager.updateExpense(expense)
            loadStats()
        }
    }

    fun updateCheck(check: com.maliar.pro.database.Check) {
        viewModelScope.launch {
            accountingManager.updateCheck(check)
            loadStats()
        }
    }

    fun updateInstallment(installment: com.maliar.pro.database.Installment) {
        viewModelScope.launch {
            accountingManager.updateInstallment(installment)
            loadStats()
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val income = accountingManager.getTotalIncome()
            val expense = accountingManager.getTotalExpense()
            val bal = accountingManager.getBalance()
            val checks = accountingManager.getUncashedChecks()
            val installments = accountingManager.getActiveInstallments()
            val monthlyInc = accountingManager.getMonthlyIncome()
            val monthlyExp = accountingManager.getMonthlyExpense()

            _totalIncome.value = income
            _totalExpense.value = expense
            _balance.value = bal
            _uncashedChecksCount.value = checks.size
            _activeInstallmentsCount.value = installments.size
            _monthlyIncome.value = monthlyInc
            _monthlyExpense.value = monthlyExp
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
