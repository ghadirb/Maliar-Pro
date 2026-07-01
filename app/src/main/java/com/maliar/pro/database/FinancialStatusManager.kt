package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FinancialStatusManager(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val financialDao = database.financialStatusDao()
    
    // Assets
    fun getAllAssets(): Flow<List<Asset>> {
        return financialDao.getAllAssets()
    }
    
    suspend fun getAllAssetsList(): List<Asset> {
        return financialDao.getAllAssetsList()
    }
    
    suspend fun getTotalAssets(): Double {
        return financialDao.getTotalAssets() ?: 0.0
    }
    
    suspend fun addAsset(asset: Asset): Long {
        return financialDao.insertAsset(asset)
    }
    
    suspend fun addAsset(name: String, amount: Double): Long {
        val asset = Asset(type = AssetType.OTHER, title = name, value = amount)
        return financialDao.insertAsset(asset)
    }
    
    suspend fun updateAsset(asset: Asset) {
        financialDao.updateAsset(asset)
    }
    
    suspend fun deleteAsset(asset: Asset) {
        financialDao.deleteAsset(asset)
    }
    
    // Debts
    fun getAllDebts(): Flow<List<Debt>> {
        return financialDao.getAllDebts()
    }
    
    suspend fun getAllDebtsList(): List<Debt> {
        return financialDao.getAllDebtsList()
    }
    
    suspend fun getTotalUnpaidDebts(): Double {
        return financialDao.getTotalUnpaidDebts() ?: 0.0
    }
    
    suspend fun addDebt(debt: Debt): Long {
        return financialDao.insertDebt(debt)
    }
    
    suspend fun addDebt(name: String, amount: Double): Long {
        val debt = Debt(type = DebtType.OTHER, title = name, amount = amount, isPaid = false)
        return financialDao.insertDebt(debt)
    }
    
    suspend fun updateDebt(debt: Debt) {
        financialDao.updateDebt(debt)
    }
    
    suspend fun deleteDebt(debt: Debt) {
        financialDao.deleteDebt(debt)
    }
    
    // Goals
    fun getAllGoals(): Flow<List<FinancialGoal>> {
        return financialDao.getAllGoals()
    }
    
    suspend fun getAllGoalsList(): List<FinancialGoal> {
        return financialDao.getAllGoalsList()
    }
    
    suspend fun getActiveGoals(): List<FinancialGoal> {
        return financialDao.getActiveGoals()
    }
    
    suspend fun addGoal(goal: FinancialGoal): Long {
        return financialDao.insertGoal(goal)
    }
    
    suspend fun addFinancialGoal(name: String, targetAmount: Double): Long {
        val goal = FinancialGoal(
            type = GoalType.CUSTOM, 
            title = name, 
            targetAmount = targetAmount, 
            targetDate = System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L,
            priority = Priority.MEDIUM,
            currentProgress = 0.0
        )
        return financialDao.insertGoal(goal)
    }
    
    suspend fun updateGoal(goal: FinancialGoal) {
        financialDao.updateGoal(goal)
    }
    
    suspend fun deleteGoal(goal: FinancialGoal) {
        financialDao.deleteGoal(goal)
    }
    
    // Fixed Income
    fun getAllFixedIncomes(): Flow<List<FixedIncome>> {
        return financialDao.getAllFixedIncomes()
    }
    
    suspend fun getAllFixedIncomesList(): List<FixedIncome> {
        return financialDao.getAllFixedIncomesList()
    }
    
    suspend fun getTotalFixedIncome(): Double {
        return financialDao.getTotalFixedIncome() ?: 0.0
    }
    
    suspend fun addFixedIncome(income: FixedIncome): Long {
        return financialDao.insertFixedIncome(income)
    }
    
    suspend fun addFixedIncome(name: String, amount: Double): Long {
        val income = FixedIncome(type = IncomeType.OTHER, title = name, amount = amount)
        return financialDao.insertFixedIncome(income)
    }
    
    suspend fun updateFixedIncome(income: FixedIncome) {
        financialDao.updateFixedIncome(income)
    }
    
    suspend fun deleteFixedIncome(income: FixedIncome) {
        financialDao.deleteFixedIncome(income)
    }
    
    // Preferences
    suspend fun getPreferences(): FinancialPreferences? {
        return financialDao.getPreferences()
    }
    
    suspend fun savePreferences(preferences: FinancialPreferences): Long {
        return financialDao.insertPreferences(preferences)
    }
    
    suspend fun updatePreferences(preferences: FinancialPreferences) {
        financialDao.updatePreferences(preferences)
    }
    
    suspend fun setPreferences(emergencyFund: Double, savingGoal: Double) {
        val prefs = getPreferences()
        if (prefs != null) {
            updatePreferences(prefs.copy(
                emergencyFundTarget = emergencyFund, 
                monthlySavingGoal = savingGoal
            ))
        } else {
            savePreferences(FinancialPreferences(
                emergencyFundTarget = emergencyFund, 
                monthlySavingGoal = savingGoal,
                riskTolerance = RiskTolerance.MEDIUM,
                investmentInterest = false,
                savingsInterest = true,
                purchasePreference = PurchasePreference.CASH
            ))
        }
    }
    
    // Completion Percentage
    suspend fun getCompletionPercentage(): Int {
        var completed = 0
        val total = 7
        
        val assets = getAllAssetsList()
        if (assets.isNotEmpty()) completed++
        
        val debts = getAllDebtsList()
        if (debts.isNotEmpty()) completed++
        
        val goals = getAllGoalsList()
        if (goals.isNotEmpty()) completed++
        
        val incomes = getAllFixedIncomesList()
        if (incomes.isNotEmpty()) completed++
        
        val preferences = getPreferences()
        if (preferences != null) completed++
        
        return (completed * 100) / total
    }
}