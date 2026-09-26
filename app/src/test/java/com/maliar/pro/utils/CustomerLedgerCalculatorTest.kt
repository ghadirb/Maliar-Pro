package com.maliar.pro.utils

import com.maliar.pro.database.CustomerLedgerEntry
import com.maliar.pro.database.CustomerLedgerType
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerLedgerCalculatorTest {
    @Test fun partialSettlementAndAdjustmentKeepCorrectBalance() {
        val totals = CustomerLedgerCalculator.totals(listOf(
            CustomerLedgerEntry(customerId = 1, type = CustomerLedgerType.CREDIT_SALE, amount = 3_000_000.0),
            CustomerLedgerEntry(customerId = 1, type = CustomerLedgerType.PAYMENT, amount = 1_000_000.0),
            CustomerLedgerEntry(customerId = 1, type = CustomerLedgerType.ADJUSTMENT, amount = -200_000.0)
        ))
        assertEquals(3_000_000.0, totals.sales, 0.0)
        assertEquals(1_000_000.0, totals.payments, 0.0)
        assertEquals(1_800_000.0, totals.balance, 0.0)
    }
}
