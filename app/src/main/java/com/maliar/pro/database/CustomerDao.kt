package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC, name COLLATE NOCASE")
    fun getAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer): Long

    @Update suspend fun update(customer: Customer)
    @Delete suspend fun delete(customer: Customer)

    @Query("SELECT * FROM customer_ledger_entries WHERE customerId = :customerId ORDER BY date DESC, id DESC")
    fun getEntries(customerId: Long): Flow<List<CustomerLedgerEntry>>

    @Query("SELECT * FROM customer_ledger_entries WHERE customerId = :customerId ORDER BY date DESC, id DESC")
    suspend fun getEntriesList(customerId: Long): List<CustomerLedgerEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CustomerLedgerEntry): Long

    @Delete suspend fun deleteEntry(entry: CustomerLedgerEntry)
}
