package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.Check
import com.maliar.pro.database.Expense
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.FinancialGoal
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
class AccountingViewModel(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager? = null
) : ViewModel() {

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

    /** Local, non-binding budget suggestion based on the last three completed months. */
    val suggestedMonthlyBudget = expenseList.map { list ->
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.DAY_OF_MONTH, 1)
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        now.set(java.util.Calendar.MINUTE, 0)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        val currentMonthStart = now.timeInMillis
        val threeMonthsAgo = (now.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.MONTH, -3)
        }.timeInMillis
        val history = list.filter { it.date >= threeMonthsAgo && it.date < currentMonthStart }
        if (history.isEmpty()) 0.0 else history.sumOf { it.amount } / 3.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val suggestedSpendableAmount = combine(suggestedMonthlyBudget, monthlyExpense) { budget, spent ->
        if (budget <= 0.0) null else (budget - spent).coerceAtLeast(0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeGoalsSummary = (financialStatusManager?.getAllGoals()
        ?.map { goals -> goals.filter { !it.isCompleted } }
        ?: kotlinx.coroutines.flow.flowOf<List<FinancialGoal>>(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<FinancialGoal>())

    val nearestGoalSavingsSuggestion = activeGoalsSummary.map { goals ->
        val goal = goals
            .filter { it.targetAmount > it.currentProgress && it.targetDate > System.currentTimeMillis() }
            .minByOrNull { it.targetDate }
        if (goal == null) null else {
            val remaining = (goal.targetAmount - goal.currentProgress).coerceAtLeast(0.0)
            val months = ((goal.targetDate - System.currentTimeMillis()).toDouble() /
                (30.44 * 24 * 60 * 60 * 1000)).coerceAtLeast(1.0)
            Triple(goal.title, remaining / months, months.toInt())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    data class EmergencyFundSummary(val target: Double, val current: Double, val percent: Int)

    val emergencyFundSummary = (if (financialStatusManager != null) {
        combine(
            financialStatusManager.getPreferencesFlow(),
            financialStatusManager.getAllAssets()
        ) { preferences, assets ->
            val target = preferences?.emergencyFundTarget ?: 0.0
            if (target <= 0.0) null
            else {
                val current = assets.filter {
                    it.type == com.maliar.pro.database.AssetType.CASH ||
                        it.type == com.maliar.pro.database.AssetType.BANK_ACCOUNT ||
                        it.type == com.maliar.pro.database.AssetType.DEPOSIT
                }.sumOf { it.value }.coerceAtLeast(0.0)
                EmergencyFundSummary(target, current, (current / target * 100.0).coerceIn(0.0, 100.0).toInt())
            }
        }
    } else kotlinx.coroutines.flow.flowOf<EmergencyFundSummary?>(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val unpaidDebtSummary = (financialStatusManager?.getAllDebts()
        ?.map { debts ->
            val unpaid = debts.filter { !it.isPaid }
            unpaid.sumOf { it.amount } to unpaid.size
        }
        ?: kotlinx.coroutines.flow.flowOf(0.0 to 0))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0 to 0)

    val sevenDayForecast = combine(monthlyIncome, monthlyExpense) { income, expense ->
        val start = accountingManager.getFinancialPeriodStartMillis()
        val elapsedDays = ((System.currentTimeMillis() - start) / (24L * 60 * 60 * 1000)).toInt() + 1
        if (elapsedDays < 1 || (income <= 0.0 && expense <= 0.0)) null
        else {
            val dailyNet = (income - expense) / elapsedDays
            ((income - expense) + dailyNet * 7.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val thirtyDayForecast = combine(monthlyIncome, monthlyExpense) { income, expense ->
        val start = accountingManager.getFinancialPeriodStartMillis()
        val elapsedDays = ((System.currentTimeMillis() - start) / (24L * 60 * 60 * 1000)).toInt() + 1
        if (elapsedDays < 1 || (income <= 0.0 && expense <= 0.0)) null
        else {
            val dailyNet = (income - expense) / elapsedDays
            (income - expense) + dailyNet * 30.0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val expenseAnalysis = expenseList.map { list ->
        val start = accountingManager.getFinancialPeriodStartMillis()
        val current = list.filter { it.date >= start }
        if (current.isEmpty()) null
        else {
            val categoryTotals = current.groupBy { it.category.trim().ifBlank { "عمومی" } }
                .mapValues { (_, items) -> items.sumOf { it.amount } }
            val top = categoryTotals.maxByOrNull { it.value }
            val elapsedDays = ((System.currentTimeMillis() - start) / (24L * 60 * 60 * 1000)).toInt() + 1
            ExpenseAnalysis(
                topCategory = top?.key ?: "عمومی",
                topCategoryAmount = top?.value ?: 0.0,
                dailyAverage = current.sumOf { it.amount } / elapsedDays.coerceAtLeast(1)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetStatus = combine(suggestedMonthlyBudget, monthlyExpense) { budget, spent ->
        when {
            budget <= 0.0 -> "وضعیت بودجه: داده کافی نیست"
            spent > budget -> "هشدار بودجه: هزینه‌های دوره از پیشنهاد بودجه عبور کرده است"
            spent >= budget * 0.8 -> "هشدار بودجه: حدود ${((spent / budget) * 100).toInt()}٪ بودجه مصرف شده است"
            else -> "وضعیت بودجه: در محدوده پیشنهادی"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "وضعیت بودجه: داده کافی نیست")

    val expenseTrend = expenseList.map { list ->
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        now.set(java.util.Calendar.MINUTE, 0)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        val currentStart = accountingManager.getFinancialPeriodStartMillis()
        val periodLength = (now.timeInMillis - currentStart).coerceAtLeast(24L * 60 * 60 * 1000)
        val previousStart = currentStart - periodLength
        val current = list.filter { it.date >= currentStart }.sumOf { it.amount }
        val previous = list.filter { it.date >= previousStart && it.date < currentStart }.sumOf { it.amount }
        if (previous <= 0.0) null else ((current - previous) / previous * 100.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    data class ExpenseAnalysis(
        val topCategory: String,
        val topCategoryAmount: Double,
        val dailyAverage: Double
    )

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
