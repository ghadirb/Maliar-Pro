package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import com.maliar.pro.database.CarLogCategory

private val CATEGORY_LABELS = linkedMapOf(
    CarLogCategory.SERVICE to "سرویس",
    CarLogCategory.REPAIR to "تعمیر",
    CarLogCategory.PART to "قطعه مصرفی",
    CarLogCategory.OTHER to "سایر (بیمه، جریمه، ...)"
)

/** "ثبت هزینه خودرو" for a cost that isn't tied to any tracked service schedule - a
 *  repair, a spare part bought on its own, insurance, a fine, etc (spec item #8). */
class AddCarExpenseDialog(
    private val context: Context,
    private val currentOdometerKm: Int,
    private val onSave: (title: String, category: CarLogCategory, odometerKm: Int?, cost: Double, notes: String, linkToFinance: Boolean) -> Unit
) {
    fun show() {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val titleInput = EditText(context).apply {
            hint = "عنوان (مثلاً: تعویض دیسک ترمز)"
            gravity = Gravity.RIGHT
        }
        container.addView(titleInput)

        val categoryLabels = CATEGORY_LABELS.values.toTypedArray()
        val categorySpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categoryLabels)
        }
        container.addView(categorySpinner)

        val kmInput = EditText(context).apply {
            hint = "کیلومتر (اختیاری)"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(currentOdometerKm.toString())
        }
        container.addView(kmInput)

        val costInput = EditText(context).apply {
            hint = "هزینه (تومان)"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
        }
        container.addView(costInput)

        val notesInput = EditText(context).apply {
            hint = "توضیحات (اختیاری)"
            gravity = Gravity.RIGHT
        }
        container.addView(notesInput)

        val linkCheckbox = CheckBox(context).apply {
            text = "این هزینه در بخش هزینه‌های مالی (دسته «خودرو») هم ثبت شود"
            isChecked = true
        }
        container.addView(linkCheckbox)

        AlertDialog.Builder(context)
            .setTitle("ثبت هزینه خودرو")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) return@setPositiveButton
                val category = CATEGORY_LABELS.keys.toList()[categorySpinner.selectedItemPosition]
                onSave(
                    title,
                    category,
                    kmInput.text.toString().toIntOrNull(),
                    costInput.text.toString().toDoubleOrNull() ?: 0.0,
                    notesInput.text.toString().trim(),
                    linkCheckbox.isChecked
                )
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
