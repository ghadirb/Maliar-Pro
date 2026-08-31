package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.Check
import com.maliar.pro.database.Expense
import com.maliar.pro.database.Income
import com.maliar.pro.database.Installment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * All numbers on this screen are now derived reactively from Room's Flow<List<...>>
 * queries instead of one-shot suspend loads that only refreshed when *this* ViewModel's
 * own add/update/delete functions were called.
 *
 * That one-shot pattern was the real reason the accounting dashboard looked like it
 * "didn't register" things the assistant tab saved: AssistantViewModel writes directly
 * through the same AccountingManager/Room database (a different ViewModel instance), so
 * the row really was being inserted - but nothing ever told *this* screen's StateFlows to
 * reload, so the header numbers stayed stale until the fragment was destroyed and
 * recreated. Deriving every total from the live Flow means ANY writer (this screen, the
 * assistant, a dialog, anything) shows up immediately, everywhere.
 */
class AccountingViewModel(private val accountingManager: AccountingManager) : ViewModel() {

    /** Whether [timestamp] falls within the *current* financial period, where the period
     *  starts on the day-of-month the user chose in the Profile tab (defaults to the 1st,
     *  i.e. the plain Jalali calendar month) - see AccountingManager.getFinancialPeriodStartMillis
     *  and PreferencesManager.getFinancialPeriodStartDay. Re-evaluated on every emission so a
     *  change to the setting is picked up next time the underlying income/expense list emits. */
    private fun isThisMonth(timestamp: Long): Boolean {
        return timestamp >= accountingManager.getFinancialPeriodStartMillis()
    }

    val incomeList: kotlinx.coroutines.flow.StateFlow<List<Income>> =
        accountingManager.getAllIncomes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseList: kotlinx.coroutines.flow.StateFlow<List<Expense>> =
        accountingManager.getAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkList: kotlinx.coroutines.flow.StateFlow<List<Check>> =
        accountingManager.getAllChecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installmentList: kotlinx.coroutines.flow.StateFlow<List<Installment>> =
        accountingManager.getAllInstallments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome = incomeList.map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense = expenseList.map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val balance = combine(totalIncome, totalExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome = incomeList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense = expenseList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyBalance = combine(monthlyIncome, monthlyExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val uncashedChecksCount = checkList.map { list -> list.count { !it.isCashed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uncashedChecksTotal = checkList.map { list -> list.filter { !it.isCashed }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val dueSoonChecksCount = checkList.map { list ->
        val weekAhead = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        list.count { !it.isCashed && it.dueDate in 0..weekAhead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeInstallmentsCount = installmentList.map { list -> list.count { it.paidInstallments < it.totalInstallments } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeInstallmentsMonthlyTotal = installmentList.map { list ->
        list.filter { it.paidInstallments < it.totalInstallments }.sumOf { it.installmentAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun deleteIncome(income: Income) {
        viewModelScope.launch { accountingManager.deleteIncome(income) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { accountingManager.deleteExpense(expense) }
    }

    fun deleteCheck(check: Check) {
        viewModelScope.launch { accountingManager.deleteCheck(check) }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch { accountingManager.deleteInstallment(installment) }
    }

    fun updateIncome(income: Income) {
        viewModelScope.launch { accountingManager.updateIncome(income) }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { accountingManager.updateExpense(expense) }
    }

    fun updateCheck(check: Check) {
        viewModelScope.launch { accountingManager.updateCheck(check) }
    }

    fun updateInstallment(installment: Installment) {
        viewModelScope.launch { accountingManager.updateInstallment(installment) }
    }

    fun addIncome(income: Income) {
        viewModelScope.launch { accountingManager.addIncome(income) }
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch { accountingManager.addExpense(expense) }
    }

    fun addCheck(check: Check) {
        viewModelScope.launch { accountingManager.addCheck(check) }
    }

    fun addInstallment(installment: Installment) {
        viewModelScope.launch { accountingManager.addInstallment(installment) }
    }
}
