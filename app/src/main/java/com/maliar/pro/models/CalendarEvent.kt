package com.maliar.pro.models

/** One entry shown on a given day of the financial calendar (تقویم مالی) - an installment
 *  payment, a check due date, a debt/debtor due date, a reminder, etc. Grouped by Jalali
 *  day-of-month by [com.maliar.pro.viewmodels.FinancialCalendarViewModel]. */
data class CalendarEvent(
    val title: String,
    val subtitle: String,
    val amount: Double?,
    val type: CalendarEventType,
    val dueDate: Long
)

enum class CalendarEventType {
    INSTALLMENT, CHECK, DEBT, DEBTOR, INCOME, REMINDER
}
