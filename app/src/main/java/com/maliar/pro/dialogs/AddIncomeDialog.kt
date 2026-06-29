package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.maliar.pro.database.Income
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class AddIncomeDialog(private val context: Context, private val viewModel: AccountingViewModel) {
    
    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن درآمد")
        
        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_income, null)
        val sourceInput = view.findViewById<EditText>(com.maliar.pro.R.id.sourceInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)
        
        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val source = sourceInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text.toString()
            
            if (source.isNotBlank() && amount > 0) {
                val income = Income(
                    source = source,
                    amount = amount,
                    description = description,
                    date = Date().time
                )
                viewModel.addIncome(income)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
