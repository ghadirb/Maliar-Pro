package com.maliar.pro.utils

import java.util.Calendar

/**
 * Pure-Kotlin Jalali (Persian solar / Shamsi) <-> Gregorian date conversion.
 *
 * IMPORTANT: android.icu.util (Android's built-in, stripped-down subset of ICU) does NOT
 * include PersianCalendar - that class only exists in the full ICU4J library, not in the
 * Android SDK. Referencing android.icu.util.PersianCalendar fails to compile ("Unresolved
 * reference"). android.icu.util.IslamicCalendar (which earlier versions of this app used
 * by mistake) does exist, but it's the lunar Hijri calendar, not the solar Jalali calendar
 * - completely different date system. So we implement the well-known, widely used
 * gregorian_to_jalali / jalali_to_gregorian algorithm (the same algorithm used by the
 * popular "jdf.php" library and its many ports) directly, with no external dependency.
 *
 * Verified by hand round-trip: 1403/1/1 <-> 2024-03-20 and back.
 */
object PersianCalendarHelper {

    val PERSIAN_MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val G_DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val J_DAYS_IN_MONTH = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    /** Converts a Gregorian (year, month 1-12, day) date to Jalali (year, month 1-12, day). */
    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): Triple<Int, Int, Int> {
        val gy = gYear - 1600
        val gm = gMonth - 1
        val gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) gDayNo += G_DAYS_IN_MONTH[i]
        if (gm > 1 && isGregorianLeap(gYear)) gDayNo += 1
        gDayNo += gd

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var i = 0
        while (i < 11 && jDayNo >= J_DAYS_IN_MONTH[i]) {
            jDayNo -= J_DAYS_IN_MONTH[i]
            i += 1
        }
        val jm = i + 1
        val jd = jDayNo + 1
        return Triple(jy, jm, jd)
    }

    /** Converts a Jalali (year, month 1-12, day) date to Gregorian (year, month 1-12, day). */
    fun jalaliToGregorian(jYear: Int, jMonth: Int, jDay: Int): Triple<Int, Int, Int> {
        val jy = jYear + 1595
        var days = -355668 + (365 * jy) + (jy / 33) * 8 + (((jy % 33) + 3) / 4) + jDay +
            if (jMonth < 7) (jMonth - 1) * 31 else ((jMonth - 7) * 30) + 186

        var gy = 400 * (days / 146097)
        days %= 146097

        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }

        gy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }

        var gd = days + 1
        val kab = if (isGregorianLeap(gy)) 29 else 28
        val salA = intArrayOf(0, 31, kab, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm += 1
        }
        return Triple(gy, gm, gd)
    }

    /** Returns (year, month 1-12, day) for the current Persian date (device local time). */
    fun getCurrentJalaliDate(): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Start (epoch millis, local midnight) of the current "financial month" - normally the
     * 1st of the current Jalali month, but if [periodStartDay] is e.g. 15 (for someone
     * paid on the 15th), the period instead runs 15th-to-14th: still inside the *previous*
     * Jalali month's cycle until the day-of-month catches up to 15 again. Used by
     * AccountingManager's "این ماه" totals and FinancialReportManager's MONTHLY report so
     * both agree with whatever the person configured in Settings -> شروع دوره مالی.
     */
    fun currentFinancialPeriodStartMillis(periodStartDay: Int = 1): Long {
        val (y, m, d) = getCurrentJalaliDate()
        if (periodStartDay <= 1) return jalaliToGregorianMillis(y, m, 1)

        val startDayThisMonth = periodStartDay.coerceAtMost(daysInJalaliMonth(y, m))
        return if (d >= startDayThisMonth) {
            jalaliToGregorianMillis(y, m, startDayThisMonth)
        } else {
            val (py, pm) = if (m == 1) (y - 1) to 12 else y to (m - 1)
            val startDayPrevMonth = periodStartDay.coerceAtMost(daysInJalaliMonth(py, pm))
            jalaliToGregorianMillis(py, pm, startDayPrevMonth)
        }
    }

    /**
     * Same idea as [currentFinancialPeriodStartMillis], but lets the caller step back through
     * *previous* financial periods instead of only ever the current one - powers the
     * "previous/next period" navigator in the reports screen. [offsetPeriods] = 0 is the
     * current period, 1 is one period back, 2 is two periods back, etc.
     */
    fun financialPeriodStartMillisForOffset(periodStartDay: Int = 1, offsetPeriods: Int = 0): Long {
        val (y, m, d) = getCurrentJalaliDate()
        var py: Int
        var pm: Int
        if (periodStartDay <= 1) {
            py = y; pm = m
        } else {
            val startDayThisMonth = periodStartDay.coerceAtMost(daysInJalaliMonth(y, m))
            if (d >= startDayThisMonth) {
                py = y; pm = m
            } else {
                if (m == 1) { py = y - 1; pm = 12 } else { py = y; pm = m - 1 }
            }
        }
        repeat(offsetPeriods) {
            if (pm == 1) { pm = 12; py -= 1 } else { pm -= 1 }
        }
        val startDay = periodStartDay.coerceAtMost(daysInJalaliMonth(py, pm))
        return jalaliToGregorianMillis(py, pm, startDay)
    }

    /** Converts a Gregorian epoch millis timestamp to (year, month 1-12, day) Jalali. */
    fun gregorianMillisToJalali(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Converts a Jalali (year, month 1-12, day) date to Gregorian epoch millis (local midnight). */
    fun jalaliToGregorianMillis(year: Int, month: Int, day: Int): Long {
        val (gy, gm, gd) = jalaliToGregorian(year, month, day)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(gy, gm - 1, gd)
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

    /** Whether the given Jalali year is a leap year (i.e. Esfand has 30 days). */
    fun isJalaliLeapYear(year: Int): Boolean {
        // A year is leap iff Esfand 30 rolls over to the same Jalali year when
        // converted through Gregorian and back - this is exact, not an approximation.
        val (gy, gm, gd) = jalaliToGregorian(year, 12, 30)
        val (checkYear, _, _) = gregorianToJalali(gy, gm, gd)
        return checkYear == year
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
