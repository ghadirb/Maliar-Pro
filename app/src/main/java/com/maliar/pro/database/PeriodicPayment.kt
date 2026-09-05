package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-entered future commitment. This intentionally has no external bill-provider
 * integration: all payment details are entered and confirmed by the person.
 */
@Entity(tableName = "periodic_payments")
data class PeriodicPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val accountId: Long? = null,
    /** Simple, transparent interval in days: 7, 30, 90 or 365 in the current UI. */
    val periodDays: Int = 30,
    val nextPaymentAt: Long,
    val category: String = "عمومی",
    val isActive: Boolean = true,
    val notes: String = "",
    val reminderDaysBefore: Int = 1,
    val reminderId: Long? = null,
    /** Scheduled occurrence that was last marked paid, used to prevent duplicate records. */
    val lastPaidOccurrenceAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
