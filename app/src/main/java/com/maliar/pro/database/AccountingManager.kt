package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AccountingManager(val context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val accountingDao = database.accountingDao()
    private val financialStatusManager = FinancialStatusManager(context)

    // Income
    fun getAllIncomes(): Flow<List<Income>> {
        return accountingDao.getAllIncomes()
    }
    
    suspend fun getAllIncomesList(): List<Income> {
        return accountingDao.getAllIncomesList()
    }
    
    suspend fun getTotalIncome(): Double {
        return accountingDao.getTotalIncome() ?: 0.0
    }
    
    suspend fun addIncome(income: Income): Long {
        val id = accountingDao.insertIncome(income)
        financialStatusManager.adjustAssetBalance(income.accountId, income.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
        return id
    }
    
    suspend fun updateIncome(income: Income) {
        val previous = accountingDao.getIncomeById(income.id)
        if (previous != null) {
            // Reverse the old transaction's effect on its (possibly different) account,
            // then apply the new one - this is what makes editing an income's amount or
            // switching which account it's linked to keep every affected balance correct,
            // instead of just re-applying the new amount on top of the old one.
            financialStatusManager.adjustAssetBalance(previous.accountId, -previous.amount)
        }
        accountingDao.updateIncome(income)
        financialStatusManager.adjustAssetBalance(income.accountId, income.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    suspend fun deleteIncome(income: Income) {
        accountingDao.deleteIncome(income)
        financialStatusManager.adjustAssetBalance(income.accountId, -income.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    // Expense
    fun getAllExpenses(): Flow<List<Expense>> {
        return accountingDao.getAllExpenses()
    }
    
    suspend fun getAllExpensesList(): List<Expense> {
        return accountingDao.getAllExpensesList()
    }
    
    suspend fun getTotalExpense(): Double {
        return accountingDao.getTotalExpense() ?: 0.0
    }

    suspend fun getExpenseTotalForAccount(accountId: Long): Double {
        return accountingDao.getExpenseTotalForAccount(accountId) ?: 0.0
    }

    suspend fun assignUnlinkedExpensesToAccount(accountId: Long) {
        accountingDao.assignUnlinkedExpensesToAccount(accountId)
    }
    
    suspend fun addExpense(expense: Expense): Long {
        val linkedAccountId = expense.accountId ?: database.financialStatusDao()
            .getAssetsByPurposeList(AccountPurpose.DAILY_SPENDING)
            .firstOrNull()
            ?.id
        val toSave = expense.copy(accountId = linkedAccountId)
        val id = accountingDao.insertExpense(toSave)
        financialStatusManager.adjustAssetBalance(linkedAccountId, -toSave.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
        checkForUnusualExpense(expense, id)
        return id
    }

    /** Best-effort, local-only anomaly check: fires a single notification the moment a
     *  newly-added expense is far above what's typical for its own category this
     *  financial period, so the user finds out immediately instead of only if they
     *  happen to ask the assistant "هزینه غیرعادی؟" (see AssistantViewModel for that
     *  on-demand version, which this reuses the same >2x-average rule as). Needs at
     *  least [MIN_SAMPLES_FOR_ANOMALY] *other* same-category expenses this period before
     *  it will flag anything - too little history makes "average" meaningless and would
     *  otherwise nag on literally the first purchase in any new category. Never throws:
     *  this must never block or fail the actual expense save above it. */
    private suspend fun checkForUnusualExpense(expense: Expense, insertedId: Long) {
        if (expense.category.isBlank() || expense.amount <= 0.0) return
        try {
            val prefs = com.maliar.pro.utils.PreferencesManager(appContext)
            if (!prefs.isFinancialInsightsEnabled()) return
            val start = getFinancialPeriodStartMillis()
            val sameCategoryBefore = accountingDao.getAllExpensesList()
                .filter { it.category == expense.category && it.date >= start && it.id != insertedId }
            if (sameCategoryBefore.size < MIN_SAMPLES_FOR_ANOMALY) return
            val average = sameCategoryBefore.map { it.amount }.average()
            if (average <= 0.0 || expense.amount < average * ANOMALY_MULTIPLIER) return
            val amountText = com.maliar.pro.utils.CurrencyFormatter.format(expense.amount)
            val label = expense.description.ifBlank { expense.category }
            com.maliar.pro.utils.NotificationHelper.notifyFinancialInsight(
                appContext,
                "هزینه «$label» به مبلغ $amountText بیش از دو برابر میانگین هزینه‌های «${expense.category}» در این دوره است و می‌تواند غیرعادی باشد."
            )
        } catch (e: Exception) {
            // Best-effort feature; never let a notification failure affect the save above.
        }
    }

    companion object {
        private const val MIN_SAMPLES_FOR_ANOMALY = 3
        private const val ANOMALY_MULTIPLIER = 2.0
    }
    
    suspend fun updateExpense(expense: Expense) {
        val previous = accountingDao.getExpenseById(expense.id)
        if (previous != null) {
            // Same reverse-then-reapply pattern as updateIncome: undo the old amount's
            // effect on its old account first, then apply the new amount to the (possibly
            // different) new account.
            financialStatusManager.adjustAssetBalance(previous.accountId, previous.amount)
        }
        accountingDao.updateExpense(expense)
        financialStatusManager.adjustAssetBalance(expense.accountId, -expense.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    suspend fun deleteExpense(expense: Expense) {
        accountingDao.deleteExpense(expense)
        financialStatusManager.adjustAssetBalance(expense.accountId, expense.amount)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    // Check
    fun getAllChecks(): Flow<List<Check>> {
        return accountingDao.getAllChecks()
    }
    
    suspend fun getAllChecksList(): List<Check> {
        return accountingDao.getAllChecksList()
    }
    
    suspend fun getUncashedChecks(): List<Check> {
        return accountingDao.getUncashedChecks()
    }
    
    suspend fun getDueChecks(timestamp: Long = System.currentTimeMillis()): List<Check> {
        return accountingDao.getDueChecks(timestamp)
    }
    
    suspend fun addCheck(check: Check): Long {
        return accountingDao.insertCheck(check)
    }
    
    suspend fun updateCheck(check: Check) {
        accountingDao.updateCheck(check)
    }
    
    suspend fun deleteCheck(check: Check) {
        accountingDao.deleteCheck(check)
    }
    
    // Installment
    fun getAllInstallments(): Flow<List<Installment>> {
        return accountingDao.getAllInstallments()
    }
    
    suspend fun getAllInstallmentsList(): List<Installment> {
        return accountingDao.getAllInstallmentsList()
    }
    
    suspend fun getActiveInstallments(): List<Installment> {
        return accountingDao.getActiveInstallments()
    }
    
    suspend fun addInstallment(installment: Installment): Long {
        return accountingDao.insertInstallment(installment)
    }
    
    suspend fun updateInstallment(installment: Installment) {
        accountingDao.updateInstallment(installment)
    }
    
    suspend fun deleteInstallment(installment: Installment) {
        accountingDao.deleteInstallment(installment)
    }
    
    // Balance
    /** "تراز کل" - scoped to the current Jalali year (1 Farvardin onward), per the person's
     *  request, rather than the app's entire lifetime. Used by both the accounting
     *  dashboard's headline figure and the home-screen widget, so the two always agree. */
    suspend fun getBalance(): Double {
        val yearStart = com.maliar.pro.utils.PersianCalendarHelper.run {
            val (year, _, _) = getCurrentJalaliDate()
            jalaliToGregorianMillis(year, 1, 1)
        }
        val yearlyIncome = accountingDao.getMonthlyIncome(yearStart) ?: 0.0
        val yearlyExpense = accountingDao.getMonthlyExpense(yearStart) ?: 0.0
        return yearlyIncome - yearlyExpense
    }

    suspend fun getMonthlyIncome(): Double {
        val start = getFinancialPeriodStartMillis()
        return accountingDao.getMonthlyIncome(start) ?: 0.0
    }

    suspend fun getMonthlyExpense(): Double {
        val start = getFinancialPeriodStartMillis()
        return accountingDao.getMonthlyExpense(start) ?: 0.0
    }

    /** "تراز دوره" - period income minus period expense, using the same
     *  getFinancialPeriodStartMillis() boundary (the person's custom period-start-day
     *  from Profile) as AccountingViewModel.monthlyBalance on the accounting dashboard,
     *  so this and that screen always agree. Deliberately separate from getBalance()
     *  above ("تراز کل"), which is scoped to the whole Jalali year on purpose and
     *  shouldn't change just because the person picks a different period-start-day. */
    suspend fun getPeriodBalance(): Double = getMonthlyIncome() - getMonthlyExpense()

    /** Epoch millis for the start of the *current* financial period, based on the
     *  period-start-day the user picked in the Profile tab (defaults to the 1st of the
     *  Jalali month when unset). Plain synchronous SharedPreferences + calendar math, so
     *  it's safe to call from reactive (non-suspend) Flow.map transforms too - see
     *  AccountingViewModel.isThisPeriod(). */
    fun getFinancialPeriodStartMillis(): Long {
        val periodStartDay = com.maliar.pro.utils.PreferencesManager(appContext).getFinancialPeriodStartDay()
        return com.maliar.pro.utils.PersianCalendarHelper.currentFinancialPeriodStartMillis(periodStartDay)
    }
}
