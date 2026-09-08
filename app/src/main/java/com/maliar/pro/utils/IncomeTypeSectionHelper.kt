package com.maliar.pro.utils

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.maliar.pro.R

/**
 * Shared wiring for the «نوع درآمد» (درآمد خدمات / فروش کالا) section in
 * dialog_add_income.xml, used by both AddIncomeDialog and EditIncomeDialog: shows/hides the
 * «بهای تمام‌شده» field based on the selected radio button and keeps the live "سود فروش"
 * preview in sync with whatever's currently typed in the amount/cost fields. Kept as one
 * small helper instead of duplicating this logic in both dialogs.
 */
object IncomeTypeSectionHelper {

    class Binding(
        private val radioProductSale: RadioButton,
        private val costOfGoodsInput: EditText
    ) {
        fun isProductSale(): Boolean = radioProductSale.isChecked

        /** 0 whenever "درآمد خدمات" is selected, regardless of what's left typed in the
         *  (hidden) cost field - so switching the radio back never accidentally saves a
         *  stray costOfGoods on a service income. */
        fun costOfGoods(): Double =
            if (isProductSale()) costOfGoodsInput.text.toString().toDoubleOrNull() ?: 0.0 else 0.0

        /** Pre-fills the section when opening EditIncomeDialog on an existing entry. */
        fun preset(isProductSale: Boolean, costOfGoods: Double) {
            radioProductSale.isChecked = isProductSale
            if (costOfGoods != 0.0) costOfGoodsInput.setText(formatPlain(costOfGoods))
        }
    }

    fun bind(view: View): Binding {
        val radioGroup = view.findViewById<RadioGroup>(R.id.incomeTypeGroup)
        val radioProductSale = view.findViewById<RadioButton>(R.id.radioProductSale)
        val productSaleSection = view.findViewById<LinearLayout>(R.id.productSaleSection)
        val costOfGoodsInput = view.findViewById<EditText>(R.id.costOfGoodsInput)
        val profitPreviewText = view.findViewById<TextView>(R.id.profitPreviewText)
        val amountInput = view.findViewById<EditText>(R.id.amountInput)

        fun refresh() {
            val isProductSale = radioProductSale.isChecked
            productSaleSection.visibility = if (isProductSale) View.VISIBLE else View.GONE
            if (!isProductSale) return
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val cost = costOfGoodsInput.text.toString().toDoubleOrNull() ?: 0.0
            val profit = amount - cost
            profitPreviewText.text = "سود فروش: ${CurrencyFormatter.format(profit)}"
            profitPreviewText.setTextColor(
                Color.parseColor(if (profit < 0) "#F44336" else "#4CAF50")
            )
        }

        radioGroup.setOnCheckedChangeListener { _, _ -> refresh() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh() }
        }
        amountInput.addTextChangedListener(watcher)
        costOfGoodsInput.addTextChangedListener(watcher)

        refresh()
        return Binding(radioProductSale, costOfGoodsInput)
    }

    private fun formatPlain(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
