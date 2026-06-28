package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: AssetType,
    val title: String,
    val value: Double,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AssetType {
    CASH,
    BANK_ACCOUNT,
    DEPOSIT,
    GOLD,
    CRYPTO,
    STOCK,
    VEHICLE,
    REAL_ESTATE,
    OTHER
}
