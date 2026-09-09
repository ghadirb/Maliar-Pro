package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.database.BusinessManager
import com.maliar.pro.database.BusinessTransaction
import com.maliar.pro.database.BusinessTransactionType
import com.maliar.pro.dialogs.BusinessTransactionDialog
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read/delete history for «عملیات کسب‌وکار» (BusinessTransactionDialog): خرید کالا, انتقال
 * بین حساب‌ها, برداشت شخصی, تزریق سرمایه. Before this screen, that dialog was a
 * fire-and-forget form - the transaction changed an account balance and (for purchases)
 * inventory, but there was nowhere in the app to see that it had actually been recorded.
 * Editing isn't offered here (same as the گذشته-only transaction lists elsewhere in the
 * app, e.g. طلا و سکه): delete-and-re-add keeps the balance/inventory math unambiguous.
 */
class BusinessTransactionListFragment : Fragment() {
    private val manager by lazy { BusinessManager(requireContext()) }
    private lateinit var content: LinearLayout
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        return ScrollView(requireContext()).apply { addView(content) }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        content.addView(heading("📋 تراکنش‌های کسب‌وکار"))
        content.addView(TextView(requireContext()).apply {
            text = "خرید کالا، انتقال بین حساب‌ها، برداشت شخصی و تزریق سرمایه - برای حذف روی مورد نگه دارید."
            setTextColor(themeColor(R.color.text_secondary)); setPadding(0, 4, 0, 16)
        })
        val addButton = android.widget.Button(requireContext()).apply {
            text = "➕ ثبت تراکنش جدید"
            setOnClickListener { BusinessTransactionDialog(requireContext()).show() }
        }
        content.addView(addButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        val listBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)
        viewLifecycleOwner.lifecycleScope.launch { manager.getTransactions().collectLatest { render(listBox, it) } }
    }

    private fun render(box: LinearLayout, items: List<BusinessTransaction>) {
        box.removeAllViews()
        if (items.isEmpty()) {
            box.addView(TextView(requireContext()).apply { text = "هنوز تراکنشی ثبت نشده است."; setTextColor(themeColor(R.color.text_secondary)) })
            return
        }
        items.forEach { tx ->
            box.addView(TextView(requireContext()).apply {
                text = "${typeLabel(tx.type)}${detail(tx)}\n${CurrencyFormatter.format(tx.amount)} · ${dateFormat.format(Date(tx.date))}"
                textSize = 14f
                setTextColor(themeColor(R.color.text_primary))
                setPadding(16, 14, 16, 14)
                setOnLongClickListener { confirmDelete(tx); true }
            })
        }
    }

    private fun detail(tx: BusinessTransaction): String = when (tx.type) {
        BusinessTransactionType.PRODUCT_PURCHASE -> " · ${tx.productName} × ${plain(tx.quantity)} (${CurrencyFormatter.format(tx.unitCost)}/واحد)"
        else -> if (tx.description.isNotBlank()) " · ${tx.description}" else ""
    }

    private fun typeLabel(type: BusinessTransactionType) = when (type) {
        BusinessTransactionType.PRODUCT_PURCHASE -> "🛒 خرید کالا"
        BusinessTransactionType.TRANSFER -> "🔁 انتقال بین حساب‌ها"
        BusinessTransactionType.OWNER_DRAW -> "🏠 برداشت شخصی"
        BusinessTransactionType.CAPITAL_INJECTION -> "💰 تزریق سرمایه"
        BusinessTransactionType.BANK_FEE -> "🏦 کارمزد بانکی"
    }

    private fun confirmDelete(tx: BusinessTransaction) {
        AlertDialog.Builder(requireContext()).setTitle("حذف تراکنش")
            .setMessage("«${typeLabel(tx.type)}» به مبلغ ${CurrencyFormatter.format(tx.amount)} حذف شود؟ موجودی حساب و کالا اصلاح می‌شود.")
            .setNegativeButton("لغو", null)
            .setPositiveButton("حذف") { _, _ -> viewLifecycleOwner.lifecycleScope.launch { manager.delete(tx) } }
            .show()
    }

    private fun plain(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
    private fun heading(text: String) = TextView(requireContext()).apply { this.text = text; textSize = 18f; setTypeface(null, 1) }
    private fun themeColor(colorRes: Int) = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
}
