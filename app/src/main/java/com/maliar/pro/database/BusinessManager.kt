package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Coordinates stock with cash movements. Purchases are inventory assets, never expenses. */
class BusinessManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val dao = database.businessDao()
    private val accounts = FinancialStatusManager(appContext)

    fun getInventory(): Flow<List<ProductInventory>> = dao.getInventory()
    fun getTransactions(): Flow<List<BusinessTransaction>> = dao.getAllTransactions()
    suspend fun inventoryCost(name: String, quantity: Double): Double =
        (dao.getInventoryItem(name.trim())?.averageUnitCost ?: 0.0) * quantity

    suspend fun add(item: BusinessTransaction): Long {
        val id = dao.insertTransaction(item)
        apply(item, 1.0)
        reconcileInventory(item.productName)
        syncProductPricing(item)
        return id
    }
    suspend fun update(item: BusinessTransaction) {
        val previous = dao.getTransaction(item.id)
        previous?.let { apply(it, -1.0) }
        dao.updateTransaction(item)
        apply(item, 1.0)
        reconcileInventory(previous?.productName.orEmpty())
        reconcileInventory(item.productName)
        syncProductPricing(item)
    }
    suspend fun delete(item: BusinessTransaction) {
        dao.deleteTransaction(item)
        apply(item, -1.0)
        reconcileInventory(item.productName)
    }

    private suspend fun apply(item: BusinessTransaction, direction: Double) {
        when (item.type) {
            BusinessTransactionType.PRODUCT_PURCHASE -> {
                accounts.adjustAssetBalance(item.fromAccountId, -item.amount * direction)
                val name = item.productName.trim()
                if (name.isNotEmpty()) {
                    val old = dao.getInventoryItem(name) ?: ProductInventory(name)
                    val delta = item.quantity * direction
                    val newQuantity = old.quantity + delta
                    val newCost = if (direction > 0 && newQuantity > 0) {
                        ((old.quantity * old.averageUnitCost) + (item.quantity * item.unitCost)) / newQuantity
                    } else old.averageUnitCost
                    dao.upsertInventory(old.copy(quantity = newQuantity, averageUnitCost = newCost, updatedAt = System.currentTimeMillis()))
                }
            }
            BusinessTransactionType.TRANSFER -> {
                accounts.adjustAssetBalance(item.fromAccountId, -item.amount * direction)
                accounts.adjustAssetBalance(item.toAccountId, item.amount * direction)
            }
            BusinessTransactionType.OWNER_DRAW -> accounts.adjustAssetBalance(item.fromAccountId, -item.amount * direction)
            BusinessTransactionType.CAPITAL_INJECTION -> accounts.adjustAssetBalance(item.toAccountId, item.amount * direction)
            BusinessTransactionType.BANK_FEE -> accounts.adjustAssetBalance(item.fromAccountId, -item.amount * direction)
        }
    }

    /** A product bought in accounting becomes visible in «قیمت کالاها» automatically.
     * Purchase history stays in the accounting ledger, which is the source of truth. */
    private suspend fun syncProductPricing(item: BusinessTransaction) {
        if (item.type == BusinessTransactionType.PRODUCT_PURCHASE && item.productName.isNotBlank()) {
            MarketAssistantManager(appContext).ensureProduct(item.productName)
        }
    }

    /** Re-derives quantity and weighted cost from the immutable purchase history after
     * edit/delete; this avoids a drifting average cost when an old purchase is corrected. */
    private suspend fun reconcileInventory(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        val purchases = dao.getAllTransactionsList().filter {
            it.type == BusinessTransactionType.PRODUCT_PURCHASE && it.productName.trim() == name
        }
        val purchasedQuantity = purchases.sumOf { it.quantity }
        val purchasedValue = purchases.sumOf { it.quantity * it.unitCost }
        val current = dao.getInventoryItem(name) ?: ProductInventory(name)
        // Keep sale-reduced stock quantity; recompute only cost basis from surviving buys.
        val average = if (purchasedQuantity > 0) purchasedValue / purchasedQuantity else 0.0
        dao.upsertInventory(current.copy(averageUnitCost = average, updatedAt = System.currentTimeMillis()))
    }

    suspend fun adjustStockForSale(income: Income, direction: Double) {
        if (!income.isProductSale || income.productName.isBlank()) return
        val name = income.productName.trim()
        val old = dao.getInventoryItem(name) ?: ProductInventory(name)
        dao.upsertInventory(old.copy(quantity = old.quantity - income.productQuantity * direction, updatedAt = System.currentTimeMillis()))
    }
}
