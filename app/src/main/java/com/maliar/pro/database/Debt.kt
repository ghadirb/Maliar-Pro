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
    val updatedAt: Long = System.currentTimeMillis(),
    /** Account this debt is settled from when marked paid (see
     *  FinancialStatusManager.toggleDebtPaid). Null keeps the old behavior: toggling
     *  isPaid is a plain flag flip with no effect on any account balance. */
    val accountId: Long? = null
)

enum class DebtType {
    LOAN,
    PERSONAL,
    CHECK,
    CREDIT_CARD,
    OTHER
}
