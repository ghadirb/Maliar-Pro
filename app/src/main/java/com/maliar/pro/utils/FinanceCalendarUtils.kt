package com.maliar.pro.utils

import com.maliar.pro.database.Installment
import java.util.Calendar

/** Shared helpers for turning accounting records (installments, checks, debts, debtors)
 *  into concrete due-date timestamps on the Jalali calendar - used by both the "نزدیک به
 *  تاریخ" widget/list and the full financial calendar screen, so the two always agree on
 *  what counts as "due". */
object FinanceCalendarUtils {

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * The next upcoming payment date (as epoch millis) for an active installment, based on
     * its Jalali [Installment.paymentDay]. If this month's payment day has already passed,
     * rolls over to next month. Returns null if the installment is already fully paid.
     */
    fun nextInstallmentDueDate(installment: Installment): Long? {
        if (installment.paidInstallments >= installment.totalInstallments) return null

        val (y, m, _) = PersianCalendarHelper.getCurrentJalaliDate()
        val today = startOfTodayMillis()

        val thisMonthDay = installment.paymentDay.coerceIn(1, PersianCalendarHelper.daysInJalaliMonth(y, m))
        val thisMonthDue = PersianCalendarHelper.jalaliToGregorianMillis(y, m, thisMonthDay)
        if (thisMonthDue >= today) return thisMonthDue

        val (ny, nm) = if (m == 12) Pair(y + 1, 1) else Pair(y, m + 1)
        val nextMonthDay = installment.paymentDay.coerceIn(1, PersianCalendarHelper.daysInJalaliMonth(ny, nm))
        return PersianCalendarHelper.jalaliToGregorianMillis(ny, nm, nextMonthDay)
    }

    /**
     * All payment-day occurrences of [installment] that fall within the given Jalali
     * (year, month), used by the financial calendar's month grid - unlike
     * [nextInstallmentDueDate], this doesn't care whether the date is in the past or
     * future, only whether the installment was still active for that occurrence.
     */
    fun installmentOccurrenceInMonth(installment: Installment, year: Int, month: Int): Long? {
        if (installment.paidInstallments >= installment.totalInstallments) return null
        val day = installment.paymentDay.coerceIn(1, PersianCalendarHelper.daysInJalaliMonth(year, month))
        val occurrence = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
        return if (occurrence >= installment.startDate) occurrence else null
    }
}
