package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialStatusDao {
    
    // Assets
    @Query("SELECT * FROM assets ORDER BY value DESC")
    fun getAllAssets(): Flow<List<Asset>>
    
    @Query("SELECT * FROM assets ORDER BY value DESC")
    suspend fun getAllAssetsList(): List<Asset>
    
    @Query("SELECT SUM(value) FROM assets")
    suspend fun getTotalAssets(): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset): Long
    
    @Update
    suspend fun updateAsset(asset: Asset)
    
    @Delete
    suspend fun deleteAsset(asset: Asset)
    
    // Debts
    @Query("SELECT * FROM debts ORDER BY amount DESC")
    fun getAllDebts(): Flow<List<Debt>>
    
    @Query("SELECT * FROM debts ORDER BY amount DESC")
    suspend fun getAllDebtsList(): List<Debt>
    
    @Query("SELECT SUM(amount) FROM debts WHERE isPaid = 0")
    suspend fun getTotalUnpaidDebts(): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt): Long
    
    @Update
    suspend fun updateDebt(debt: Debt)
    
    @Delete
    suspend fun deleteDebt(debt: Debt)
    
    // Financial Goals
    @Query("SELECT * FROM financial_goals ORDER BY priority DESC, targetDate ASC")
    fun getAllGoals(): Flow<List<FinancialGoal>>
    
    @Query("SELECT * FROM financial_goals ORDER BY priority DESC, targetDate ASC")
    suspend fun getAllGoalsList(): List<FinancialGoal>
    
    @Query("SELECT * FROM financial_goals WHERE isCompleted = 0")
    suspend fun getActiveGoals(): List<FinancialGoal>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: FinancialGoal): Long
    
    @Update
    suspend fun updateGoal(goal: FinancialGoal)
    
    @Delete
    suspend fun deleteGoal(goal: FinancialGoal)
    
    // Fixed Income
    @Query("SELECT * FROM fixed_incomes ORDER BY amount DESC")
    fun getAllFixedIncomes(): Flow<List<FixedIncome>>
    
    @Query("SELECT * FROM fixed_incomes ORDER BY amount DESC")
    suspend fun getAllFixedIncomesList(): List<FixedIncome>
    
    @Query("SELECT SUM(amount) FROM fixed_incomes")
    suspend fun getTotalFixedIncome(): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixedIncome(income: FixedIncome): Long
    
    @Update
    suspend fun updateFixedIncome(income: FixedIncome)
    
    @Delete
    suspend fun deleteFixedIncome(income: FixedIncome)
    
    // Financial Preferences
    @Query("SELECT * FROM financial_preferences LIMIT 1")
    suspend fun getPreferences(): FinancialPreferences?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: FinancialPreferences): Long
    
    @Update
    suspend fun updatePreferences(preferences: FinancialPreferences)
}
