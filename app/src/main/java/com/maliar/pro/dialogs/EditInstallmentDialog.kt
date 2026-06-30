package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.database.Installment
import com.maliar.pro.viewmodels.AccountingViewModel

class EditInstallmentDialog(private val context: Context, private val viewModel: AccountingViewModel, private val installment: Installment) {

    private var selectedStartDate: Long = installment.startDate

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش قسط")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_installment, null)
        val titleInput = view.findViewById<EditText>(com.maliar.pro.R.id.titleInput)
        val totalAmountInput = view.findViewById<EditText>(com.maliar.pro.R.id.totalAmountInput)
        val installmentCountInput = view.findViewById<EditText>(com.maliar.pro.R.id.installmentCountInput)
        val monthlyAmountInput = view.findViewById<EditText>(com.maliar.pro.R.id.monthlyAmountInput)
        val lenderInput = view.findViewById<EditText>(com.maliar.pro.R.id.lenderInput)
        val startDateButton = view.findViewById<Button>(com.maliar.pro.R.id.startDateButton)

        titleInput.setText(installment.title)
        totalAmountInput.setText(installment.totalAmount.toString())
        installmentCountInput.setText(installment.totalInstallments.toString())
        monthlyAmountInput.setText(installment.installmentAmount.toString())
        lenderInput.setText(installment.recipient)

        val persianCalendar = IslamicCalendar.getInstance(ULocale.forLanguageTag("fa_IR"))
        persianCalendar.timeInMillis = installment.startDate
        startDateButton.text = "${persianCalendar.get(IslamicCalendar.DAY_OF_MONTH)}/${persianCalendar.get(IslamicCalendar.MONTH) + 1}/${persianCalendar.get(IslamicCalendar.YEAR)}"

        startDateButton.setOnClickListener {
            android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    persianCalendar.set(year, month, day)
                    selectedStartDate = persianCalendar.timeInMillis
                    startDateButton.text = "$day/${month + 1}/$year"
                },
                persianCalendar.get(IslamicCalendar.YEAR),
                persianCalendar.get(IslamicCalendar.MONTH),
                persianCalendar.get(IslamicCalendar.DAY_OF_MONTH)
            ).show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val title = titleInput.text.toString()
            val totalAmount = totalAmountInput.text.toString().toDoubleOrNull() ?: 0.0
            val installmentCount = installmentCountInput.text.toString().toIntOrNull() ?: 1
            val monthlyAmount = monthlyAmountInput.text.toString().toDoubleOrNull() ?: 0.0
            val lender = lenderInput.text.toString()

            if (title.isNotBlank() && totalAmount > 0 && installmentCount > 0) {
                val updatedInstallment = installment.copy(
                    title = title,
                    totalAmount = totalAmount,
                    installmentAmount = monthlyAmount,
                    totalInstallments = installmentCount,
                    startDate = selectedStartDate,
                    recipient = lender
                )
                viewModel.updateInstallment(updatedInstallment)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
