package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.maliar.pro.database.Check
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Calendar
import java.util.Date

class AddCheckDialog(private val context: Context, private val viewModel: AccountingViewModel) {
    
    private var selectedDueDate: Long = System.currentTimeMillis()
    
    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن چک")
        
        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_check, null)
        val checkNumberInput = view.findViewById<EditText>(com.maliar.pro.R.id.checkNumberInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val payeeInput = view.findViewById<EditText>(com.maliar.pro.R.id.payeeInput)
        val dueDateButton = view.findViewById<Button>(com.maliar.pro.R.id.dueDateButton)
        
        dueDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDueDate = calendar.timeInMillis
                    dueDateButton.text = "$day/${month + 1}/$year"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
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
                    payee = payee,
                    dueDate = selectedDueDate,
                    issueDate = Date().time,
                    isCashed = false
                )
                viewModel.addCheck(check)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
