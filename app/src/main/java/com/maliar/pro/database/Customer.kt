package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A business customer. Their balance is derived from [CustomerLedgerEntry] rows. */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class CustomerLedgerType { CREDIT_SALE, PAYMENT, ADJUSTMENT }

/** Immutable customer-account movement. Positive adjustments increase the receivable. */
@Entity(tableName = "customer_ledger_entries")
data class CustomerLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val type: CustomerLedgerType,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val note: String = "",
    val linkedIncomeId: Long? = null,
    val accountId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
