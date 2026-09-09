package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Spinner
import com.maliar.pro.database.Asset
import com.maliar.pro.database.Expense
import com.maliar.pro.database.ExpenseCategory
import com.maliar.pro.utils.AccountSpinnerHelper
import com.maliar.pro.viewmodels.AccountingViewModel

class EditExpenseDialog(private val context: Context, private val viewModel: AccountingViewModel, private val expense: Expense) {

    private var loadedAccounts: List<Asset> = emptyList()

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش هزینه")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_expense, null)
        // Custom row layout (item_category_dropdown) + explicit popup background: the
        // previous android.R.layout.simple_list_item_1 rows were small and hard to tap,
        // and the popup had no background of its own to stand out against the screen.
        val categoryInput = view.findViewById<AutoCompleteTextView>(com.maliar.pro.R.id.categoryInput).apply {
            setAdapter(ArrayAdapter(context, com.maliar.pro.R.layout.item_category_dropdown, android.R.id.text1, ExpenseCategory.ALL))
            setDropDownBackgroundResource(com.maliar.pro.R.drawable.bg_category_dropdown)
            threshold = 0
            setOnClickListener { showDropDown() }
        }
        view.findViewById<com.google.android.material.textfield.TextInputLayout>(com.maliar.pro.R.id.categoryInputLayout)
            ?.setEndIconOnClickListener { categoryInput.showDropDown() }
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)
        val accountSpinner = view.findViewById<Spinner>(com.maliar.pro.R.id.accountSpinner)
        AccountSpinnerHelper.populate(context, accountSpinner, preselectAccountId = expense.accountId) { loadedAccounts = it }

        categoryInput.setText(expense.category, false)
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
                    description = description,
                    accountId = AccountSpinnerHelper.selectedAccountId(accountSpinner, loadedAccounts)
                )
                viewModel.updateExpense(updatedExpense)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
