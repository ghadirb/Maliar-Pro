package com.maliar.pro.utils

import com.maliar.pro.database.BusinessTransaction
import com.maliar.pro.database.BusinessTransactionType
import com.maliar.pro.database.Income
import com.maliar.pro.database.ProductInventory
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessDashboardCalculatorTest {
    @Test fun usesOnlyRecordedProductSalesAndCurrentInventory() {
        val result = BusinessDashboardCalculator.calculate(
            incomes = listOf(
                Income(amount = 150.0, description = "sale", date = 200, isProductSale = true, costOfGoods = 80.0),
                Income(amount = 90.0, description = "service", date = 200),
                Income(amount = 120.0, description = "old sale", date = 50, isProductSale = true, costOfGoods = 50.0)
            ),
            inventory = listOf(ProductInventory("A", 2.0, 40.0), ProductInventory("B", -1.0, 70.0)),
            transactions = listOf(
                BusinessTransaction(id = 1, type = BusinessTransactionType.PRODUCT_PURCHASE, amount = 80.0, date = 190),
                BusinessTransaction(id = 2, type = BusinessTransactionType.TRANSFER, amount = 10.0, date = 210)
            ), periodStart = 100, todayStart = 180
        )
        assertEquals(150.0, result.todayProductSales, 0.0)
        assertEquals(150.0, result.periodProductSales, 0.0)
        assertEquals(70.0, result.periodGrossProfit, 0.0)
        assertEquals(1, result.periodSaleCount)
        assertEquals(80.0, result.inventoryCostValue, 0.0)
        assertEquals(1, result.outOfStockCount)
        assertEquals(listOf(1L), result.recentPurchases.map { it.id })
    }
}
