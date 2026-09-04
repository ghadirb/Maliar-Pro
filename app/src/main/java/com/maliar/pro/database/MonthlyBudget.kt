package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_budgets",
    indices = [Index(value = ["year", "month", "category"], unique = true)]
)
data class MonthlyBudget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int,
    val category: String,
    val amount: Double,
    val softThreshold: Int = 70,
    val hardThreshold: Int = 85,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
