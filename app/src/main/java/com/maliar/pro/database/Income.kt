package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val date: Long,
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
