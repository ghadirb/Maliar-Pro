package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.maliar.pro.database.Income
import com.maliar.pro.viewmodels.AccountingViewModel
import java.util.Date

class EditIncomeDialog(private val context: Context, private val viewModel: AccountingViewModel, private val income: Income) {

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش درآمد")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_income, null)
        val categoryInput = view.findViewById<EditText>(com.maliar.pro.R.id.categoryInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)

        categoryInput.setText(income.category)
        amountInput.setText(income.amount.toString())
        descriptionInput.setText(income.description)

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val category = categoryInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text.toString()

            if (category.isNotBlank() && amount > 0) {
                val updatedIncome = income.copy(
                    category = category,
                    amount = amount,
                    description = description
                )
                viewModel.updateIncome(updatedIncome)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
