package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {

    @Query("SELECT * FROM bank_accounts ORDER BY bankName ASC")
    fun getAllBankAccounts(): Flow<List<BankAccount>>

    @Query("SELECT * FROM bank_accounts WHERE bankName = :bankName AND lastDigits = :lastDigits LIMIT 1")
    suspend fun findAccount(bankName: String, lastDigits: String): BankAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: BankAccount): Long

    @Update
    suspend fun update(account: BankAccount)

    @Query("SELECT id FROM processed_sms WHERE id = :id LIMIT 1")
    suspend fun findProcessedSms(id: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSmsProcessed(sms: ProcessedSms)
}
