package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.*
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A focused, ledger-backed gold/coin workspace. It shares assets, rates, AI and notifications with the rest of Maliar. */
class GoldPortfolioFragment : Fragment() {
    private val manager by lazy { GoldPortfolioManager(requireContext()) }
    private lateinit var root: LinearLayout
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        return ScrollView(requireContext()).apply { addView(root) }
    }
    override fun onViewCreated(view: View, state: Bundle?) { super.onViewCreated(view, state); render(); lifecycleScope.launch { manager.transactions().collect { render() } } }
    override fun onResume() { super.onResume(); lifecycleScope.launch { manager.syncAllAssets(); render() } }
    private fun render() = lifecycleScope.launch {
        root.removeAllViews()
        root.addView(title("🪙 طلا و سکه")); root.addView(TextView(requireContext()).apply { text = "کیف طلا، سود و زیان و ارزش روز با نرخ بازار"; setPadding(0, 4, 0, 16) })
        val positions = manager.positions(); val invested = positions.sumOf { it.invested }; val current = positions.sumOf { it.currentValue }; val profit = current - invested
        root.addView(TextView(requireContext()).apply { text = "ارزش کل: ${CurrencyFormatter.format(current)}\nسرمایه‌گذاری: ${CurrencyFormatter.format(invested)}\nسود/زیان: ${CurrencyFormatter.format(profit)} (${if (invested > 0) String.format("%.1f", profit / invested * 100) else "۰"}٪)"; textSize = 16f; setPadding(20, 18, 20, 18); setBackgroundColor(0xFFF4F0E8.toInt()) })
        actions()
        root.addView(title("کیف طلای من")); if (positions.isEmpty()) root.addView(TextView(requireContext()).apply { text = "هنوز معامله‌ای ثبت نشده است." })
        positions.forEach { p -> root.addView(TextView(requireContext()).apply { text = "${p.kind.label} · ${plain(p.quantity)} ${if (p.kind.isWeighted) "گرم" else "عدد"}\nمیانگین خرید ${CurrencyFormatter.format(p.averageCost)} · نرخ فعلی ${CurrencyFormatter.format(p.currentUnitPrice)}\nارزش ${CurrencyFormatter.format(p.currentValue)} · سود/زیان ${CurrencyFormatter.format(p.profit)} (${String.format("%.1f", p.returnPercent)}٪)"; textSize = 14f; setPadding(12, 16, 12, 16) }) }
        val tx = manager.transactions().first(); if (tx.isNotEmpty()) { root.addView(title("معاملات اخیر")); tx.take(12).forEach { t -> root.addView(TextView(requireContext()).apply { text = "${if (t.type == GoldTransactionType.BUY) "خرید" else "فروش"} ${t.kind.label} · ${plain(t.quantity)} × ${CurrencyFormatter.format(t.unitPrice)}"; setPadding(12, 7, 12, 7); setOnLongClickListener { lifecycleScope.launch { manager.delete(t) }; true } }) }
    }
    private fun actions() { listOf("➕ ثبت خرید یا فروش" to ::showTransaction, "🧮 ماشین حساب طلا" to ::showCalculator, "🔔 هشدار قیمت" to ::showAlert, "🤖 تحلیل هوشمند طلا" to ::showAi).forEach { (label, action) -> root.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }) } }
    private fun showTransaction() { val box = form(); val kind = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, GoldAssetKind.values().map { it.label }); box.addView(this) }; val type = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("خرید", "فروش")); box.addView(this) }; val qty = field(box, "وزن (گرم) یا تعداد"); val price = field(box, "قیمت هر گرم/عدد (تومان)"); val fee = field(box, "کارمزد (اختیاری)"); val note = field(box, "توضیحات"); androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("ثبت معامله طلا").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> val q=qty.text.toString().toDoubleOrNull()?:0.0; val p=price.text.toString().toDoubleOrNull()?:0.0; if(q>0&&p>=0) lifecycleScope.launch { manager.add(GoldTransaction(kind=GoldAssetKind.values()[kind.selectedItemPosition], type=if(type.selectedItemPosition==0) GoldTransactionType.BUY else GoldTransactionType.SELL, quantity=q, unitPrice=p, fee=fee.text.toString().toDoubleOrNull()?:0.0, notes=note.text.toString())) } }.show() }
    private fun showCalculator() { val box=form(); val amount=field(box,"سرمایه (تومان)"); val change=field(box,"تغییر احتمالی درصد (مثلاً ۱۰ یا -۱۰)"); val result=TextView(requireContext()); box.addView(result); androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("ماشین حساب طلا").setView(box).setNegativeButton("بستن",null).setPositiveButton("محاسبه"){_,_-> lifecycleScope.launch { val p=manager.currentPrice(GoldAssetKind.GOLD_18); val a=amount.text.toString().toDoubleOrNull()?:0.0; val c=change.text.toString().toDoubleOrNull()?:0.0; result.text="نرخ هر گرم: ${CurrencyFormatter.format(p)}\nبا این مبلغ: ${plain(if(p>0)a/p else 0.0)} گرم\nارزش پس از تغییر: ${CurrencyFormatter.format(a*(1+c/100))}" } }.show() }
    private fun showAlert() { val box=form(); val kind=Spinner(requireContext()).apply { adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,GoldAssetKind.values().map{it.label}); box.addView(this) }; val target=field(box,"قیمت هدف (تومان)"); val above=CheckBox(requireContext()).apply{text="وقتی بالاتر رسید";isChecked=true;box.addView(this)}; androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("هشدار قیمت").setView(box).setNegativeButton("لغو",null).setPositiveButton("ثبت"){_,_-> target.text.toString().toDoubleOrNull()?.takeIf{it>0}?.let { lifecycleScope.launch { manager.addAlert(GoldAssetKind.values()[kind.selectedItemPosition],it,above.isChecked) } } }.show() }
    private fun showAi() = lifecycleScope.launch { val p=manager.positions(); val text=p.joinToString("؛ "){"${it.kind.label}: ${plain(it.quantity)}، بازده ${String.format("%.1f",it.returnPercent)}٪"}; val result=AIHelper.generateText(requireContext(),"شما تحلیلگر محتاط سبد طلا هستید. فقط فارسی، حداکثر ۴ خط، بدون پیش‌بینی قطعی و بدون توصیه خرید یا فروش. در پایان بنویس این تحلیل توصیه مالی قطعی نیست.","سبد کاربر: $text") ?: "تحلیل هوشمند در دسترس نیست؛ کلید API فعال را بررسی کنید."; androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("تحلیل هوشمند طلا").setMessage(result).setPositiveButton("متوجه شدم",null).show() }
    private fun title(text:String)=TextView(requireContext()).apply{this.text=text;textSize=18f;setTypeface(null,1);setPadding(0,24,0,8)}
    private fun form()=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(42,8,42,0)}
    private fun field(box:LinearLayout,hint:String)=EditText(requireContext()).apply{this.hint=hint;inputType=2;box.addView(this)}
    private fun plain(v:Double)=if(v==v.toLong().toDouble())v.toLong().toString() else String.format("%.2f",v)
}
