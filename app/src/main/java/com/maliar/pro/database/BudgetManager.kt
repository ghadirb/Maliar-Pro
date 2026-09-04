package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class BudgetManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).budgetDao()

    fun getForMonth(year: Int, month: Int): Flow<List<MonthlyBudget>> = dao.getForMonth(year, month)

    suspend fun save(budget: MonthlyBudget): Long = dao.upsert(budget)

    suspend fun delete(budget: MonthlyBudget) = dao.delete(budget)
}
