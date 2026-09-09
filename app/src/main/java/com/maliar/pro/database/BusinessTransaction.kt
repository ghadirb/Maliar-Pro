package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cash movements which are deliberately not ordinary income or expense. */
@Entity(tableName = "business_transactions")
data class BusinessTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BusinessTransactionType,
    val amount: Double,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val supplier: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unitCost: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BusinessTransactionType {
    PRODUCT_PURCHASE, TRANSFER, OWNER_DRAW, CAPITAL_INJECTION, BANK_FEE
}

/** Current stock is kept separately from purchase history so the UI can show it quickly. */
@Entity(tableName = "product_inventory")
data class ProductInventory(
    @PrimaryKey val name: String,
    val quantity: Double = 0.0,
    val averageUnitCost: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
