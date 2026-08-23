package com.maliar.pro.models

/** A single upcoming financial obligation/expectation - a check, an installment
 *  payment, or a debtor/creditor due date - normalized so the "نزدیک به تاریخ" widget on
 *  the accounting tab and the full due-list screen can show them together, sorted by
 *  actual due date, without caring which table each came from. */
data class DueItem(
    val id: Long,
    val type: DueItemType,
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val subtitle: String = ""
) {
    val daysLeft: Int
        get() {
            val diff = dueDate - System.currentTimeMillis()
            return (diff / (24 * 60 * 60 * 1000)).toInt()
        }

    val isOverdue: Boolean get() = daysLeft < 0
}

enum class DueItemType {
    CHECK,
    INSTALLMENT,
    DEBTOR_THEY_OWE_ME,
    DEBTOR_I_OWE_THEM
}
