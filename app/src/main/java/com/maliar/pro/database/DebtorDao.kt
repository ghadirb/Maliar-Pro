package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtorDao {

    @Query("SELECT * FROM debtors ORDER BY isSettled ASC, dueDate ASC")
    fun getAllDebtors(): Flow<List<Debtor>>

    @Query("SELECT * FROM debtors WHERE id = :id")
    suspend fun getDebtorById(id: Long): Debtor?

    @Query("SELECT * FROM debtors WHERE id = :id")
    fun getDebtorByIdFlow(id: Long): Flow<Debtor?>

    @Query("SELECT * FROM debtors WHERE isSettled = 0 AND dueDate IS NOT NULL ORDER BY dueDate ASC")
    suspend fun getUpcomingDebtors(): List<Debtor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtor(debtor: Debtor): Long

    @Update
    suspend fun updateDebtor(debtor: Debtor)

    @Delete
    suspend fun deleteDebtor(debtor: Debtor)

    @Query("DELETE FROM debtors WHERE id = :id")
    suspend fun deleteDebtorById(id: Long)

    // Payments
    @Query("SELECT * FROM debtor_payments WHERE debtorId = :debtorId ORDER BY date DESC")
    fun getPaymentsForDebtor(debtorId: Long): Flow<List<DebtorPayment>>

    @Query("SELECT * FROM debtor_payments WHERE debtorId = :debtorId ORDER BY date DESC")
    suspend fun getPaymentsForDebtorList(debtorId: Long): List<DebtorPayment>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debtor_payments WHERE debtorId = :debtorId")
    suspend fun getTotalPaidForDebtor(debtorId: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debtor_payments WHERE debtorId = :debtorId")
    fun getTotalPaidForDebtorFlow(debtorId: Long): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: DebtorPayment): Long

    @Delete
    suspend fun deletePayment(payment: DebtorPayment)
}
