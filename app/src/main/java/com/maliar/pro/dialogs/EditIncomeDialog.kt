package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Spinner
import com.maliar.pro.database.Asset
import com.maliar.pro.database.Income
import com.maliar.pro.utils.AccountSpinnerHelper
import com.maliar.pro.utils.IncomeTypeSectionHelper
import com.maliar.pro.viewmodels.AccountingViewModel

class EditIncomeDialog(private val context: Context, private val viewModel: AccountingViewModel, private val income: Income) {

    private var loadedAccounts: List<Asset> = emptyList()

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ویرایش درآمد")

        val view = android.view.LayoutInflater.from(context).inflate(com.maliar.pro.R.layout.dialog_add_income, null)
        val categoryInput = view.findViewById<EditText>(com.maliar.pro.R.id.sourceInput)
        val amountInput = view.findViewById<EditText>(com.maliar.pro.R.id.amountInput)
        val descriptionInput = view.findViewById<EditText>(com.maliar.pro.R.id.descriptionInput)
        val accountSpinner = view.findViewById<Spinner>(com.maliar.pro.R.id.accountSpinner)
        AccountSpinnerHelper.populate(context, accountSpinner, preselectAccountId = income.accountId) { loadedAccounts = it }

        categoryInput.setText(income.category)
        amountInput.setText(income.amount.toString())
        descriptionInput.setText(income.description)
        val typeSection = IncomeTypeSectionHelper.bind(view)
        typeSection.preset(income.isProductSale, income.costOfGoods)

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val category = categoryInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text.toString()

            if (category.isNotBlank() && amount > 0) {
                val updatedIncome = income.copy(
                    category = category,
                    amount = amount,
                    description = description,
                    accountId = AccountSpinnerHelper.selectedAccountId(accountSpinner, loadedAccounts),
                    isProductSale = typeSection.isProductSale(),
                    costOfGoods = typeSection.costOfGoods()
                )
                viewModel.updateIncome(updatedIncome)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
