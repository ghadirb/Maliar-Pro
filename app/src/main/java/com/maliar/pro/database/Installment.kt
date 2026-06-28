package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installments")
data class Installment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val totalAmount: Double,
    val installmentAmount: Double,
    val totalInstallments: Int,
    val paidInstallments: Int = 0,
    val startDate: Long,
    val paymentDay: Int, // Day of Persian month (1-31)
    val recipient: String = "",
    val description: String = "",
    val lastPaymentDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
