package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
        val title = EditText(context).apply { hint = "نام کالا" }
        val quantity = EditText(context).apply { hint = "تعداد"; inputType = 2 }
        val unitCost = EditText(context).apply { hint = "قیمت خرید هر واحد"; inputType = 2 }
        val amount = EditText(context).apply { hint = "مبلغ"; inputType = 2 }
        val note = EditText(context).apply { hint = "توضیحات" }
        val fromLabel = TextView(context)
        val toLabel = TextView(context)
        val from = Spinner(context); val to = Spinner(context)
        var loaded = emptyList<Asset>()
        AccountSpinnerHelper.populate(context, from) { loaded = it }
        AccountSpinnerHelper.populate(context, to) { loaded = it }

        // Each row is only shown for the kinds it actually applies to - a خرید کالا doesn't
        // need a "to" account, and a برداشت شخصی doesn't need نام کالا/تعداد/قیمت خرید.
        val purchaseRows = listOf(title, quantity, unitCost)
        fun showOnly(views: Set<View>) { (purchaseRows + listOf(amount, note, fromLabel, from, toLabel, to)).forEach { it.visibility = if (it in views) View.VISIBLE else View.GONE } }
        fun applyKind(position: Int) {
            when (BusinessTransactionType.values()[position]) {
                BusinessTransactionType.PRODUCT_PURCHASE -> { showOnly(setOf(title, quantity, unitCost, note, fromLabel, from)); fromLabel.text = "حساب پرداخت‌کننده" }
                BusinessTransactionType.TRANSFER -> { showOnly(setOf(amount, note, fromLabel, from, toLabel, to)); fromLabel.text = "حساب مبدأ"; toLabel.text = "حساب مقصد" }
                BusinessTransactionType.OWNER_DRAW -> { showOnly(setOf(amount, note, fromLabel, from)); fromLabel.text = "حساب برداشت" }
                BusinessTransactionType.CAPITAL_INJECTION -> { showOnly(setOf(amount, note, toLabel, to)); toLabel.text = "حساب واریز" }
                BusinessTransactionType.BANK_FEE -> { showOnly(setOf(amount, note, fromLabel, from)); fromLabel.text = "حساب کسرشده" }
            }
        }
        kindSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = applyKind(position)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        note.hint = "توضیحات / تأمین‌کننده (اختیاری)"
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0)
            addView(kindSpinner)
            listOf(title, quantity, unitCost, amount, note).forEach(::addView)
            addView(fromLabel); addView(from)
            addView(toLabel); addView(to)
        }
        applyKind(0)
        AlertDialog.Builder(context).setTitle("عملیات کسب‌وکار").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val selected = kindSpinner.selectedItemPosition
                val qty = quantity.text.toString().toDoubleOrNull() ?: 0.0
                val cost = unitCost.text.toString().toDoubleOrNull() ?: 0.0
                val entered = amount.text.toString().toDoubleOrNull() ?: 0.0
                val total = if (selected == 0 && qty > 0 && cost > 0) qty * cost else entered
                if (total <= 0) { Toast.makeText(context, "مبلغ معتبر وارد کنید.", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                val type = BusinessTransactionType.values()[selected]
                val fromId = AccountSpinnerHelper.selectedAccountId(from, loaded)
                val toId = AccountSpinnerHelper.selectedAccountId(to, loaded)
                // PRODUCT_PURCHASE/OWNER_DRAW/BANK_FEE debit "from"; TRANSFER/CAPITAL_INJECTION
                // credit "to". Recording still proceeds either way (per «تراکنش‌های کسب‌وکار»
                // history), but without the account the balance genuinely won't move - worth
                // an explicit warning instead of a silent no-op, which is what looked like "no
                // effect at all" before this.
                val needsFrom = type == BusinessTransactionType.PRODUCT_PURCHASE || type == BusinessTransactionType.OWNER_DRAW || type == BusinessTransactionType.BANK_FEE || type == BusinessTransactionType.TRANSFER
                val needsTo = type == BusinessTransactionType.TRANSFER || type == BusinessTransactionType.CAPITAL_INJECTION
                val missingAccount = (needsFrom && fromId == null) || (needsTo && toId == null)
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
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
                    if (missingAccount) {
                        Toast.makeText(context, "ثبت شد، اما چون حسابی انتخاب نشد موجودی هیچ حسابی تغییر نکرد. برای دیدن اثر روی حساب، دفعهٔ بعد یک حساب انتخاب کنید.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "ثبت شد. از «مشاهدهٔ تراکنش‌های کسب‌وکار» می‌توانید آن را ببینید.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }
}
