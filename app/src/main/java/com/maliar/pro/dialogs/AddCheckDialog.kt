package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.R
import com.maliar.pro.database.Check
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class AddCheckDialog(private val context: Context, private val viewModel: AccountingViewModel) {

    private var selectedDueDate: Long = System.currentTimeMillis()

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن چک")

        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(R.id.dueDateButton)

        dueDateButton.text = "۱۴۰۳/۵/۱۵" // Persian default

        dueDateButton.setOnClickListener {
            // Custom Jalali picker placeholder
            AlertDialog.Builder(context)
                .setTitle("تاریخ شمسی")
                .setMessage("سال/ماه/روز (مثال: ۱۴۰۳/۵/۱۵)")
                .setView(EditText(context).apply { setText("۱۴۰۳/۵/۱۵") })
                .setPositiveButton("تایید") { _, _ ->
                    // Parse later
                }
                .show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val checkNumber = checkNumberInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val payee = payeeInput.text.toString()

            if (checkNumber.isNotBlank() && amount > 0) {
                val check = Check(
                    checkNumber = checkNumber,
                    amount = amount,
                    recipient = payee,
                    issuer = "کاربر",
                    bankName = "بانک",
                    accountNumber = "",
                    issueDate = Date().time,
                    dueDate = selectedDueDate,
                    isCashed = false
                )
                viewModel.addCheck(check)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}