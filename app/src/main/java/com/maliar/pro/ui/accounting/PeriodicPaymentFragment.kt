package com.maliar.pro.ui.accounting

import android.text.Editable
import android.text.TextWatcher

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.PeriodicPayment
import com.maliar.pro.database.PeriodicPaymentManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.launch

class PeriodicPaymentFragment : Fragment() {
    private val manager by lazy { PeriodicPaymentManager(requireContext()) }
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private lateinit var summary: TextView
    private lateinit var list: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 12)
        }
        val add = android.widget.Button(requireContext()).apply {
            text = "افزودن پرداخت دوره‌ای"
            setOnClickListener { showAddDialog() }
        }
        root.addView(add)
        summary = TextView(requireContext()).apply { setPadding(0, 12, 0, 12) }
        root.addView(summary)
        list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            manager.getAll().collect { payments ->
                list.removeAllViews()
                val upcoming = payments.filter { it.isActive && it.nextPaymentAt <= System.currentTimeMillis() + 30L * DAY }
                summary.text = if (upcoming.isEmpty()) "پرداخت دوره‌ای ثبت نشده است."
                else "تعهدات ۳۰ روز آینده: ${CurrencyFormatter.format(upcoming.sumOf { it.amount }, "")} تومان"
                payments.forEach { payment ->
                    val row = TextView(requireContext()).apply {
                        setPadding(0, 10, 0, 10)
                        text = "${payment.title} · ${CurrencyFormatter.format(payment.amount, "")} تومان\n" +
                            "سررسید: ${formatJalaliDate(payment.nextPaymentAt)} · هر ${payment.periodDays} روز" +
                            if (!payment.isActive) "\nغیرفعال" else ""
                        setOnClickListener { showActions(payment) }
                    }
                    list.addView(row)
                }
            }
        }
    }

    private fun showAddDialog() {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
        fun field(hint: String): EditText = EditText(requireContext()).also { it.hint = hint; box.addView(it) }
        val title = field("عنوان (مثلاً اینترنت)")
        val amount = field("مبلغ تومان")
        val period = field("دوره به روز (۷، ۳۰، ۹۰ یا ۳۶۵) - برای اجاره خودکار تشخیص می‌شود")
        val reminderDays = field("چند روز قبل یادآوری شود؟").also { it.setText("1") }
        // Auto-detect period from title for rent
        title.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: ""
                val detected = PeriodicPaymentManager.detectPeriodDays(t, "")
                if (detected != null) {
                    period.setText(detected.toString())
                }
            }
        })
        val (year, month, day) = PersianCalendarHelper.getCurrentJalaliDate()
        val date = field("تاریخ بعدی شمسی (مثلاً ۱۴۰۵/۰۶/۱۵)").also { it.setText("$year/$month/$day") }
        val category = field("دسته‌بندی")
        val notes = field("توضیحات (اختیاری)")
        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = financialManager.getAllAssetsList()
            val accountSpinner = Spinner(requireContext()).apply {
                adapter = android.widget.ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("حساب پیش‌فرض") + accounts.map { it.title }
                )
                box.addView(this)
            }
            AlertDialog.Builder(requireContext()).setTitle("پرداخت دوره‌ای").setView(box)
                .setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره") { _, _ ->
                val parsedAmount = amount.text.toString().replace(",", "").toDoubleOrNull()
                val parsedPeriod = period.text.toString().toIntOrNull()?.coerceAtLeast(1)
                val parsedReminderDays = reminderDays.text.toString().toIntOrNull()?.coerceIn(0, 30) ?: 1
                val parsedDate = parseJalaliDate(date.text.toString())
                if (title.text.isBlank() || parsedAmount == null || parsedAmount <= 0 || parsedDate == null) {
                    Toast.makeText(requireContext(), "عنوان، مبلغ و تاریخ صحیح الزامی است.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    manager.save(PeriodicPayment(
                        title = title.text.toString().trim(),
                        amount = parsedAmount,
                        periodDays = parsedPeriod ?: 30,
                        nextPaymentAt = parsedDate,
                        category = category.text.toString().trim().ifBlank { "عمومی" },
                        accountId = accountSpinner.selectedItemPosition.takeIf { it > 0 }?.let { accounts[it - 1].id },
                        notes = notes.text.toString().trim(),
                        reminderDaysBefore = parsedReminderDays
                    ))
                }
            }.show()
        }
    }

    private fun formatJalaliDate(millis: Long): String {
        val (year, month, day) = PersianCalendarHelper.gregorianMillisToJalali(millis)
        return "$year/$month/$day"
    }

    private fun parseJalaliDate(value: String): Long? {
        val normalized = value
            .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
            .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')
        val parts = normalized.trim().split(Regex("[/\\-]"))
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 1300..1600 || month !in 1..12 || day !in 1..PersianCalendarHelper.daysInJalaliMonth(year, month)) return null
        return PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
    }

    private fun showActions(payment: PeriodicPayment) {
        val labels = if (payment.isActive) arrayOf("پرداخت شد؛ ثبت هزینه و انتقال به موعد بعد", "غیرفعال‌کردن", "حذف") else arrayOf("فعال‌کردن", "حذف")
        AlertDialog.Builder(requireContext()).setTitle(payment.title).setItems(labels) { _, which ->
            if (payment.isActive && which == 0) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val settled = manager.markPaid(payment.id)
                    Toast.makeText(requireContext(), if (settled == null) "این موعد قبلاً ثبت شده است." else "پرداخت ثبت شد.", Toast.LENGTH_SHORT).show()
                }
            } else if (payment.isActive && which == 1) {
                viewLifecycleOwner.lifecycleScope.launch { manager.setActive(payment.id, false) }
            } else if (!payment.isActive && which == 0) {
                viewLifecycleOwner.lifecycleScope.launch { manager.setActive(payment.id, true) }
            } else {
                viewLifecycleOwner.lifecycleScope.launch { manager.delete(payment) }
            }
        }.show()
    }

    companion object { private const val DAY = 24L * 60 * 60 * 1000 }
}



