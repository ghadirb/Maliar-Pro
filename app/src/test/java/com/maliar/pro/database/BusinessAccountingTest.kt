package com.maliar.pro.database

import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessAccountingTest {
    @Test fun `service income is entirely profit`() {
        assertEquals(150_000.0, Income(amount = 150_000.0, description = "خدمت", date = 0).profit, 0.0)
    }

    @Test fun `product sale profit excludes cost of goods and discount stays auditable`() {
        val sale = Income(amount = 140_000.0, description = "فروش", date = 0,
            isProductSale = true, costOfGoods = 80_000.0, productName = "کالا",
            productQuantity = 1.0, listPrice = 150_000.0, discountAmount = 10_000.0)
        assertEquals(60_000.0, sale.profit, 0.0)
        assertEquals(10_000.0, sale.listPrice - sale.amount, 0.0)
    }
}
