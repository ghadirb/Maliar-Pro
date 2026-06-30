package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.maliar.pro.database.Expense
import com.maliar.pro.viewmodels.AccountingViewModel

class EditExpenseDialog(private val context: Context, private val viewModel: AccountingViewModel, private val expense: Expense) {

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش هزینه")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_expense, null)
        val categoryInput = view.findViewById<EditText>(com.maliar.pro.R.id.categoryInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)

        categoryInput.setText(expense.category)
        amountInput.setText(expense.amount.toString())
        descriptionInput.setText(expense.description)

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val category = categoryInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text.toString()

            if (category.isNotBlank() && amount > 0) {
                val updatedExpense = expense.copy(
                    category = category,
                    amount = amount,
                    description = description
                )
                viewModel.updateExpense(updatedExpense)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
