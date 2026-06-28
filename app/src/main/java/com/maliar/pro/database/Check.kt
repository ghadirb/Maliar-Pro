package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checks")
data class Check(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val checkNumber: String,
    val issuer: String,
    val recipient: String,
    val bankName: String,
    val accountNumber: String,
    val issueDate: Long,
    val dueDate: Long,
    val isReceived: Boolean = false,
    val isCashed: Boolean = false,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
