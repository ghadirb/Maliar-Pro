package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DebtorManager(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.debtorDao()
    private val financialStatusManager = FinancialStatusManager(appContext)

    fun getAllDebtors(): Flow<List<Debtor>> = dao.getAllDebtors()

    suspend fun getDebtorById(id: Long): Debtor? = dao.getDebtorById(id)

    fun getDebtorByIdFlow(id: Long): Flow<Debtor?> = dao.getDebtorByIdFlow(id)

    suspend fun getUpcomingDebtors(): List<Debtor> = dao.getUpcomingDebtors()

    suspend fun addDebtor(debtor: Debtor): Long = dao.insertDebtor(debtor)

    suspend fun updateDebtor(debtor: Debtor) = dao.updateDebtor(debtor.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteDebtor(debtor: Debtor) = dao.deleteDebtor(debtor)

    fun getPaymentsForDebtor(debtorId: Long): Flow<List<DebtorPayment>> = dao.getPaymentsForDebtor(debtorId)

    suspend fun getPaymentsForDebtorList(debtorId: Long): List<DebtorPayment> = dao.getPaymentsForDebtorList(debtorId)

    suspend fun getTotalPaid(debtorId: Long): Double = dao.getTotalPaidForDebtor(debtorId)

    fun getTotalPaidFlow(debtorId: Long): Flow<Double> = dao.getTotalPaidForDebtorFlow(debtorId)

    /** Records a payment, adjusts the linked account's balance (if any) in the direction
     *  that matches the debtor relationship, and marks the debtor settled once fully
     *  paid. [DebtorDirection.THEY_OWE_ME]: the person is paying the user back, so money
     *  comes IN to the account (+amount). [DebtorDirection.I_OWE_THEM]: the user is
     *  paying them back, so money goes OUT of the account (-amount). No-ops on the
     *  balance side when [DebtorPayment.accountId] is null, exactly like every other
     *  account-linked flow in this app. */
    suspend fun addPayment(payment: DebtorPayment) {
        dao.insertPayment(payment)
        val debtor = dao.getDebtorById(payment.debtorId) ?: return
        if (payment.accountId != null) {
            val delta = if (debtor.direction == DebtorDirection.THEY_OWE_ME) payment.amount else -payment.amount
            financialStatusManager.adjustAssetBalance(payment.accountId, delta)
        }
        val totalPaid = dao.getTotalPaidForDebtor(payment.debtorId)
        if (totalPaid >= debtor.amount && !debtor.isSettled) {
            dao.updateDebtor(debtor.copy(isSettled = true, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Reverses a previously-recorded payment's effect on its linked account (if any),
     *  the mirror image of [addPayment]'s direction logic, then deletes it. */
    suspend fun deletePayment(payment: DebtorPayment) {
        if (payment.accountId != null) {
            val debtor = dao.getDebtorById(payment.debtorId)
            if (debtor != null) {
                val delta = if (debtor.direction == DebtorDirection.THEY_OWE_ME) -payment.amount else payment.amount
                financialStatusManager.adjustAssetBalance(payment.accountId, delta)
            }
        }
        dao.deletePayment(payment)
    }
}
