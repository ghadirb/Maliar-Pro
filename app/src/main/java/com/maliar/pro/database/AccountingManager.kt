package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AccountingManager(val context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val accountingDao = database.accountingDao()
    
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
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
        return id
    }
    
    suspend fun updateIncome(income: Income) {
        accountingDao.updateIncome(income)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    suspend fun deleteIncome(income: Income) {
        accountingDao.deleteIncome(income)
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
        val id = accountingDao.insertExpense(expense.copy(accountId = linkedAccountId))
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
        return id
    }
    
    suspend fun updateExpense(expense: Expense) {
        accountingDao.updateExpense(expense)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
    }
    
    suspend fun deleteExpense(expense: Expense) {
        accountingDao.deleteExpense(expense)
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
