package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fixed_incomes")
data class FixedIncome(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: IncomeType,
    val title: String,
    val amount: Double,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class IncomeType {
    SALARY,
    SECOND_JOB,
    RENT,
    INVESTMENT_RETURN,
    OTHER
}
