package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.R
import com.maliar.pro.database.Check
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class AddCheckDialog(private val context: Context, private val viewModel: AccountingViewModel) {

    private var selectedDueDate: Long = System.currentTimeMillis()
    private var isReceivable: Boolean = true // New: receivable or payable

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن چک")

        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(R.id.dueDateButton)
        // Add receivable/payable toggle if in layout

        dueDateButton.text = PersianCalendarHelper.toJalaliFromMillis(selectedDueDate)

        dueDateButton.setOnClickListener {
            // Simple Jalali input dialog or use library. For now, use date picker with Persian conversion
            showJalaliDatePicker(dueDateButton)
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            // ... save with type receivable/payable
            val check = Check( /* ... isReceivable */ )
            viewModel.addCheck(check)
        }
        builder.show()
    }

    private fun showJalaliDatePicker(button: Button) {
        // Implement custom dialog for year/month/day Jalali input
        // Or integrate PersianDatePicker library
        AlertDialog.Builder(context)
            .setTitle("تاریخ شمسی")
            .setMessage("سال/ماه/روز شمسی وارد کنید (مثال: 1403/5/15)")
            .setView(EditText(context).apply { hint = "1403/5/15" })
            .setPositiveButton("تایید") { _, _ -> 
                // Parse and set
            }
            .show()
    }
}