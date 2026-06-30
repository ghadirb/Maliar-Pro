package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.database.Check
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class EditCheckDialog(private val context: Context, private val viewModel: AccountingViewModel, private val check: Check) {

    private var selectedDueDate: Long = check.dueDate

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش چک")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(com.maliar.pro.R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(com.maliar.pro.R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(com.maliar.pro.R.id.dueDateButton)

        checkNumberInput.setText(check.checkNumber)
        amountInput.setText(check.amount.toString())
        payeeInput.setText(check.recipient)

        val persianCalendar = IslamicCalendar.getInstance(ULocale.forLanguageTag("fa_IR"))
        persianCalendar.timeInMillis = check.dueDate
        dueDateButton.text = "${persianCalendar.get(IslamicCalendar.DAY_OF_MONTH)}/${persianCalendar.get(IslamicCalendar.MONTH) + 1}/${persianCalendar.get(IslamicCalendar.YEAR)}"

        dueDateButton.setOnClickListener {
            android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    persianCalendar.set(year, month, day)
                    selectedDueDate = persianCalendar.timeInMillis
                    dueDateButton.text = "$day/${month + 1}/$year"
                },
                persianCalendar.get(IslamicCalendar.YEAR),
                persianCalendar.get(IslamicCalendar.MONTH),
                persianCalendar.get(IslamicCalendar.DAY_OF_MONTH)
            ).show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val checkNumber = checkNumberInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val payee = payeeInput.text.toString()

            if (checkNumber.isNotBlank() && amount > 0) {
                val updatedCheck = check.copy(
                    checkNumber = checkNumber,
                    amount = amount,
                    recipient = payee,
                    dueDate = selectedDueDate
                )
                viewModel.updateCheck(updatedCheck)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
