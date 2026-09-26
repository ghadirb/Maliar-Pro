package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.database.Asset
import com.maliar.pro.database.Customer
import com.maliar.pro.database.CustomerLedgerEntry
import com.maliar.pro.database.CustomerLedgerType
import com.maliar.pro.database.CustomerManager
import com.maliar.pro.database.Income
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.utils.AccountSpinnerHelper
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Compact customer list and ledger UI; no duplicate balance or cash ledger is maintained here. */
class CustomerLedgerFragment : Fragment() {
    private val manager by lazy { CustomerManager(requireContext()) }
    private lateinit var content: LinearLayout
    private var observer: Job? = null

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        return ScrollView(requireContext()).apply { addView(content) }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        showCustomers()
    }

    private fun showCustomers() {
        observer?.cancel()
        content.removeAllViews()
        content.addView(title("مشتریان و حساب نسیه"))
        content.addView(TextView(requireContext()).apply {
            text = "فروش نسیه فقط طلب مشتری را افزایش می‌دهد؛ دریافت وجه فقط به حساب انتخاب‌شده واریز می‌شود."
            setTextColor(color(R.color.text_secondary)); setPadding(0, 4, 0, 12)
        })
        content.addView(android.widget.Button(requireContext()).apply {
            text = "افزودن مشتری"
            setOnClickListener { addCustomerDialog() }
        })
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        content.addView(rows)
        observer = viewLifecycleOwner.lifecycleScope.launch {
            manager.getAll().collectLatest {
                // Highest-debt customers first, matching the "بیشترین بدهکاران" business
                // report requested in the spec; customers with a credit (negative) balance
                // or none sort after debtors, alphabetically among themselves.
                val balances = manager.getBalancesList()
                    .sortedWith(compareByDescending<com.maliar.pro.database.CustomerBalance> { it.balance.coerceAtLeast(0.0) }
                        .thenBy { it.customer.name })
                rows.removeAllViews()
                if (balances.isEmpty()) {
                    rows.addView(note("هنوز مشتری ثبت نشده است."))
                } else balances.forEach { item ->
                    rows.addView(TextView(requireContext()).apply {
                        text = "${item.customer.name}\nمانده: ${CurrencyFormatter.format(item.balance)} · خرید: ${CurrencyFormatter.format(item.sales)} · پرداخت: ${CurrencyFormatter.format(item.payments)}"
                        textSize = 15f; setTextColor(color(R.color.text_primary)); setPadding(16, 16, 16, 16)
                        setOnClickListener { showCustomer(item.customer) }
                    })
                }
            }
        }
    }

    private fun showCustomer(customer: Customer) {
        observer?.cancel()
        content.removeAllViews()
        content.addView(android.widget.Button(requireContext()).apply { text = "بازگشت به مشتریان"; setOnClickListener { showCustomers() } })
        content.addView(title(customer.name).apply { setPadding(0, 16, 0, 0) })
        val summary = TextView(requireContext()).apply { setTextColor(color(R.color.text_primary)); setPadding(0, 8, 0, 12) }
        content.addView(summary)
        val sale = android.widget.Button(requireContext()).apply { text = "ثبت فروش نسیه"; setOnClickListener { creditSaleDialog(customer) } }
        val payment = android.widget.Button(requireContext()).apply { text = "دریافت وجه"; setOnClickListener { paymentDialog(customer) } }
        val adjustment = android.widget.Button(requireContext()).apply { text = "اصلاح حساب"; setOnClickListener { adjustmentDialog(customer) } }
        val reminder = android.widget.Button(requireContext()).apply { text = "یادآوری پیگیری بدهی"; setOnClickListener { reminderDialog(customer) } }
        content.addView(sale); content.addView(payment); content.addView(adjustment); content.addView(reminder)
        content.addView(title("گردش حساب").apply { textSize = 16f; setPadding(0, 16, 0, 4) })
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        observer = viewLifecycleOwner.lifecycleScope.launch {
            manager.getBalance(customer.id).collectLatest { balance ->
                if (balance != null) summary.text = "مانده فعلی: ${CurrencyFormatter.format(balance.balance)}\nفروش نسیه: ${CurrencyFormatter.format(balance.sales)} · دریافت: ${CurrencyFormatter.format(balance.payments)}"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            manager.getEntries(customer.id).collectLatest { entries ->
                rows.removeAllViews()
                if (entries.isEmpty()) rows.addView(note("هنوز گردش حسابی ثبت نشده است."))
                else entries.forEach { entry ->
                    rows.addView(TextView(requireContext()).apply {
                        text = "${entryLabel(entry.type)} · ${CurrencyFormatter.format(entry.amount)}${entry.note.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""}"
                        textSize = 14f; setTextColor(color(R.color.text_primary)); setPadding(16, 14, 16, 14)
                        setOnLongClickListener { confirmDeleteEntry(entry); true }
                    })
                }
            }
        }
    }

    private fun addCustomerDialog() {
        val box = formBox()
        val name = field("نام مشتری")
        val phone = field("شماره تماس (اختیاری)")
        val note = field("توضیحات (اختیاری)")
        box.addView(name); box.addView(phone); box.addView(note)
        AlertDialog.Builder(requireContext()).setTitle("افزودن مشتری").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ ->
                val title = name.text.toString().trim()
                if (title.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { manager.add(Customer(name = title, phoneNumber = phone.text.toString().trim(), description = note.text.toString().trim())) }
            }.show()
    }

    private fun creditSaleDialog(customer: Customer) {
        val box = formBox()
        val amount = field("مبلغ فروش")
        val product = field("نام کالا (اختیاری؛ برای کسر موجودی)")
        val cost = field("بهای تمام‌شده (اختیاری)")
        val note = field("یادداشت")
        box.addView(amount); box.addView(product); box.addView(cost); box.addView(note)
        AlertDialog.Builder(requireContext()).setTitle("فروش نسیه به ${customer.name}").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (value > 0) viewLifecycleOwner.lifecycleScope.launch {
                    manager.addCreditSale(customer.id, Income(
                        amount = value, description = note.text.toString().trim(), date = System.currentTimeMillis(),
                        category = "فروش نسیه", isProductSale = product.text.toString().trim().isNotBlank(),
                        productName = product.text.toString().trim(), costOfGoods = cost.text.toString().toDoubleOrNull() ?: 0.0
                    ), note.text.toString().trim(), null)
                }
            }.show()
    }

    private fun paymentDialog(customer: Customer) {
        val box = formBox()
        val amount = field("مبلغ دریافت")
        val note = field("یادداشت")
        val spinner = Spinner(requireContext())
        var accounts: List<Asset> = emptyList()
        AccountSpinnerHelper.populate(requireContext(), spinner) { accounts = it }
        box.addView(amount); box.addView(spinner); box.addView(note)
        AlertDialog.Builder(requireContext()).setTitle("دریافت وجه از ${customer.name}").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (value > 0) viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { manager.receivePayment(customer.id, value, AccountSpinnerHelper.selectedAccountId(spinner, accounts), note.text.toString().trim()) }
                }
            }.show()
    }

    private fun adjustmentDialog(customer: Customer) {
        val box = formBox()
        val amount = field("مبلغ اصلاح (مثبت: افزایش طلب، منفی: کاهش طلب)")
        val note = field("علت اصلاح")
        box.addView(amount); box.addView(note)
        AlertDialog.Builder(requireContext()).setTitle("اصلاح حساب ${customer.name}").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (value != 0.0 && note.text.toString().trim().isNotBlank()) viewLifecycleOwner.lifecycleScope.launch {
                    manager.addAdjustment(customer.id, value, note.text.toString().trim())
                }
            }.show()
    }

    private fun reminderDialog(customer: Customer) {
        val box = formBox()
        val days = field("تعداد روز تا یادآوری (پیش‌فرض ۷)")
        val note = field("متن یادآوری (اختیاری)")
        box.addView(days); box.addView(note)
        AlertDialog.Builder(requireContext()).setTitle("یادآوری پیگیری ${customer.name}").setView(box)
            .setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
                val afterDays = (days.text.toString().toIntOrNull() ?: 7).coerceIn(1, 365)
                viewLifecycleOwner.lifecycleScope.launch {
                    SmartReminderManager(requireContext()).addReminder(ReminderEntity(
                        title = "پیگیری بدهی مشتری: ${customer.name}",
                        description = note.text.toString().trim().ifBlank { "مانده حساب مشتری را بررسی کنید." },
                        triggerTime = System.currentTimeMillis() + afterDays * 24L * 60 * 60 * 1000,
                        category = "مشتریان",
                        relatedPerson = customer.name,
                        contactPhoneNumber = customer.phoneNumber
                    ))
                }
            }.show()
    }

    private fun confirmDeleteEntry(entry: CustomerLedgerEntry) {
        AlertDialog.Builder(requireContext()).setTitle("حذف گردش حساب")
            .setMessage("این عملیات و اثر مالی وابسته به آن اصلاح می‌شود.")
            .setNegativeButton("لغو", null).setPositiveButton("حذف") { _, _ -> viewLifecycleOwner.lifecycleScope.launch { manager.deleteEntry(entry) } }.show()
    }

    private fun entryLabel(type: CustomerLedgerType) = when (type) { CustomerLedgerType.CREDIT_SALE -> "فروش نسیه"; CustomerLedgerType.PAYMENT -> "دریافت وجه"; CustomerLedgerType.ADJUSTMENT -> "اصلاح حساب" }
    private fun title(value: String) = TextView(requireContext()).apply { text = value; textSize = 20f; setTypeface(null, 1); setTextColor(color(R.color.text_primary)) }
    private fun note(value: String) = TextView(requireContext()).apply { text = value; setTextColor(color(R.color.text_secondary)); setPadding(16, 16, 16, 16) }
    private fun formBox() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 12, 36, 12) }
    private fun field(hint: String) = EditText(requireContext()).apply { this.hint = hint }
    private fun color(res: Int) = androidx.core.content.ContextCompat.getColor(requireContext(), res)
}
