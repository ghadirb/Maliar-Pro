package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: DebtType,
    val title: String,
    val amount: Double,
    val installmentAmount: Double? = null,
    val endDate: Long? = null,
    val description: String = "",
    val isPaid: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DebtType {
    LOAN,
    PERSONAL,
    CHECK,
    CREDIT_CARD,
    OTHER
}
