package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText

class AddOdometerDialog(
    private val context: Context,
    private val currentKm: Int,
    private val onSave: (Int) -> Unit
) {
    fun show() {
        val edit = EditText(context).apply {
            hint = "کیلومتر جدید"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.RIGHT
            setText(currentKm.toString())
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        AlertDialog.Builder(context)
            .setTitle("ثبت کیلومتر جدید")
            .setMessage("آخرین کیلومتر ثبت‌شده: ${String.format("%,d", currentKm)}")
            .setView(edit)
            .setPositiveButton("ذخیره") { _, _ ->
                val km = edit.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (km >= currentKm) onSave(km)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
