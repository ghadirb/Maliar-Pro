package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.database.Installment
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.AccountingViewModel

class AddInstallmentDialog(private val context: Context, private val viewModel: AccountingViewModel) {

    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    private var selectedJalaliYear: Int = today.first
    private var selectedJalaliMonth: Int = today.second
    private var selectedJalaliDay: Int = today.third
    private var selectedStartDate: Long = PersianCalendarHelper.jalaliToGregorianMillis(
        selectedJalaliYear, selectedJalaliMonth, selectedJalaliDay
    )

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن قسط")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_installment, null)
        val titleInput = view.findViewById<EditText>(com.maliar.pro.R.id.titleInput)
        val totalAmountInput = view.findViewById<EditText>(com.maliar.pro.R.id.totalAmountInput)
        val installmentCountInput = view.findViewById<EditText>(com.maliar.pro.R.id.installmentCountInput)
        val monthlyAmountInput = view.findViewById<EditText>(com.maliar.pro.R.id.monthlyAmountInput)
        val lenderInput = view.findViewById<EditText>(com.maliar.pro.R.id.lenderInput)
        val startDateButton = view.findViewById<Button>(com.maliar.pro.R.id.startDateButton)

        fun refreshDateButtonText() {
            startDateButton.text = PersianCalendarHelper.formatJalali(
                selectedJalaliYear, selectedJalaliMonth, selectedJalaliDay
            )
        }
        refreshDateButtonText()

        startDateButton.setOnClickListener {
            PersianDatePickerDialog(
                context,
                initialYear = selectedJalaliYear,
                initialMonth = selectedJalaliMonth,
                initialDay = selectedJalaliDay
            ) { year, month, day ->
                selectedJalaliYear = year
                selectedJalaliMonth = month
                selectedJalaliDay = day
                selectedStartDate = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
                refreshDateButtonText()
            }.show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val title = titleInput.text.toString()
            val totalAmount = totalAmountInput.text.toString().toDoubleOrNull() ?: 0.0
            val installmentCount = installmentCountInput.text.toString().toIntOrNull() ?: 1
            val monthlyAmount = monthlyAmountInput.text.toString().toDoubleOrNull() ?: 0.0
            val lender = lenderInput.text.toString()

            if (title.isNotBlank() && totalAmount > 0 && installmentCount > 0) {
                val installment = Installment(
                    title = title,
                    totalAmount = totalAmount,
                    installmentAmount = monthlyAmount,
                    totalInstallments = installmentCount,
                    paidInstallments = 0,
                    startDate = selectedStartDate,
                    paymentDay = 1,
                    recipient = lender
                )
                viewModel.addInstallment(installment)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
