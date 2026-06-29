package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.maliar.pro.database.Expense
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class AddExpenseDialog(private val context: Context, private val viewModel: AccountingViewModel) {
    
    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن هزینه")
        
        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_expense, null)
        val categoryInput = view.findViewById<EditText>(com.maliar.pro.R.id.categoryInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)
        
        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val category = categoryInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text.toString()
            
            if (category.isNotBlank() && amount > 0) {
                val expense = Expense(
                    category = category,
                    amount = amount,
                    description = description,
                    date = Date().time
                )
                viewModel.addExpense(expense)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
