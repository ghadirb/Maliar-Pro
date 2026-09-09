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
    val updatedAt: Long = System.currentTimeMillis(),
    /** Grams of gold this asset represents, if the user entered a quantity instead of a
     *  fixed price (only meaningful when [type] is [AssetType.GOLD]). When set, [value] is
     *  kept in sync with the live per-gram rate (see [com.maliar.pro.database.FinancialStatusManager.refreshGoldAssetValues])
     *  rather than being a fixed number the user has to update by hand; when null, [value]
     *  behaves exactly as before (a plain fixed amount the user typed in). */
    val goldGrams: Double? = null,
    val purpose: AccountPurpose = AccountPurpose.NORMAL,
    val dailyLimit: Double? = null
)

enum class AccountPurpose {
    NORMAL,
    DAILY_SPENDING,
    SAVINGS,
    EMERGENCY
}

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
