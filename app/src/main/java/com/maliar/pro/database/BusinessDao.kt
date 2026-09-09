package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Query("SELECT * FROM business_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<BusinessTransaction>>
    @Query("SELECT * FROM business_transactions ORDER BY date DESC")
    suspend fun getAllTransactionsList(): List<BusinessTransaction>
    @Query("SELECT * FROM business_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransaction(id: Long): BusinessTransaction?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransaction(item: BusinessTransaction): Long
    @Update suspend fun updateTransaction(item: BusinessTransaction)
    @Delete suspend fun deleteTransaction(item: BusinessTransaction)

    @Query("SELECT * FROM product_inventory ORDER BY name COLLATE NOCASE")
    fun getInventory(): Flow<List<ProductInventory>>
    @Query("SELECT * FROM product_inventory WHERE name = :name LIMIT 1")
    suspend fun getInventoryItem(name: String): ProductInventory?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInventory(item: ProductInventory)
}
