package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DebtorManager(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.debtorDao()

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

    /** Records a payment and, if it fully settles the balance, marks the debtor as settled. */
    suspend fun addPayment(payment: DebtorPayment) {
        dao.insertPayment(payment)
        val debtor = dao.getDebtorById(payment.debtorId) ?: return
        val totalPaid = dao.getTotalPaidForDebtor(payment.debtorId)
        if (totalPaid >= debtor.amount && !debtor.isSettled) {
            dao.updateDebtor(debtor.copy(isSettled = true, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deletePayment(payment: DebtorPayment) = dao.deletePayment(payment)
}
