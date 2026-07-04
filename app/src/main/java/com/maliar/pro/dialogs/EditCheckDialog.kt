package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import com.maliar.pro.database.Check
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.AccountingViewModel

class EditCheckDialog(private val context: Context, private val viewModel: AccountingViewModel, private val check: Check) {

    private var selectedDueDate: Long = check.dueDate
    private val initialJalali = PersianCalendarHelper.gregorianMillisToJalali(check.dueDate)
    private var selectedJalaliYear: Int = initialJalali.first
    private var selectedJalaliMonth: Int = initialJalali.second
    private var selectedJalaliDay: Int = initialJalali.third

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش چک")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(com.maliar.pro.R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(com.maliar.pro.R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(com.maliar.pro.R.id.dueDateButton)
        val checkTypeRadioGroup = view.findViewById<RadioGroup>(com.maliar.pro.R.id.checkTypeRadioGroup)

        checkNumberInput.setText(check.checkNumber)
        amountInput.setText(check.amount.toString())
        payeeInput.setText(check.recipient)
        checkTypeRadioGroup.check(
            if (check.isReceived) com.maliar.pro.R.id.receivedRadioButton
            else com.maliar.pro.R.id.paidRadioButton
        )

        fun refreshDateButtonText() {
            dueDateButton.text = PersianCalendarHelper.formatJalali(
                selectedJalaliYear, selectedJalaliMonth, selectedJalaliDay
            )
        }
        refreshDateButtonText()

        dueDateButton.setOnClickListener {
            PersianDatePickerDialog(
                context,
                initialYear = selectedJalaliYear,
                initialMonth = selectedJalaliMonth,
                initialDay = selectedJalaliDay
            ) { year, month, day ->
                selectedJalaliYear = year
                selectedJalaliMonth = month
                selectedJalaliDay = day
                selectedDueDate = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
                refreshDateButtonText()
            }.show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val checkNumber = checkNumberInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val payee = payeeInput.text.toString()
            val isReceived = checkTypeRadioGroup.checkedRadioButtonId == com.maliar.pro.R.id.receivedRadioButton

            if (checkNumber.isNotBlank() && amount > 0) {
                val updatedCheck = check.copy(
                    checkNumber = checkNumber,
                    amount = amount,
                    recipient = payee,
                    dueDate = selectedDueDate,
                    isReceived = isReceived
                )
                viewModel.updateCheck(updatedCheck)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
