package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodPriceDao {
    @Query("SELECT * FROM user_food_prices ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<UserFoodPrice>>

    @Query("SELECT * FROM user_food_prices WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): UserFoodPrice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(price: UserFoodPrice): Long

    @Delete
    suspend fun delete(price: UserFoodPrice)
}
