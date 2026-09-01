package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.maliar.pro.database.CarServiceItem
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper

/** Presets from the common maintenance items list, purely to speed up data entry - the
 *  name field underneath stays freely editable so any custom item works too. */
private val COMMON_SERVICE_NAMES = arrayOf(
    "روغن موتور", "فیلتر روغن", "فیلتر هوا", "فیلتر کابین", "فیلتر بنزین", "شمع",
    "لنت ترمز", "دیسک ترمز", "لاستیک", "باتری", "روغن گیربکس", "تسمه تایم",
    "تسمه دینام", "ضدیخ", "برف‌پاک‌کن", "کولر", "سرویس دوره‌ای", "بیمه", "سایر / سفارشی"
)

class AddCarServiceItemDialog(
    private val context: Context,
    private val carId: Long,
    private val currentOdometerKm: Int,
    private val existing: CarServiceItem? = null,
    private val onSave: (CarServiceItem) -> Unit
) {
    constructor(context: Context, carId: Long, currentOdometerKm: Int, onSave: (CarServiceItem) -> Unit) :
        this(context, carId, currentOdometerKm, null, onSave)

    fun show() {
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val presetSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, COMMON_SERVICE_NAMES)
        }
        container.addView(presetSpinner)

        val nameInput = EditText(context).apply {
            hint = "نام مورد سرویس"
            gravity = Gravity.RIGHT
            setText(existing?.name.orEmpty())
        }
        container.addView(nameInput)
        presetSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (COMMON_SERVICE_NAMES[position] != "سایر / سفارشی") nameInput.setText(COMMON_SERVICE_NAMES[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        val intervalKmInput = EditText(context).apply {
            hint = "هر چند کیلومتر تعویض/سرویس شود (اختیاری)"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(existing?.intervalKm?.toString().orEmpty())
        }
        container.addView(intervalKmInput)

        val intervalMonthsInput = EditText(context).apply {
            hint = "یا هر چند ماه یک‌بار (اختیاری)"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(existing?.intervalDays?.let { (it / 30).toString() }.orEmpty())
        }
        container.addView(intervalMonthsInput)

        val lastKmInput = EditText(context).apply {
            hint = "کیلومتر آخرین سرویس"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText((existing?.lastServiceOdometerKm ?: currentOdometerKm).toString())
        }
        container.addView(lastKmInput)

        var lastDate = existing?.lastServiceDate ?: System.currentTimeMillis()
        val dateLabel = TextView(context).apply {
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(lastDate)
            text = "تاریخ آخرین سرویس: ${PersianCalendarHelper.formatJalali(y, m, d)}"
            setPadding(0, padding, 0, 0)
            setOnClickListener {
                val (cy, cm, cd) = PersianCalendarHelper.gregorianMillisToJalali(lastDate)
                PersianDatePickerDialog(context, cy, cm, cd) { y2, m2, d2 ->
                    lastDate = PersianCalendarHelper.jalaliToGregorianMillis(y2, m2, d2)
                    text = "تاریخ آخرین سرویس: ${PersianCalendarHelper.formatJalali(y2, m2, d2)}"
                }.show()
            }
        }
        container.addView(dateLabel)

        val notesInput = EditText(context).apply {
            hint = "توضیحات (اختیاری)"
            gravity = Gravity.RIGHT
            setText(existing?.notes.orEmpty())
        }
        container.addView(notesInput)

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) "افزودن مورد سرویس" else "ویرایش مورد سرویس")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val intervalKm = intervalKmInput.text.toString().toIntOrNull()
                val intervalMonths = intervalMonthsInput.text.toString().toIntOrNull()
                val item = (existing ?: CarServiceItem(carId = carId, name = name)).copy(
                    name = name,
                    intervalKm = intervalKm,
                    intervalDays = intervalMonths?.times(30),
                    lastServiceOdometerKm = lastKmInput.text.toString().toIntOrNull(),
                    lastServiceDate = lastDate,
                    notes = notesInput.text.toString().trim()
                )
                onSave(item)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
