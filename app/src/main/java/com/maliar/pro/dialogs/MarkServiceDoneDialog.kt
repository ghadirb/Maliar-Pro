package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import com.maliar.pro.database.CarServiceItem

/** "انجام شد" flow: confirms the odometer reading and cost for a service that was just
 *  performed, then hands off to CarDetailViewModel.markServiceDone which writes the
 *  history row and rolls the item's schedule forward. */
class MarkServiceDoneDialog(
    private val context: Context,
    private val item: CarServiceItem,
    private val currentOdometerKm: Int,
    private val onConfirm: (odometerKm: Int?, cost: Double, notes: String) -> Unit
) {
    fun show() {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val kmInput = EditText(context).apply {
            hint = "کیلومتر انجام سرویس"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(currentOdometerKm.toString())
        }
        container.addView(kmInput)

        val costInput = EditText(context).apply {
            hint = "هزینه (تومان)"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(if (item.lastCost > 0) item.lastCost.toLong().toString() else "")
        }
        container.addView(costInput)

        val notesInput = EditText(context).apply {
            hint = "توضیحات (اختیاری)"
            gravity = Gravity.RIGHT
        }
        container.addView(notesInput)

        AlertDialog.Builder(context)
            .setTitle("انجام شد: ${item.name}")
            .setView(container)
            .setPositiveButton("ثبت") { _, _ ->
                onConfirm(
                    kmInput.text.toString().toIntOrNull(),
                    costInput.text.toString().toDoubleOrNull() ?: 0.0,
                    notesInput.text.toString().trim()
                )
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
