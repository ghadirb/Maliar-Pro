package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE year = :year AND month = :month ORDER BY category")
    fun getForMonth(year: Int, month: Int): Flow<List<MonthlyBudget>>

    @Query("SELECT * FROM monthly_budgets WHERE year = :year AND month = :month ORDER BY category")
    suspend fun getForMonthList(year: Int, month: Int): List<MonthlyBudget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: MonthlyBudget): Long

    @Delete
    suspend fun delete(budget: MonthlyBudget)
}
