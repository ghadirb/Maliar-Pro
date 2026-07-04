package com.maliar.pro.utils

import android.icu.util.PersianCalendar

/**
 * Helpers around Android ICU's PersianCalendar (the real Jalali/Shamsi solar calendar,
 * not to be confused with android.icu.util.IslamicCalendar which is the lunar
 * Hijri calendar and was mistakenly used elsewhere in earlier versions of this app).
 */
object PersianCalendarHelper {

    val PERSIAN_MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** Returns (year, month 1-12, day) for the current Persian date. */
    fun getCurrentJalaliDate(): Triple<Int, Int, Int> {
        val cal = PersianCalendar()
        return Triple(
            cal.get(PersianCalendar.YEAR),
            cal.get(PersianCalendar.MONTH) + 1,
            cal.get(PersianCalendar.DAY_OF_MONTH)
        )
    }

    /** Converts a Gregorian epoch millis timestamp to (year, month 1-12, day) Jalali. */
    fun gregorianMillisToJalali(millis: Long): Triple<Int, Int, Int> {
        val cal = PersianCalendar()
        cal.timeInMillis = millis
        return Triple(
            cal.get(PersianCalendar.YEAR),
            cal.get(PersianCalendar.MONTH) + 1,
            cal.get(PersianCalendar.DAY_OF_MONTH)
        )
    }

    /** Converts a Jalali (year, month 1-12, day) date to Gregorian epoch millis. */
    fun jalaliToGregorianMillis(year: Int, month: Int, day: Int): Long {
        val cal = PersianCalendar()
        cal.clear()
        cal.set(year, month - 1, day)
        return cal.timeInMillis
    }

    /** Number of days in a given Jalali month/year (handles leap years for Esfand). */
    fun daysInJalaliMonth(year: Int, month: Int): Int {
        return when (month) {
            in 1..6 -> 31
            in 7..11 -> 30
            12 -> if (isJalaliLeapYear(year)) 30 else 29
            else -> 30
        }
    }

    fun isJalaliLeapYear(year: Int): Boolean {
        // 33-year cycle approximation for Jalali leap years
        val remainders = intArrayOf(1, 5, 9, 13, 17, 22, 26, 30)
        return (year % 33) in remainders
    }

    /** Formats a Jalali date for display, e.g. "۱۵ مرداد ۱۴۰۳". */
    fun formatJalali(year: Int, month: Int, day: Int): String {
        val monthName = PERSIAN_MONTH_NAMES.getOrElse(month - 1) { "" }
        return "$day $monthName $year"
    }

    /**
     * @deprecated kept only for backward-compatibility with any old call sites;
     * prefer showing [com.maliar.pro.ui.common.PersianDatePickerDialog] directly
     * so the user gets the real scrollable day/month/year picker.
     */
    @Deprecated("Use PersianDatePickerDialog for a real scrollable UI")
    fun showPersianDatePicker(
        context: android.content.Context,
        onDateSelected: (year: Int, month: Int, day: Int) -> Unit
    ) {
        com.maliar.pro.ui.common.PersianDatePickerDialog(context) { y, m, d ->
            onDateSelected(y, m, d)
        }.show()
    }
}
