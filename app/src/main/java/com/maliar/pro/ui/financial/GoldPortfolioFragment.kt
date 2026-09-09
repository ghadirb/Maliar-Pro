package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.*
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoldPortfolioFragment : Fragment() {
    private val manager by lazy { GoldPortfolioManager(requireContext()) }
    private lateinit var content: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        return ScrollView(requireContext()).apply { addView(content) }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        render()
        viewLifecycleOwner.lifecycleScope.launch { manager.transactions().collect { render() } }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch { manager.syncAllAssets(); render() }
    }

    private fun render() {
        viewLifecycleOwner.lifecycleScope.launch {
            content.removeAllViews()
            content.addView(heading("🪙 طلا و سکه"))
            content.addView(TextView(requireContext()).apply { text = "کیف طلا، سود و زیان و ارزش روز با نرخ بازار"; setPadding(0, 4, 0, 16) })
            val positions = manager.positions()
            val invested = positions.sumOf { it.invested }
            val current = positions.sumOf { it.currentValue }
            val profit = current - invested
            content.addView(TextView(requireContext()).apply {
                text = "ارزش کل: ${CurrencyFormatter.format(current)}\nسرمایه‌گذاری: ${CurrencyFormatter.format(invested)}\nسود/زیان: ${CurrencyFormatter.format(profit)} (${percent(profit, invested)}٪)"
                textSize = 16f; setPadding(20, 18, 20, 18); setBackgroundColor(0xFFF4F0E8.toInt())
            })
            addAction("➕ ثبت خرید یا فروش") { showTransactionDialog() }
            addAction("🧮 ماشین حساب طلا") { showCalculatorDialog() }
            addAction("🔔 هشدار قیمت") { showAlertDialog() }
            addAction("🤖 تحلیل هوشمند طلا") { showAiAnalysis() }
            content.addView(heading("کیف طلای من"))
            if (positions.isEmpty()) content.addView(TextView(requireContext()).apply { text = "هنوز معامله‌ای ثبت نشده است." })
            positions.forEach { position ->
                content.addView(TextView(requireContext()).apply {
                    text = "${position.kind.label} · ${plain(position.quantity)} ${if (position.kind.isWeighted) "گرم" else "عدد"}\nمیانگین خرید ${CurrencyFormatter.format(position.averageCost)} · نرخ فعلی ${CurrencyFormatter.format(position.currentUnitPrice)}\nارزش ${CurrencyFormatter.format(position.currentValue)} · سود/زیان ${CurrencyFormatter.format(position.profit)} (${String.format("%.1f", position.returnPercent)}٪)"
                    textSize = 14f; setPadding(12, 16, 12, 16)
                })
            }
            val transactions = manager.transactions().first()
            if (transactions.isNotEmpty()) {
                content.addView(heading("معاملات اخیر"))
                transactions.take(12).forEach { transaction ->
                    content.addView(TextView(requireContext()).apply {
                        text = "${if (transaction.type == GoldTransactionType.BUY) "خرید" else "فروش"} ${transaction.kind.label} · ${plain(transaction.quantity)} × ${CurrencyFormatter.format(transaction.unitPrice)}"
                        setPadding(12, 7, 12, 7)
                        setOnLongClickListener { viewLifecycleOwner.lifecycleScope.launch { manager.delete(transaction) }; true }
                    })
                }
            }
        }
    }

    private fun addAction(label: String, action: () -> Unit) {
        content.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
    }

    private fun showTransactionDialog() {
        val box = form(); val kind = kindSpinner(box)
        val type = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("خرید", "فروش")); box.addView(this) }
        val quantity = field(box, "وزن (گرم) یا تعداد"); val price = field(box, "قیمت هر گرم/عدد (تومان)"); val fee = field(box, "کارمزد (اختیاری)"); val notes = field(box, "توضیحات")
        AlertDialog.Builder(requireContext()).setTitle("ثبت معامله طلا").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ ->
            val q = quantity.text.toString().toDoubleOrNull() ?: 0.0; val p = price.text.toString().toDoubleOrNull() ?: 0.0
            if (q > 0 && p >= 0) viewLifecycleOwner.lifecycleScope.launch {
                manager.add(GoldTransaction(kind = GoldAssetKind.values()[kind.selectedItemPosition], type = if (type.selectedItemPosition == 0) GoldTransactionType.BUY else GoldTransactionType.SELL, quantity = q, unitPrice = p, fee = fee.text.toString().toDoubleOrNull() ?: 0.0, notes = notes.text.toString()))
            }
        }.show()
    }

    private fun showCalculatorDialog() {
        val box = form(); val amount = field(box, "سرمایه (تومان)"); val change = field(box, "تغییر احتمالی درصد (مثلاً ۱۰ یا -۱۰)")
        AlertDialog.Builder(requireContext()).setTitle("ماشین حساب طلا").setView(box).setNegativeButton("بستن", null).setPositiveButton("محاسبه") { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                val price = manager.currentPrice(GoldAssetKind.GOLD_18); val capital = amount.text.toString().toDoubleOrNull() ?: 0.0; val delta = change.text.toString().toDoubleOrNull() ?: 0.0
                AlertDialog.Builder(requireContext()).setTitle("نتیجه ماشین حساب").setMessage("نرخ هر گرم: ${CurrencyFormatter.format(price)}\nبا این مبلغ: ${plain(if (price > 0) capital / price else 0.0)} گرم\nارزش پس از تغییر: ${CurrencyFormatter.format(capital * (1 + delta / 100))}").setPositiveButton("متوجه شدم", null).show()
            }
        }.show()
    }

    private fun showAlertDialog() {
        val box = form(); val kind = kindSpinner(box); val target = field(box, "قیمت هدف (تومان)")
        val above = CheckBox(requireContext()).apply { text = "وقتی بالاتر رسید"; isChecked = true; box.addView(this) }
        AlertDialog.Builder(requireContext()).setTitle("هشدار قیمت").setView(box).setNegativeButton("لغو", null).setPositiveButton("ثبت") { _, _ ->
            target.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { value -> viewLifecycleOwner.lifecycleScope.launch { manager.addAlert(GoldAssetKind.values()[kind.selectedItemPosition], value, above.isChecked) } }
        }.show()
    }

    private fun showAiAnalysis() {
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = manager.positions().joinToString("؛ ") { "${it.kind.label}: ${plain(it.quantity)}، بازده ${String.format("%.1f", it.returnPercent)}٪" }
            val response = AIHelper.generateText(requireContext(), "شما تحلیلگر محتاط سبد طلا هستید. فقط فارسی، حداکثر ۴ خط، بدون پیش‌بینی قطعی و بدون توصیه خرید یا فروش. در پایان بنویس این تحلیل توصیه مالی قطعی نیست.", "سبد کاربر: $summary") ?: "تحلیل هوشمند در دسترس نیست؛ کلید API فعال را بررسی کنید."
            AlertDialog.Builder(requireContext()).setTitle("تحلیل هوشمند طلا").setMessage(response).setPositiveButton("متوجه شدم", null).show()
        }
    }

    private fun kindSpinner(box: LinearLayout) = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, GoldAssetKind.values().map { it.label }); box.addView(this) }
    private fun heading(text: String) = TextView(requireContext()).apply { this.text = text; textSize = 18f; setTypeface(null, 1); setPadding(0, 24, 0, 8) }
    private fun form() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 8, 42, 0) }
    private fun field(box: LinearLayout, hint: String) = EditText(requireContext()).apply { this.hint = hint; inputType = 2; box.addView(this) }
    private fun plain(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)
    private fun percent(profit: Double, invested: Double) = if (invested > 0) String.format("%.1f", profit / invested * 100) else "۰"
}
