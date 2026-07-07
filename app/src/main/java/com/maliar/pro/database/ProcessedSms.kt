package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tiny dedup guard: the same SMS broadcast can occasionally be delivered more than
 * once by Android on some devices, and this stops it from being recorded as two separate
 * transactions. [id] is a hash of sender+body+timestamp, not the SMS content itself.
 */
@Entity(tableName = "processed_sms")
data class ProcessedSms(
    @PrimaryKey
    val id: String,
    val processedAt: Long = System.currentTimeMillis()
)
