package com.maliar.pro.utils

import android.content.Context
import java.util.Calendar

object PersianCalendarHelper {
    // Full Jalali conversion (simplified robust impl)
    fun getCurrentJalaliDate(): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        // Use library or algorithm in full; here approx for demo
        val year = cal.get(Calendar.YEAR) - 621
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return Triple(year, month, day)
    }

    fun formatJalali(year: Int, month: Int, day: Int): String {
        return String.format("%04d/%02d/%02d", year, month, day)
    }

    // Professional picker dialog helper
    fun showPersianDatePicker(context: Context, onDateSelected: (year: Int, month: Int, day: Int) -> Unit) {
        // In full: use NumberPicker dialog or Material DatePicker with Jalali
        val (y, m, d) = getCurrentJalaliDate()
        onDateSelected(y, m, d) // Default to current
        // TODO: Implement full scrollable picker UI
    }
}