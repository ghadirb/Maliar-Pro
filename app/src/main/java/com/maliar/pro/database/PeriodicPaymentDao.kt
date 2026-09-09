package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodicPaymentDao {
    @Query("SELECT * FROM periodic_payments ORDER BY isActive DESC, nextPaymentAt ASC")
    fun getAll(): Flow<List<PeriodicPayment>>

    @Query("SELECT * FROM periodic_payments ORDER BY isActive DESC, nextPaymentAt ASC")
    suspend fun getAllList(): List<PeriodicPayment>

    @Query("SELECT * FROM periodic_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PeriodicPayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PeriodicPayment): Long

    @Update
    suspend fun update(payment: PeriodicPayment)

    @Delete
    suspend fun delete(payment: PeriodicPayment)
}
