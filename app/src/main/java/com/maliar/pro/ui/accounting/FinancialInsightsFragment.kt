package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.BudgetManager
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Local, explainable financial insights. Each message is explicitly labelled and tied to data. */
class FinancialInsightsFragment : Fragment() {
    private val accounting by lazy { AccountingManager(requireContext()) }
    private val financial by lazy { FinancialStatusManager(requireContext()) }
    private lateinit var box: LinearLayout
    override fun onCreateView(i: android.view.LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 48) }
        return ScrollView(requireContext()).apply { addView(box) }
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        lifecycleScope.launch {
            combine(accounting.getAllExpenses(), accounting.getAllIncomes(), financial.getAllDebts(), financial.getAllGoals()) { e, i, d, g -> arrayOf(e, i, d, g) }
                .collect { values ->
                    @Suppress("UNCHECKED_CAST") render(values[0] as List<com.maliar.pro.database.Expense>, values[1] as List<com.maliar.pro.database.Income>, values[2] as List<com.maliar.pro.database.Debt>, values[3] as List<com.maliar.pro.database.FinancialGoal>)
                }
        }
    }
    private fun render(expenses: List<com.maliar.pro.database.Expense>, incomes: List<com.maliar.pro.database.Income>, debts: List<com.maliar.pro.database.Debt>, goals: List<com.maliar.pro.database.FinancialGoal>) {
        box.removeAllViews(); box.addView(title("تحلیل مالی هوشمند"))
        box.addView(note("این بینش‌ها تقریبی و فقط بر اساس داده‌های ثبت‌شده در برنامه هستند؛ توصیهٔ قطعی مالی نیستند."))
        val start = accounting.getFinancialPeriodStartMillis()
        val current = expenses.filter { it.date >= start }
        val previousStart = start - (System.currentTimeMillis() - start).coerceAtLeast(24L * 60 * 60 * 1000)
        val previous = expenses.filter { it.date in previousStart until start }
        if (current.size < 2) add("دادهٔ کافی نیست", "برای تحلیل قابل‌اتکا حداقل چند هزینه در دورهٔ جاری لازم است.", "برچسب: بر اساس داده‌های ثبت‌شده")
        else {
            val currentTotal = current.sumOf { it.amount }; val previousTotal = previous.sumOf { it.amount }
            if (previousTotal > 0.0) {
                val change = ((currentTotal - previousTotal) / previousTotal * 100).toInt()
                add("مقایسه با دورهٔ قبل", "هزینهٔ دورهٔ جاری ${kotlin.math.abs(change)}٪ ${if (change >= 0) "بیشتر" else "کمتر"} است.", "برچسب: تقریبی · دادهٔ مرتبط: ${current.size} تراکنش")
            }
            val olderStart = previousStart - (start - previousStart)
            val older = expenses.filter { it.date in olderStart until previousStart }
            if (older.isNotEmpty()) add("مقایسه با میانگین دوره‌های اخیر", "میانگین هزینهٔ سه بازهٔ اخیر ${CurrencyFormatter.format((currentTotal + previousTotal + older.sumOf { it.amount }) / 3)} است.", "برچسب: تقریبی · مشاهدهٔ داده‌های هزینه")
            current.groupBy { it.category.ifBlank { "عمومی" } }.maxByOrNull { it.value.sumOf { x -> x.amount } }?.let { (category, rows) ->
                add("بیشترین دستهٔ هزینه", "دستهٔ «$category» با ${CurrencyFormatter.format(rows.sumOf { it.amount })} بیشترین سهم را دارد.", "برچسب: پیشنهاد · ${rows.size} تراکنش مرتبط", rows)
            }
            val avg = current.sumOf { it.amount } / current.size
            current.maxByOrNull { it.amount }?.takeIf { it.amount > avg * 2 }?.let { add("هزینهٔ غیرعادی", "هزینهٔ «${it.description.ifBlank { it.category }}» بیش از دو برابر میانگین این دوره است؛ بررسی آن پیشنهاد می‌شود.", "برچسب: پیشنهاد · تراکنش مرتبط", listOf(it)) }
        }
        val income = incomes.filter { it.date >= start }.sumOf { it.profit }; val expense = current.sumOf { it.amount }
        add("وضعیت پس‌انداز", "خالص دورهٔ جاری: ${CurrencyFormatter.format(income - expense)}.", "برچسب: تقریبی · درآمد: ${CurrencyFormatter.format(income)} · هزینه: ${CurrencyFormatter.format(expense)}")
        val unpaid = debts.filter { !it.isPaid }.sumOf { it.amount }
        if (unpaid > 0) add("بدهی ثبت‌شده", "${CurrencyFormatter.format(unpaid)} بدهی پرداخت‌نشده در برنامه ثبت شده است.", "برچسب: بر اساس داده‌های ثبت‌شده")
        goals.filter { !it.isCompleted }.maxByOrNull { it.targetAmount - it.currentProgress }?.let { goal -> add("وضعیت هدف مالی", "برای هدف «${goal.title}» هنوز ${CurrencyFormatter.format((goal.targetAmount - goal.currentProgress).coerceAtLeast(0.0))} باقی مانده است.", "برچسب: بر اساس داده‌های ثبت‌شده") }
        val projected = income - expense
        if (projected < 0) add("خطر کمبود تراز", "خالص فعلی دوره منفی است؛ اگر روند ثبت‌شده ادامه یابد، کسری تراز محتمل است.", "برچسب: تقریبی · مشاهدهٔ داده‌های درآمد و هزینه")
    }
    private fun add(h: String, body: String, tag: String, related: List<com.maliar.pro.database.Expense> = emptyList()) { box.addView(LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); isClickable = related.isNotEmpty(); setOnClickListener { if (related.isNotEmpty()) androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("تراکنش‌های مرتبط").setMessage(related.take(10).joinToString("\n") { "${it.category}: ${CurrencyFormatter.format(it.amount)} · ${it.description}" }).setPositiveButton("بستن", null).show() }; addView(title(h).apply { textSize = 16f }); addView(note(body)); addView(note(tag + if (related.isNotEmpty()) " · لمس برای مشاهده" else "")) }) }
    private fun title(t: String) = TextView(requireContext()).apply { text = t; textSize = 21f; setTypeface(null, 1); setTextColor(requireContext().getColor(R.color.text_primary)) }
    private fun note(t: String) = TextView(requireContext()).apply { text = t; textSize = 13f; setPadding(0, 5, 0, 5); setTextColor(requireContext().getColor(R.color.text_secondary)) }
}
