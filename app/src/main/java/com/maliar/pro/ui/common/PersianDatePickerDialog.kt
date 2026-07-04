package com.maliar.pro.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView
import com.maliar.pro.R
import com.maliar.pro.utils.PersianCalendarHelper

/**
 * A professional, fully-scrollable Jalali (Persian solar) date picker — day / month / year
 * NumberPicker wheels, no Gregorian calendar grid involved anywhere. This replaces:
 *  - the old fake AlertDialog+EditText "type your date manually" dialog in AddCheckDialog, and
 *  - the standard android.app.DatePickerDialog (which always renders a Gregorian-structured
 *    grid regardless of which Calendar subclass you feed it) used in Edit dialogs.
 *
 * Defaults to today's Persian date unless an initial date is supplied.
 */
class PersianDatePickerDialog(
    private val context: Context,
    initialYear: Int? = null,
    initialMonth: Int? = null, // 1-12
    initialDay: Int? = null,
    private val onDateSelected: (year: Int, month: Int, day: Int) -> Unit
) {
    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    private var year = initialYear ?: today.first
    private var month = initialMonth ?: today.second
    private var day = initialDay ?: today.third

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_persian_date_picker, null)
        val dayPicker = view.findViewById<NumberPicker>(R.id.dayPicker)
        val monthPicker = view.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = view.findViewById<NumberPicker>(R.id.yearPicker)
        val label = view.findViewById<TextView>(R.id.selectedDateLabel)

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.displayedValues = PersianCalendarHelper.PERSIAN_MONTH_NAMES
        monthPicker.value = month
        monthPicker.wrapSelectorWheel = true

        yearPicker.minValue = today.first - 100
        yearPicker.maxValue = today.first + 20
        yearPicker.value = year
        yearPicker.wrapSelectorWheel = false

        fun refreshDayRange(keepDay: Boolean) {
            val maxDay = PersianCalendarHelper.daysInJalaliMonth(yearPicker.value, monthPicker.value)
            dayPicker.minValue = 1
            dayPicker.maxValue = maxDay
            if (!keepDay || dayPicker.value > maxDay) {
                dayPicker.value = minOf(day, maxDay)
            }
        }

        dayPicker.minValue = 1
        dayPicker.maxValue = PersianCalendarHelper.daysInJalaliMonth(year, month)
        dayPicker.value = day
        dayPicker.wrapSelectorWheel = true

        fun updateLabel() {
            label.text = PersianCalendarHelper.formatJalali(yearPicker.value, monthPicker.value, dayPicker.value)
        }
        updateLabel()

        monthPicker.setOnValueChangedListener { _, _, newVal ->
            month = newVal
            refreshDayRange(keepDay = true)
            updateLabel()
        }
        yearPicker.setOnValueChangedListener { _, _, newVal ->
            year = newVal
            refreshDayRange(keepDay = true)
            updateLabel()
        }
        dayPicker.setOnValueChangedListener { _, _, newVal ->
            day = newVal
            updateLabel()
        }

        AlertDialog.Builder(context)
            .setTitle("انتخاب تاریخ")
            .setView(view)
            .setPositiveButton("تایید") { _, _ ->
                onDateSelected(yearPicker.value, monthPicker.value, dayPicker.value)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
