package com.maliar.pro.utils

import com.maliar.pro.database.CustomerLedgerEntry
import com.maliar.pro.database.CustomerLedgerType

/** Pure balance arithmetic for customer-ledger tests and UI summaries. */
object CustomerLedgerCalculator {
    data class Totals(val sales: Double, val payments: Double, val adjustments: Double) {
        val balance: Double get() = sales + adjustments - payments
    }
    fun totals(entries: List<CustomerLedgerEntry>): Totals = Totals(
        sales = entries.filter { it.type == CustomerLedgerType.CREDIT_SALE }.sumOf { it.amount },
        payments = entries.filter { it.type == CustomerLedgerType.PAYMENT }.sumOf { it.amount },
        adjustments = entries.filter { it.type == CustomerLedgerType.ADJUSTMENT }.sumOf { it.amount }
    )
}
