package com.maliar.pro.ui.common

import android.app.AlertDialog
import android.content.Context
import android.widget.TimePicker
import com.maliar.pro.R

/**
 * Replaces the platform android.app.TimePickerDialog, whose own internal OK/Cancel button
 * bar was rendering invisible/missing entirely under Theme.MaliarPro.TimePickerDialog on
 * some devices (confirmed via screenshot - the dialog just ends after the clock face,
 * with no button row at all, forcing people to blindly tap where a button *should* be).
 *
 * This uses the exact same android.app.AlertDialog.Builder + .setPositiveButton() /
 * .setNegativeButton() pattern already used - and confirmed working, with clearly visible
 * "لغو"/"ذخیره" buttons - by AddReminderDialog and EditReminderDialog elsewhere in this
 * app, just with a plain TimePicker widget as the dialog's body instead of the platform
 * TimePickerDialog's own internal layout. Full control over the buttons, guaranteed
 * visible.
 */
object MaliarTimePickerDialog {
    fun show(
        context: Context,
        initialHour: Int,
        initialMinute: Int,
        onTimeSet: (hour: Int, minute: Int) -> Unit
    ) {
        val timePicker = TimePicker(context).apply {
            setIs24HourView(true)
            hour = initialHour
            minute = initialMinute
        }

        AlertDialog.Builder(context, R.style.Theme_MaliarPro_TimePickerDialog)
            .setTitle("انتخاب ساعت")
            .setView(timePicker)
            .setPositiveButton("تایید") { _, _ ->
                onTimeSet(timePicker.hour, timePicker.minute)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
