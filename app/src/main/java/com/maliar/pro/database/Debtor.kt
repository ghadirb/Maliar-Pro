package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person the user either owes money to or is owed money by. [DIRECTION.THEY_OWE_ME]
 * means the user lent money out (a "بدهکار" from the user's point of view);
 * [DIRECTION.I_OWE_THEM] means the user borrowed money (a "طلبکار" from the user's point
 * of view). [amount] is always the original principal - the running "مانده" (remaining
 * balance) is derived as amount - sum(DebtorPayment.amount) for this debtorId, so partial
 * payments never need to mutate this row directly.
 */
@Entity(tableName = "debtors")
data class Debtor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String = "",
    val direction: DebtorDirection,
    val amount: Double,
    val dueDate: Long? = null,
    val description: String = "",
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DebtorDirection {
    /** The person owes the user money (user lent it out). */
    THEY_OWE_ME,
    /** The user owes the person money (user borrowed it). */
    I_OWE_THEM
}
