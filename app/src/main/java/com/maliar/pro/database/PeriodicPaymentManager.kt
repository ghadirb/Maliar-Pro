package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PeriodicPaymentManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).periodicPaymentDao()

    fun getAll(): Flow<List<PeriodicPayment>> = dao.getAll()

    suspend fun getAllList(): List<PeriodicPayment> = dao.getAllList()

    suspend fun save(payment: PeriodicPayment): Long = dao.insert(payment)

    suspend fun delete(payment: PeriodicPayment) = dao.delete(payment)

    /**
     * Advances exactly one scheduled occurrence. Returning null means the same occurrence
     * was already settled (or the item was removed), so callers must not create a duplicate
     * expense transaction.
     */
    suspend fun markPaid(paymentId: Long): PeriodicPayment? {
        val current = dao.getById(paymentId) ?: return null
        if (!current.isActive || current.lastPaidOccurrenceAt == current.nextPaymentAt) return null
        val updated = current.copy(
            lastPaidOccurrenceAt = current.nextPaymentAt,
            nextPaymentAt = current.nextPaymentAt + current.periodDays.coerceAtLeast(1) * DAY_MILLIS,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        return current
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
