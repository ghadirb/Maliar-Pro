package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import com.maliar.pro.database.Asset
import com.maliar.pro.database.BusinessManager
import com.maliar.pro.database.BusinessTransaction
import com.maliar.pro.database.BusinessTransactionType
import com.maliar.pro.database.Expense
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.utils.AccountSpinnerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Compact, deliberate entry point for non-income/non-expense business cash movements. */
class BusinessTransactionDialog(private val context: Context) {
    fun show() {
        val kinds = listOf("خرید کالا", "انتقال بین حساب‌ها", "برداشت شخصی", "تزریق سرمایه", "کارمزد بانکی/کارتخوان")
        val kindSpinner = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, kinds) }
        val title = EditText(context).apply { hint = "نام کالا / عنوان" }
        val quantity = EditText(context).apply { hint = "تعداد (فقط خرید کالا)"; inputType = 2 }
        val unitCost = EditText(context).apply { hint = "قیمت خرید هر واحد"; inputType = 2 }
        val amount = EditText(context).apply { hint = "مبلغ"; inputType = 2 }
        val note = EditText(context).apply { hint = "توضیحات یا تأمین‌کننده (اختیاری)" }
        val from = Spinner(context); val to = Spinner(context)
        var loaded = emptyList<Asset>()
        AccountSpinnerHelper.populate(context, from) { loaded = it }
        AccountSpinnerHelper.populate(context, to) { loaded = it }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0)
            listOf(kindSpinner, title, quantity, unitCost, amount, note).forEach(::addView)
            addView(android.widget.TextView(context).apply { text = "حساب مبدأ / پرداخت‌کننده" }); addView(from)
            addView(android.widget.TextView(context).apply { text = "حساب مقصد (فقط انتقال و تزریق)" }); addView(to)
        }
        AlertDialog.Builder(context).setTitle("عملیات کسب‌وکار").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val selected = kindSpinner.selectedItemPosition
                val qty = quantity.text.toString().toDoubleOrNull() ?: 0.0
                val cost = unitCost.text.toString().toDoubleOrNull() ?: 0.0
                val entered = amount.text.toString().toDoubleOrNull() ?: 0.0
                val total = if (selected == 0 && qty > 0 && cost > 0) qty * cost else entered
                if (total <= 0) return@setPositiveButton
                val type = BusinessTransactionType.values()[selected]
                val fromId = AccountSpinnerHelper.selectedAccountId(from, loaded)
                val toId = AccountSpinnerHelper.selectedAccountId(to, loaded)
                CoroutineScope(Dispatchers.IO).launch {
                    if (type == BusinessTransactionType.BANK_FEE) {
                        AccountingManager(context).addExpense(Expense(
                            amount = total,
                            description = "کارمزد بانکی: ${note.text}",
                            date = System.currentTimeMillis(),
                            category = "کارمزد بانکی",
                            accountId = fromId
                        ))
                    } else {
                        BusinessManager(context).add(BusinessTransaction(type = type, amount = total,
                            fromAccountId = fromId, toAccountId = toId, description = note.text.toString(),
                            supplier = note.text.toString(), productName = title.text.toString(), quantity = qty, unitCost = cost))
                    }
                }
            }.show()
    }
}
