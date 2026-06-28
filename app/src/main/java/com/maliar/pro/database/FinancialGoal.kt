package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: GoalType,
    val title: String,
    val targetAmount: Double,
    val targetDate: Long,
    val priority: Priority,
    val currentProgress: Double = 0.0,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class GoalType {
    HOUSE,
    CAR,
    TRAVEL,
    INVESTMENT,
    EMERGENCY_FUND,
    RETIREMENT,
    CUSTOM
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}
