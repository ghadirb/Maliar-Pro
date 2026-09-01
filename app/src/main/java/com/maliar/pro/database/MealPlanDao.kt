package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {

    @Query("SELECT * FROM meal_plans ORDER BY weekStartDate DESC")
    fun getAllPlans(): Flow<List<MealPlan>>

    @Query("SELECT * FROM meal_plans ORDER BY weekStartDate DESC LIMIT 1")
    fun getLatestPlan(): Flow<MealPlan?>

    @Query("SELECT * FROM meal_plans WHERE weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getPlanForWeek(weekStartDate: Long): MealPlan?

    @Insert
    suspend fun insertPlan(plan: MealPlan): Long

    @Delete
    suspend fun deletePlan(plan: MealPlan)

    @Query("SELECT * FROM meal_plan_entries WHERE mealPlanId = :planId ORDER BY dayOfWeek ASC")
    fun getEntries(planId: Long): Flow<List<MealPlanEntry>>

    @Query("SELECT * FROM meal_plan_entries WHERE mealPlanId = :planId ORDER BY dayOfWeek ASC")
    suspend fun getEntriesList(planId: Long): List<MealPlanEntry>

    @Insert
    suspend fun insertEntries(entries: List<MealPlanEntry>)

    @Query("DELETE FROM meal_plan_entries WHERE mealPlanId = :planId")
    suspend fun deleteEntriesForPlan(planId: Long)
}
