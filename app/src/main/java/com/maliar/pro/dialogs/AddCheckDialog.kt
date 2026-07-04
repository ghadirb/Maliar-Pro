package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import com.maliar.pro.R
import com.maliar.pro.database.Check
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class AddCheckDialog(private val context: Context, private val viewModel: AccountingViewModel) {

    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    private var selectedJalaliYear: Int = today.first
    private var selectedJalaliMonth: Int = today.second
    private var selectedJalaliDay: Int = today.third
    private var selectedDueDate: Long = PersianCalendarHelper.jalaliToGregorianMillis(
        selectedJalaliYear, selectedJalaliMonth, selectedJalaliDay
    )

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن چک")

        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(R.id.dueDateButton)
        val checkTypeRadioGroup = view.findViewById<RadioGroup>(R.id.checkTypeRadioGroup)

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
            val isReceived = checkTypeRadioGroup.checkedRadioButtonId == R.id.receivedRadioButton

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
                    isReceived = isReceived,
                    isCashed = false
                )
                viewModel.addCheck(check)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
