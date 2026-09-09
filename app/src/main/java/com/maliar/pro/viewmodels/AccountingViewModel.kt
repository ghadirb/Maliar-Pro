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
import com.maliar.pro.database.PeriodicPayment
import com.maliar.pro.database.PeriodicPaymentManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
private const val ATTENTION_WINDOW_DAYS = 7L

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

    private val fixedIncomeList = (financialStatusManager?.getAllFixedIncomes()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assetList = (financialStatusManager?.getAllAssets()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val forecastAssetList = assetList

    val debtList = (financialStatusManager?.getAllDebts()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val forecastDebtList = debtList

    /** Active (not completed) financial goals, nearest target date first - for the Home
     *  tab's "اهداف مالی" card (spec: only show this section if the feature genuinely
     *  has data, never a placeholder goal). */
    val goalList = (financialStatusManager?.getAllGoals()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .map { goals -> goals.filter { !it.isCompleted }.sortedBy { it.targetDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val periodicPaymentManager = PeriodicPaymentManager(accountingManager.context)
    val periodicPayments = periodicPaymentManager.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val next30DayCommitments = periodicPayments.map { payments ->
        val now = System.currentTimeMillis()
        val horizon = now + 30L * 24 * 60 * 60 * 1000
        payments.filter { it.isActive && it.nextPaymentAt in now..horizon }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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

    /** Annual net profit: product revenue becomes profit only after cost of goods. */
    val balance = combine(incomeList, yearlyExpense) { incomes, exp ->
        incomes.filter { isThisYear(it.date) }.sumOf { it.profit } - exp
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome = incomeList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense = expenseList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyProfit = incomeList.map { list -> list.filter { isThisMonth(it.date) }.sumOf { it.profit } }
    val monthlyBalance = combine(monthlyProfit, monthlyExpense) { profit, exp -> profit - exp }
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

    data class DailySpendingSummary(
        val accountTitle: String,
        val remainingBalance: Double,
        val dailySuggestion: Double,
        val isConfigured: Boolean
    )

    val dailySpendingSummary = if (financialStatusManager != null) {
        combine(
            financialStatusManager.getAssetsByPurpose(com.maliar.pro.database.AccountPurpose.DAILY_SPENDING),
            expenseList
        ) { accounts, expenses ->
            val account = accounts.firstOrNull()
            if (account == null) {
                DailySpendingSummary("", 0.0, 0.0, false)
            } else {
                val spentFromAccount = expenses.filter { it.accountId == account.id }.sumOf { it.amount }
                val remaining = (account.value - spentFromAccount).coerceAtLeast(0.0)
                val (jalaliYear, jalaliMonth, jalaliDay) =
                    com.maliar.pro.utils.PersianCalendarHelper.getCurrentJalaliDate()
                val days = (com.maliar.pro.utils.PersianCalendarHelper.daysInJalaliMonth(jalaliYear, jalaliMonth) -
                    jalaliDay + 1).coerceAtLeast(1)
                val todayStart = startOfToday()
                val spentToday = expenses.filter { it.accountId == account.id && it.date >= todayStart }.sumOf { it.amount }
                val dailyLimitRemaining = account.dailyLimit?.let { (it - spentToday).coerceAtLeast(0.0) }
                val suggestion = minOf(remaining / days, dailyLimitRemaining ?: Double.MAX_VALUE)
                DailySpendingSummary(account.title, remaining, suggestion, true)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DailySpendingSummary("", 0.0, 0.0, false)
        )
    } else {
        kotlinx.coroutines.flow.flowOf(DailySpendingSummary("", 0.0, 0.0, false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailySpendingSummary("", 0.0, 0.0, false))
    }

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

    data class FinancialForecast(
        val days: Int,
        val startingBalance: Double,
        val projectedIncome: Double,
        val projectedExpense: Double,
        val projectedCommitments: Double,
        val projectedBalance: Double,
        val hasEnoughData: Boolean
    )

    private data class ForecastContext(
        val monthlyFixedIncome: Double,
        val monthlyInstallments: Double,
        val monthlyDebtPayments: Double,
        val liquidAssets: Double
    )

    private val forecastContext = combine(
        installmentList,
        fixedIncomeList,
        forecastAssetList,
        forecastDebtList,
        periodicPayments
    ) { installments, fixedIncomes, assets, debts, periodic ->
        ForecastContext(
            monthlyFixedIncome = fixedIncomes.sumOf { it.amount }.coerceAtLeast(0.0),
            monthlyInstallments = installments
                .filter { it.paidInstallments < it.totalInstallments }
                .sumOf { it.installmentAmount }
                .coerceAtLeast(0.0),
            monthlyDebtPayments = debts
                .filter { !it.isPaid }
                .sumOf { it.installmentAmount ?: 0.0 }
                .coerceAtLeast(0.0),
            liquidAssets = assets.filter {
                it.type == com.maliar.pro.database.AssetType.CASH ||
                    it.type == com.maliar.pro.database.AssetType.BANK_ACCOUNT ||
                    it.type == com.maliar.pro.database.AssetType.DEPOSIT
            }.sumOf { it.value }.coerceAtLeast(0.0)
        ).let { base ->
            val periodicMonthly = periodic.filter { it.isActive }.sumOf {
                (it.amount * 30.0 / it.periodDays.coerceAtLeast(1)).coerceAtLeast(0.0)
            }
            base.copy(monthlyInstallments = base.monthlyInstallments + periodicMonthly)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ForecastContext(0.0, 0.0, 0.0, 0.0)
    )

    /**
     * Conservative 30/60/90-day outlook ("آینده مالی من"). It only uses data stored
     * locally: recorded income/expense pace, fixed income, unpaid installment/debt
     * payments and liquid assets. It deliberately does not guess market prices or
     * future discretionary income - every number here is a "برآورد" (estimate), never
     * presented as certain.
     */
    val financialForecasts = combine(monthlyIncome, monthlyExpense, forecastContext) { income, expense, context ->
        val start = accountingManager.getFinancialPeriodStartMillis()
        val elapsedDays = ((System.currentTimeMillis() - start) / (24L * 60 * 60 * 1000)).toInt() + 1
        val hasActivity = income > 0.0 || expense > 0.0 || context.monthlyFixedIncome > 0.0 ||
            context.monthlyInstallments > 0.0 || context.monthlyDebtPayments > 0.0 ||
            context.liquidAssets > 0.0
        if (!hasActivity || elapsedDays < 1) {
            listOf(7, 30, 60, 90).map { FinancialForecast(it, 0.0, 0.0, 0.0, 0.0, 0.0, false) }
        } else {
            val observedDailyIncome = income / elapsedDays
            val expectedDailyIncome = if (context.monthlyFixedIncome > 0.0) {
                context.monthlyFixedIncome / 30.0
            } else {
                observedDailyIncome
            }
            val expectedDailyExpense = expense / elapsedDays
            val monthlyCommitments = context.monthlyInstallments + context.monthlyDebtPayments
            val startingBalance = if (context.liquidAssets > 0.0) context.liquidAssets else income - expense

            listOf(7, 30, 60, 90).map { days ->
                val commitmentCycles = ceil(days / 30.0)
                val projectedIncome = expectedDailyIncome * days
                val projectedExpense = expectedDailyExpense * days
                val projectedCommitments = monthlyCommitments * commitmentCycles
                val projected = startingBalance + projectedIncome - projectedExpense - projectedCommitments
                FinancialForecast(
                    days = days,
                    startingBalance = startingBalance,
                    projectedIncome = projectedIncome,
                    projectedExpense = projectedExpense,
                    projectedCommitments = projectedCommitments,
                    projectedBalance = projected,
                    hasEnoughData = true
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun forecastFor(days: Int) = financialForecasts.map { forecasts ->
        forecasts.firstOrNull { it.days == days }?.takeIf { it.hasEnoughData }?.projectedBalance
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sevenDayForecast = forecastFor(7)
    val thirtyDayForecast = forecastFor(30)
    val sixtyDayForecast = forecastFor(60)
    val ninetyDayForecast = forecastFor(90)

    /**
     * A single cautious warning sentence for the "آینده مالی من" screen, or null when
     * nothing is worth flagging. Only ever built from the already-computed 30-day
     * forecast (the spec's example horizon), and always phrased as an estimate
     * ("احتمال دارد"/"بر اساس اطلاعات ثبت‌شده") rather than a certainty - this never
     * blocks or alters any real transaction, it's purely informational.
     */
    val forecastAlert = financialForecasts.map { forecasts ->
        val thirty = forecasts.firstOrNull { it.days == 30 }?.takeIf { it.hasEnoughData } ?: return@map null
        when {
            thirty.projectedBalance < 0.0 ->
                "⚠️ بر اساس اطلاعات ثبت‌شده، در ۳۰ روز آینده احتمال کسری موجودی وجود دارد."
            thirty.startingBalance > 0.0 && thirty.projectedBalance < thirty.startingBalance * 0.3 ->
                "با روند فعلی هزینه‌ها، احتمال دارد موجودی قابل‌خرج شما در ۳۰ روز آینده کاهش قابل‌توجهی داشته باشد."
            else -> null
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

            // Previous period's per-category totals, used to find which category rose or
            // fell the most (spec: "بیشترین افزایش/کاهش نسبت به ماه قبل") - reuses the
            // exact same financial-period boundaries as expenseTrend below so "ماه قبل"
            // always means the previous financial period, not necessarily the 1st-to-1st
            // calendar month.
            val periodLength = (System.currentTimeMillis() - start).coerceAtLeast(24L * 60 * 60 * 1000)
            val previousStart = start - periodLength
            val previous = list.filter { it.date >= previousStart && it.date < start }
            val previousCategoryTotals = previous.groupBy { it.category.trim().ifBlank { "عمومی" } }
                .mapValues { (_, items) -> items.sumOf { it.amount } }
            val allCategories = categoryTotals.keys + previousCategoryTotals.keys
            val categoryChanges = allCategories.mapNotNull { cat ->
                val curr = categoryTotals[cat] ?: 0.0
                val prev = previousCategoryTotals[cat] ?: 0.0
                if (prev <= 0.0) null else CategoryChange(cat, curr, prev, (curr - prev) / prev * 100.0)
            }
            val biggestIncrease = categoryChanges.filter { it.changePercent > 0 }.maxByOrNull { it.changePercent }
            val biggestDecrease = categoryChanges.filter { it.changePercent < 0 }.minByOrNull { it.changePercent }

            // Costliest single calendar day in the current period (spec: "روزهای پرهزینه").
            val byDay = current.groupBy { expense ->
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = expense.date }
                cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
            }.mapValues { (_, items) -> items.sumOf { it.amount } }
            val topDayEntry = byDay.maxByOrNull { it.value }
            val topDayLabel = topDayEntry?.let { (dayKey, _) ->
                val sample = current.first { expense ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = expense.date }
                    cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR) == dayKey
                }
                val (y, m, d) = com.maliar.pro.utils.PersianCalendarHelper.gregorianMillisToJalali(sample.date)
                "$y/$m/$d"
            }

            ExpenseAnalysis(
                topCategory = top?.key ?: "عمومی",
                topCategoryAmount = top?.value ?: 0.0,
                dailyAverage = current.sumOf { it.amount } / elapsedDays.coerceAtLeast(1),
                transactionCount = current.size,
                biggestIncreaseCategory = biggestIncrease,
                biggestDecreaseCategory = biggestDecrease,
                topSpendingDayLabel = topDayLabel,
                topSpendingDayAmount = topDayEntry?.value ?: 0.0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Current financial period's expenses grouped by category, sorted highest first -
     * shared source of truth for anything that draws a category breakdown (the new
     * "خانه" tab's donut chart, in particular), reusing the exact same grouping/period
     * boundary as [expenseAnalysis] above so the two never disagree with each other.
     */
    val categoryBreakdown = expenseList.map { list ->
        val start = accountingManager.getFinancialPeriodStartMillis()
        list.filter { it.date >= start }
            .groupBy { it.category.trim().ifBlank { "سایر" } }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .filterValues { it > 0.0 }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val dailyAverage: Double,
        val transactionCount: Int = 0,
        val biggestIncreaseCategory: CategoryChange? = null,
        val biggestDecreaseCategory: CategoryChange? = null,
        val topSpendingDayLabel: String? = null,
        val topSpendingDayAmount: Double = 0.0
    )

    /** One category's change between the current and previous financial period, used by
     *  [expenseAnalysis]'s "بیشترین افزایش/کاهش نسبت به ماه قبل" breakdown. */
    data class CategoryChange(
        val category: String,
        val currentAmount: Double,
        val previousAmount: Double,
        val changePercent: Double
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

    enum class AttentionType { INSTALLMENT, DEBT, CHECK }

    /** One thing genuinely worth the person's attention soon - an unpaid installment,
     *  debt, or uncashed check within [ATTENTION_WINDOW_DAYS]. [dueInDays] is negative
     *  for something already overdue. */
    data class AttentionItem(
        val type: AttentionType,
        val title: String,
        val amount: Double,
        val dueInDays: Long
    )

    /**
     * Feeds the Home tab's "نیازمند توجه" card - built from installments/debts/checks
     * only (the same three financial-status pieces AccountingViewModel already tracks
     * elsewhere, e.g. [dueSoonChecksCount]/[activeInstallmentsCount]); reminders are a
     * separate manager entirely ([com.maliar.pro.database.SmartReminderManager]) and are
     * read directly by HomeFragment itself rather than threaded through here, so this
     * ViewModel's own scope doesn't need to grow to cover a different domain.
     * Sorted soonest-first; nothing here when nothing is actually due soon (spec: only
     * show this section when there's a real item, never an empty placeholder).
     */
    val attentionItems = combine(installmentList, debtList, checkList) { installments, debts, checks ->
        val now = System.currentTimeMillis()
        val horizon = now + ATTENTION_WINDOW_DAYS * 24L * 60 * 60 * 1000
        val fromInstallments = installments
            .filter { it.paidInstallments < it.totalInstallments }
            .mapNotNull { installment ->
                com.maliar.pro.utils.FinanceCalendarUtils.nextInstallmentDueDate(installment)?.let { due ->
                    if (due <= horizon) AttentionItem(AttentionType.INSTALLMENT, installment.title, installment.installmentAmount, (due - now) / (24L * 60 * 60 * 1000))
                    else null
                }
            }
        val fromDebts = debts
            .filter { !it.isPaid && it.endDate != null && it.endDate <= horizon }
            .map { AttentionItem(AttentionType.DEBT, it.title, it.amount, (it.endDate!! - now) / (24L * 60 * 60 * 1000)) }
        val fromChecks = checks
            .filter { !it.isCashed && it.dueDate <= horizon }
            .map { AttentionItem(AttentionType.CHECK, "چک ${it.checkNumber}".trim(), it.amount, (it.dueDate - now) / (24L * 60 * 60 * 1000)) }
        (fromInstallments + fromDebts + fromChecks).sortedBy { it.dueInDays }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** See AccountingManager.payInstallment: records a real expense linked to the
     *  installment's account (if any) and advances its paid count. */
    fun payInstallment(installment: Installment) {
        viewModelScope.launch { accountingManager.payInstallment(installment) }
    }
}
