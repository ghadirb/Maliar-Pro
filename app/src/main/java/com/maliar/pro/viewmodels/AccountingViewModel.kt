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

    /** Whether [timestamp] falls within the current Jalali (شمسی) year, i.e. on or after
     *  1 Farvardin of this year. Used for the headline "تراز کل" figure, which the person
     *  asked to scope to the current year rather than the app's entire lifetime. */
    private fun isThisYear(timestamp: Long): Boolean {
        val (currentYear, _, _) = com.maliar.pro.utils.PersianCalendarHelper.getCurrentJalaliDate()
        val yearStartMillis = com.maliar.pro.utils.PersianCalendarHelper.jalaliToGregorianMillis(currentYear, 1, 1)
        return timestamp >= yearStartMillis
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

    /** Headline "تراز کل" income/balance - scoped to the current Jalali *year* (1 Farvardin
     *  onward), per the person's request, rather than the app's entire lifetime. Still
     *  independent of the دوره selector below, which stays month/period-scoped. */
    val yearlyIncome = incomeList.map { list -> list.filter { isThisYear(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val yearlyExpense = expenseList.map { list -> list.filter { isThisYear(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val balance = combine(yearlyIncome, yearlyExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome = incomeList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense = expenseList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyBalance = combine(monthlyIncome, monthlyExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayExpense = expenseList.map { list ->
        val start = startOfToday()
        list.filter { it.date >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weekExpense = expenseList.map { list ->
        val start = startOfToday() - 6L * 24 * 60 * 60 * 1000
        list.filter { it.date >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentPeriodSavings = monthlyBalance

    val financialHealthScore = combine(monthlyIncome, monthlyExpense) { income, expense ->
        when {
            income <= 0.0 && expense <= 0.0 -> 0
            income <= 0.0 -> 25
            else -> {
                val ratio = ((income - expense) / income).coerceIn(-1.0, 1.0)
                (50 + (ratio * 50)).toInt().coerceIn(0, 100)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
