package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.database.Installment
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Calendar
import java.util.Date

class AddInstallmentDialog(private val context: Context, private val viewModel: AccountingViewModel) {
    
    private var selectedStartDate: Long = System.currentTimeMillis()
    
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
        
        startDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedStartDate = calendar.timeInMillis
                    startDateButton.text = "$day/${month + 1}/$year"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
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
