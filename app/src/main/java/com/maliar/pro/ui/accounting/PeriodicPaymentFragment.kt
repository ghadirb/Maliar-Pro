package com.maliar.pro.ui.accounting

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.PeriodicPayment
import com.maliar.pro.database.PeriodicPaymentManager
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PeriodicPaymentFragment : Fragment() {
    private val manager by lazy { PeriodicPaymentManager(requireContext()) }
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

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
        val summary = TextView(requireContext()).apply { setPadding(0, 12, 0, 12) }
        root.addView(summary)
        val list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
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
                            "سررسید: ${dateFormat.format(Date(payment.nextPaymentAt))} · هر ${payment.periodDays} روز" +
                            if (!payment.isActive) "\nغیرفعال" else ""
                        setOnClickListener { showActions(payment) }
                    }
                    list.addView(row)
                }
            }
        }
        return root
    }

    private fun showAddDialog() {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
        fun field(hint: String): EditText = EditText(requireContext()).also { it.hint = hint; box.addView(it) }
        val title = field("عنوان (مثلاً اینترنت)")
        val amount = field("مبلغ تومان")
        val period = field("دوره به روز (۷، ۳۰، ۹۰ یا ۳۶۵)")
        val date = field("تاریخ بعدی میلادی (YYYY-MM-DD)")
        val category = field("دسته‌بندی")
        AlertDialog.Builder(requireContext()).setTitle("پرداخت دوره‌ای").setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val parsedAmount = amount.text.toString().replace(",", "").toDoubleOrNull()
                val parsedPeriod = period.text.toString().toIntOrNull()?.coerceAtLeast(1)
                val parsedDate = runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date.text.toString())?.time
                }.getOrNull()
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
                        category = category.text.toString().trim().ifBlank { "عمومی" }
                    ))
                }
            }.show()
    }

    private fun showActions(payment: PeriodicPayment) {
        val labels = if (payment.isActive) arrayOf("پرداخت شد؛ انتقال به موعد بعد", "حذف") else arrayOf("حذف")
        AlertDialog.Builder(requireContext()).setTitle(payment.title).setItems(labels) { _, which ->
            if (payment.isActive && which == 0) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val settled = manager.markPaid(payment.id)
                    Toast.makeText(requireContext(), if (settled == null) "این موعد قبلاً ثبت شده است." else "پرداخت ثبت شد.", Toast.LENGTH_SHORT).show()
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch { manager.delete(payment) }
            }
        }.show()
    }

    companion object { private const val DAY = 24L * 60 * 60 * 1000 }
}
