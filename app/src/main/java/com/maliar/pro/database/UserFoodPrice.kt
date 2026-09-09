package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_food_prices",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class UserFoodPrice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val pricePerUnit: Double,
    val unitLabel: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
