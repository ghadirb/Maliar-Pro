package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_preferences")
data class FinancialPreferences(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val riskTolerance: RiskTolerance,
    val investmentInterest: Boolean,
    val savingsInterest: Boolean,
    val purchasePreference: PurchasePreference,
    val householdSize: Int? = null,
    val dependents: Int? = null,
    val maritalStatus: MaritalStatus? = null,
    val aiAccessAccounting: Boolean = true,
    val aiUseAssets: Boolean = true,
    val aiUseGoals: Boolean = true,
    val aiShowInvestmentSuggestions: Boolean = true,
    val aiShowSavingSuggestions: Boolean = true,
    val aiShowPurchaseSuggestions: Boolean = true,
    val aiShowMealSuggestions: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RiskTolerance {
    LOW,
    MEDIUM,
    HIGH
}

enum class PurchasePreference {
    CASH,
    INSTALLMENT
}

enum class MaritalStatus {
    SINGLE,
    MARRIED,
    DIVORCED,
    WIDOWED
}
