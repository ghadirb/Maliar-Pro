package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class BankAccountManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).bankAccountDao()

    fun getAllBankAccounts(): Flow<List<BankAccount>> = dao.getAllBankAccounts()

    /** Creates the account if it's new, otherwise just updates its balance. */
    suspend fun upsertBalance(bankName: String, lastDigits: String, balance: Double) {
        val existing = dao.findAccount(bankName, lastDigits)
        if (existing == null) {
            dao.upsert(BankAccount(bankName = bankName, lastDigits = lastDigits, balance = balance))
        } else {
            dao.update(existing.copy(balance = balance, updatedAt = System.currentTimeMillis()))
        }
    }

    /** True if this exact SMS was already processed before (guards against duplicate broadcasts). */
    suspend fun isSmsAlreadyProcessed(smsId: String): Boolean = dao.findProcessedSms(smsId) != null

    suspend fun markSmsProcessed(smsId: String) {
        dao.markSmsProcessed(ProcessedSms(id = smsId))
    }
}
