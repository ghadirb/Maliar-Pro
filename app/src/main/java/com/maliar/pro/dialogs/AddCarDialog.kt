package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import com.maliar.pro.database.Car

/** Add/edit a car. Built as plain EditTexts in code (no separate layout XML) since it's a
 *  short, one-off form - matches the pragmatic style of the simplest existing dialogs while
 *  avoiding an extra layout file for six fields. */
class AddCarDialog(
    private val context: Context,
    private val existing: Car? = null,
    private val onSave: (Car) -> Unit
) {

    constructor(context: Context, onSave: (Car) -> Unit) : this(context, null, onSave)

    fun show() {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        fun field(hint: String, value: String = "", inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val edit = EditText(context)
            edit.hint = hint
            edit.setText(value)
            edit.inputType = inputType
            edit.gravity = Gravity.RIGHT
            container.addView(edit)
            return edit
        }

        val nameInput = field("نام خودرو (مثلاً: پژو ۲۰۶ خودم)", existing?.name.orEmpty())
        val brandInput = field("برند", existing?.brand.orEmpty())
        val modelInput = field("مدل", existing?.model.orEmpty())
        val yearInput = field("سال (اختیاری)", existing?.year?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val plateInput = field("پلاک (اختیاری)", existing?.plate.orEmpty())
        val kmInput = field("کیلومتر فعلی", existing?.currentOdometerKm?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val notesInput = field("توضیحات (اختیاری)", existing?.notes.orEmpty())

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) "افزودن خودرو" else "ویرایش خودرو")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val car = (existing ?: Car(name = name)).copy(
                    name = name,
                    brand = brandInput.text.toString().trim(),
                    model = modelInput.text.toString().trim(),
                    year = yearInput.text.toString().toIntOrNull(),
                    plate = plateInput.text.toString().trim(),
                    currentOdometerKm = kmInput.text.toString().toIntOrNull() ?: (existing?.currentOdometerKm ?: 0),
                    notes = notesInput.text.toString().trim()
                )
                onSave(car)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
