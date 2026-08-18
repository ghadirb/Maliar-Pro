package com.maliar.pro.database

/**
 * Compatibility model retained only so the accounting screen can compile.
 * Bank-account/SMS synchronization is disabled in the Play-safe test build.
 */
data class BankAccount(
    val id: Long = 0,
    val bankName: String,
    val lastDigits: String = "",
    val balance: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
