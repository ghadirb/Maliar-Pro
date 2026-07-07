package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per bank the person has a card/account with, detected from SMS. Keyed by bank
 * name + last 4 digits together (not just bank name) since someone can have more than one
 * card at the same bank.
 */
@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankName: String,
    val lastDigits: String = "",
    val balance: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
