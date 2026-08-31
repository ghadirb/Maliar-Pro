package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AccountingManager(context: Context) {

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
    }
    
    suspend fun deleteIncome(income: Income) {
        accountingDao.deleteIncome(income)
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
    
    suspend fun addExpense(expense: Expense): Long {
        val id = accountingDao.insertExpense(expense)
        com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(appContext)
        return id
    }
    
    suspend fun updateExpense(expense: Expense) {
        accountingDao.updateExpense(expense)
    }
    
    suspend fun deleteExpense(expense: Expense) {
        accountingDao.deleteExpense(expense)
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
    suspend fun getBalance(): Double {
        return getTotalIncome() - getTotalExpense()
    }

    suspend fun getMonthlyIncome(): Double {
        val start = getFinancialPeriodStartMillis()
        return accountingDao.getMonthlyIncome(start) ?: 0.0
    }

    suspend fun getMonthlyExpense(): Double {
        val start = getFinancialPeriodStartMillis()
        return accountingDao.getMonthlyExpense(start) ?: 0.0
    }

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
