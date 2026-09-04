package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FinancialStatusManager(context: Context) {
    
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val financialDao = database.financialStatusDao()
    private val marketRateHistoryDao = database.marketRateHistoryDao()
    
    // Assets
    fun getAllAssets(): Flow<List<Asset>> {
        return financialDao.getAllAssets()
    }

    fun getAssetsByPurpose(purpose: AccountPurpose): Flow<List<Asset>> {
        return financialDao.getAssetsByPurpose(purpose)
    }

    suspend fun setAccountPurpose(
        assetId: Long,
        purpose: AccountPurpose,
        assignExistingExpenses: Boolean = false
    ) {
        if (purpose == AccountPurpose.DAILY_SPENDING) {
            financialDao.clearPurpose(AccountPurpose.DAILY_SPENDING)
            if (assignExistingExpenses) {
                database.accountingDao().assignUnlinkedExpensesToAccount(assetId)
            }
        }
        financialDao.setPurpose(assetId, purpose)
    }

    suspend fun setDailyLimit(assetId: Long, dailyLimit: Double?) {
        financialDao.setDailyLimit(assetId, dailyLimit?.takeIf { it > 0.0 })
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

    /** Adds a gold asset the user specified by weight (grams) rather than a fixed price;
     *  its [Asset.value] is computed from the current rate right away and kept fresh
     *  afterwards by [refreshGoldAssetValues]. If no rate is available yet (offline/first
     *  run), the value simply starts at 0 and self-corrects the next time the rate is
     *  reachable - it never guesses a price. */
    suspend fun addGoldAsset(
        name: String,
        grams: Double,
        purpose: AccountPurpose = AccountPurpose.NORMAL,
        dailyLimit: Double? = null
    ): Long {
        val rate = runCatching { com.maliar.pro.utils.MarketRateClient(appContext).fetch() }.getOrNull()
        val value = rate?.gold?.let { grams * it / com.maliar.pro.utils.MarketRateClient.RIAL_TO_TOMAN } ?: 0.0
        val id = financialDao.insertAsset(
            Asset(
                type = AssetType.GOLD,
                title = name,
                value = value,
                goldGrams = grams,
                purpose = purpose,
                dailyLimit = dailyLimit?.takeIf { it > 0.0 }
            )
        )
        if (purpose != AccountPurpose.NORMAL) setAccountPurpose(id, purpose)
        return id
    }

    /** Re-prices every gold asset that was entered by weight (see [addGoldAsset]) against
     *  the latest gold rate, so their [Asset.value] - and therefore "کل دارایی‌ها" wherever
     *  it's summed - never goes stale. Best-effort: with no reachable rate this silently
     *  does nothing and leaves the last known values in place, exactly like the rest of the
     *  market-rate features. Safe to call often; it skips writes when the value hasn't
     *  meaningfully changed. */
    suspend fun refreshGoldAssetValues() {
        val rate = runCatching { com.maliar.pro.utils.MarketRateClient(appContext).fetch() }.getOrNull() ?: return
        val goldPerGramToman = rate.gold?.let { it / com.maliar.pro.utils.MarketRateClient.RIAL_TO_TOMAN } ?: return
        val goldAssets = getAllAssetsList().filter { it.type == AssetType.GOLD && (it.goldGrams ?: 0.0) > 0.0 }
        for (asset in goldAssets) {
            val newValue = asset.goldGrams!! * goldPerGramToman
            if (kotlin.math.abs(newValue - asset.value) > 1.0) {
                financialDao.updateAsset(asset.copy(value = newValue, updatedAt = System.currentTimeMillis()))
            }
        }
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

    fun getPreferencesFlow(): Flow<FinancialPreferences?> = financialDao.getPreferencesFlow()
    
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

    // Market rate history (feature: "روند نرخ طلا و دلار" chart on the reports screen)

    /** Upserts today's rate snapshot (replaces any row already recorded for today - see
     *  the unique index on [MarketRateHistory.date]) and prunes anything older than a
     *  year, so the table can't grow unbounded on a long-lived install. */
    suspend fun recordMarketRateSnapshot(rates: com.maliar.pro.utils.MarketRates) {
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        marketRateHistoryDao.insert(
            MarketRateHistory(
                date = todayStart,
                gold = rates.gold,
                currency = rates.currency,
                coinEmami = rates.coinEmami,
                coinHalf = rates.coinHalf,
                coinQuarter = rates.coinQuarter
            )
        )
        marketRateHistoryDao.deleteOlderThan(todayStart - 365L * 24 * 60 * 60 * 1000)
    }

    suspend fun getMarketRateHistory(days: Int): List<MarketRateHistory> {
        val since = startOfDayMillis(System.currentTimeMillis()) - days.toLong() * 24 * 60 * 60 * 1000
        return marketRateHistoryDao.getSince(since)
    }

    private fun startOfDayMillis(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
