package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One installment/settlement payment recorded against a [Debtor]'s balance - this is the
 *  "تاریخچه تراکنش‌ها" (transaction history) shown on the debtor detail screen. */
@Entity(
    tableName = "debtor_payments",
    foreignKeys = [
        ForeignKey(
            entity = Debtor::class,
            parentColumns = ["id"],
            childColumns = ["debtorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("debtorId")]
)
data class DebtorPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val debtorId: Long,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Account this payment moves money into (THEY_OWE_ME - the person paid the user
     *  back) or out of (I_OWE_THEM - the user paid them back). Null keeps the old
     *  behavior of not touching any account balance - see DebtorManager.addPayment. */
    val accountId: Long? = null
)
