package com.maliar.pro.utils

import com.maliar.pro.database.BusinessTransaction
import com.maliar.pro.database.BusinessTransactionType
import com.maliar.pro.database.Income
import com.maliar.pro.database.ProductInventory

/** Read-only business figures derived from the existing sales, purchase and stock ledgers. */
data class BusinessDashboardSummary(
    val todayProductSales: Double,
    val periodProductSales: Double,
    val periodGrossProfit: Double,
    val periodSaleCount: Int,
    val inventoryCostValue: Double,
    val outOfStockCount: Int,
    val recentPurchases: List<BusinessTransaction>,
    val bestSellingProduct: ProductPerformance?,
    val lowestProfitProduct: ProductPerformance?,
    val lowStockProducts: List<ProductInventory>
)

data class ProductPerformance(val name: String, val sales: Double, val profit: Double, val count: Int)

object BusinessDashboardCalculator {
    fun calculate(
        incomes: List<Income>, inventory: List<ProductInventory>, transactions: List<BusinessTransaction>,
        periodStart: Long, todayStart: Long
    ): BusinessDashboardSummary {
        val periodSales = incomes.filter { it.isProductSale && it.date >= periodStart }
        val products = periodSales.filter { it.productName.isNotBlank() }.groupBy { it.productName.trim() }
            .map { (name, rows) -> ProductPerformance(name, rows.sumOf { it.amount }, rows.sumOf { it.profit }, rows.size) }
        return BusinessDashboardSummary(
            todayProductSales = periodSales.filter { it.date >= todayStart }.sumOf { it.amount },
            periodProductSales = periodSales.sumOf { it.amount },
            periodGrossProfit = periodSales.sumOf { it.profit },
            periodSaleCount = periodSales.size,
            inventoryCostValue = inventory.sumOf { it.quantity.coerceAtLeast(0.0) * it.averageUnitCost },
            outOfStockCount = inventory.count { it.quantity <= 0.0 },
            recentPurchases = transactions.filter { it.type == BusinessTransactionType.PRODUCT_PURCHASE }
                .sortedByDescending { it.date }.take(3),
            bestSellingProduct = products.maxByOrNull { it.sales },
            lowestProfitProduct = products.minByOrNull { it.profit },
            lowStockProducts = inventory.filter { it.quantity in 0.0..1.0 }.sortedBy { it.quantity }.take(3)
        )
    }
}
