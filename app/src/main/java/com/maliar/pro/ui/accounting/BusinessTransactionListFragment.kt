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
import androidx.navigation.fragment.findNavController
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.BusinessManager
import com.maliar.pro.database.BusinessTransaction
import com.maliar.pro.database.BusinessTransactionType
import com.maliar.pro.database.ProductInventory
import com.maliar.pro.database.CustomerManager
import com.maliar.pro.dialogs.BusinessTransactionDialog
import com.maliar.pro.utils.BusinessDashboardCalculator
import com.maliar.pro.utils.BusinessDashboardSummary
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Business ledger plus a read-only dashboard built solely from the existing Room data. */
class BusinessTransactionListFragment : Fragment() {
    private val manager by lazy { BusinessManager(requireContext()) }
    private val accountingManager by lazy { AccountingManager(requireContext()) }
    private val customerManager by lazy { CustomerManager(requireContext()) }
    private lateinit var content: LinearLayout
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 48)
        }
        return ScrollView(requireContext()).apply { addView(content) }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        content.addView(heading("کسب‌وکار من"))
        content.addView(TextView(requireContext()).apply {
            text = "فروش کالا، سود ناخالص و موجودی فقط از اطلاعات ثبت‌شده محاسبه می‌شوند."
            setTextColor(themeColor(R.color.text_secondary))
            setPadding(0, 4, 0, 16)
        })
        content.addView(android.widget.Button(requireContext()).apply {
            text = "ثبت تراکنش جدید"
            setOnClickListener { BusinessTransactionDialog(requireContext()).show() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        content.addView(android.widget.Button(requireContext()).apply {
            text = "مشتریان و حساب نسیه"
            setOnClickListener { findNavController().navigate(R.id.action_businessTransactionListFragment_to_customerLedgerFragment) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        val dashboardBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(dashboardBox)
        content.addView(heading("تراکنش‌های کسب‌وکار").apply { setPadding(0, 16, 0, 4) })
        val listBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)

        viewLifecycleOwner.lifecycleScope.launch {
            combine(manager.getTransactions(), manager.getInventory(), accountingManager.getAllIncomes(), customerManager.getAll()) {
                    transactions, inventory, incomes, _ -> Triple(transactions, inventory, incomes)
            }.collectLatest { (transactions, inventory, incomes) ->
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val summary = BusinessDashboardCalculator.calculate(
                    incomes, inventory, transactions,
                    accountingManager.getFinancialPeriodStartMillis(), todayStart
                )
                val receivables = customerManager.getBalancesList().sumOf { it.balance.coerceAtLeast(0.0) }
                renderDashboard(dashboardBox, summary, inventory, receivables)
                renderTransactions(listBox, transactions)
            }
        }
    }

    private fun renderDashboard(box: LinearLayout, summary: BusinessDashboardSummary, inventory: List<ProductInventory>, receivables: Double) {
        box.removeAllViews()
        fun line(label: String, value: String) = TextView(requireContext()).apply {
            text = "$label: $value"
            textSize = 14f
            setTextColor(themeColor(R.color.text_primary))
            setPadding(16, 7, 16, 7)
        }
        box.addView(line("فروش کالای امروز", CurrencyFormatter.format(summary.todayProductSales)))
        box.addView(line("فروش کالا در دورهٔ جاری", CurrencyFormatter.format(summary.periodProductSales)))
        box.addView(line("سود ناخالص کالا در دورهٔ جاری", CurrencyFormatter.format(summary.periodGrossProfit)))
        box.addView(line("تعداد فروش کالا", "${summary.periodSaleCount} مورد"))
        box.addView(line("ارزش بهای تمام‌شدهٔ موجودی", CurrencyFormatter.format(summary.inventoryCostValue)))
        box.addView(line("دریافتنی از مشتریان", CurrencyFormatter.format(receivables)))
        box.addView(line("پرداختی مرتبط با کسب‌وکار", CurrencyFormatter.format(summary.relatedPayments)))
        if (inventory.isNotEmpty()) box.addView(line("کالای ناموجود", "${summary.outOfStockCount} مورد"))
        summary.bestSellingProduct?.let { box.addView(line("پرفروش‌ترین کالا", "${it.name} · ${CurrencyFormatter.format(it.sales)}")) }
        summary.lowestProfitProduct?.let { box.addView(line("کم‌سودترین کالای ثبت‌شده", "${it.name} · ${CurrencyFormatter.format(it.profit)}")) }
        if (summary.lowStockProducts.isNotEmpty()) box.addView(line("رو به اتمام", summary.lowStockProducts.joinToString("، ") { "${it.name} (${plain(it.quantity)})" }))
        if (summary.recentPurchases.isNotEmpty()) {
            box.addView(TextView(requireContext()).apply {
                text = "خریدهای اخیر"
                textSize = 14f
                setTypeface(null, 1)
                setTextColor(themeColor(R.color.text_primary))
                setPadding(16, 14, 16, 4)
            })
            summary.recentPurchases.forEach { purchase ->
                box.addView(TextView(requireContext()).apply {
                    text = "${purchase.productName.ifBlank { "کالای بدون نام" }} · ${CurrencyFormatter.format(purchase.amount)}"
                    textSize = 13f
                    setTextColor(themeColor(R.color.text_secondary))
                    setPadding(16, 3, 16, 3)
                })
            }
        }
        box.addView(TextView(requireContext()).apply {
            text = "هزینه‌های عمومی و مطالبات اینجا لحاظ نشده‌اند، چون در داده‌های فعلی برچسب کسب‌وکار ندارند."
            textSize = 12f
            setTextColor(themeColor(R.color.text_secondary))
            setPadding(16, 12, 16, 4)
        })
    }

    private fun renderTransactions(box: LinearLayout, items: List<BusinessTransaction>) {
        box.removeAllViews()
        if (items.isEmpty()) {
            box.addView(TextView(requireContext()).apply {
                text = "هنوز تراکنشی ثبت نشده است."
                setTextColor(themeColor(R.color.text_secondary))
            })
            return
        }
        items.forEach { transaction ->
            box.addView(TextView(requireContext()).apply {
                text = "${typeLabel(transaction.type)}${detail(transaction)}\n${CurrencyFormatter.format(transaction.amount)} · ${dateFormat.format(Date(transaction.date))}"
                textSize = 14f
                setTextColor(themeColor(R.color.text_primary))
                setPadding(16, 14, 16, 14)
                setOnLongClickListener { confirmDelete(transaction); true }
            })
        }
    }

    private fun detail(tx: BusinessTransaction): String = when (tx.type) {
        BusinessTransactionType.PRODUCT_PURCHASE -> " · ${tx.productName} × ${plain(tx.quantity)} (${CurrencyFormatter.format(tx.unitCost)}/واحد)"
        else -> if (tx.description.isNotBlank()) " · ${tx.description}" else ""
    }

    private fun typeLabel(type: BusinessTransactionType) = when (type) {
        BusinessTransactionType.PRODUCT_PURCHASE -> "خرید کالا"
        BusinessTransactionType.TRANSFER -> "انتقال بین حساب‌ها"
        BusinessTransactionType.OWNER_DRAW -> "برداشت شخصی"
        BusinessTransactionType.CAPITAL_INJECTION -> "تزریق سرمایه"
        BusinessTransactionType.BANK_FEE -> "کارمزد بانکی"
    }

    private fun confirmDelete(tx: BusinessTransaction) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف تراکنش")
            .setMessage("«${typeLabel(tx.type)}» به مبلغ ${CurrencyFormatter.format(tx.amount)} حذف شود؟ موجودی حساب و کالا اصلاح می‌شود.")
            .setNegativeButton("لغو", null)
            .setPositiveButton("حذف") { _, _ -> viewLifecycleOwner.lifecycleScope.launch { manager.delete(tx) } }
            .show()
    }

    private fun plain(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
    private fun heading(text: String) = TextView(requireContext()).apply { this.text = text; textSize = 18f; setTypeface(null, 1) }
    private fun themeColor(colorRes: Int) = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
}
