package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One generated weekly meal plan. [weekStartDate] is midnight of the Jalali Saturday that
 *  week starts on, so re-generating for "this week" can find and replace the existing plan
 *  instead of piling up duplicates. */
@Entity(tableName = "meal_plans")
data class MealPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStartDate: Long,
    /** 0 = no budget set - the plan is generated purely by "prefer recent purchases, then
     *  cheapest" with no budget-fitting pass. */
    val budget: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

/** One meal slot in a plan: [dayOfWeek] 0=شنبه ... 6=جمعه, matching how the rest of this
 *  file's UI/logic index the week. [recipeName] refers back to RecipeCatalog.RECIPES by
 *  name (the catalog is static app data, not a DB table, so there's no FK here - just a
 *  lookup key). [estimatedCost] is a snapshot at generation time so past plans keep
 *  showing what they cost even if prices move later. */
@Entity(tableName = "meal_plan_entries")
data class MealPlanEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mealPlanId: Long,
    val dayOfWeek: Int,
    val mealType: String,
    val recipeName: String,
    val estimatedCost: Double
)
