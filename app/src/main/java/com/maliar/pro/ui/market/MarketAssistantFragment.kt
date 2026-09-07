package com.maliar.pro.ui.market

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.*
import com.maliar.pro.utils.MarketBackendClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Local-first Market Assistant. Remote price retrieval is intentionally delegated to the API. */
class MarketAssistantFragment : Fragment() {
    private val manager by lazy { MarketAssistantManager(requireContext()) }
    private lateinit var productsBox: LinearLayout
    private var products: List<MarketProduct> = emptyList()

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 48) }
        root.addView(TextView(requireContext()).apply { text = "بازاریار"; textSize = 28f; setTypeface(null, 1) })
        root.addView(TextView(requireContext()).apply { text = "قیمت خرید خود را با بازار عمده و خرده مقایسه کنید."; setPadding(0, 8, 0, 18) })
        listOf("🔎 جستجوی قیمت بازار" to { chooseProduct { showMarket(it) } }, "📦 کالاهای من" to { showAddProduct() }, "🏪 منابع عمده و خرده" to { showSources() }, "🤖 مشاور خرید" to { chooseProduct { showAdvice(it) } }).forEach { (label, action) ->
            root.addView(Button(requireContext()).apply { text = label; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
        root.addView(TextView(requireContext()).apply { text = "کالاهای ثبت‌شده"; textSize = 18f; setTypeface(null, 1); setPadding(0, 18, 0, 8) })
        productsBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }; root.addView(productsBox)
        scroll.addView(root); return scroll
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { viewLifecycleOwner.lifecycleScope.launch { manager.products().collectLatest { items -> products = items; renderProducts() } } }
    private fun renderProducts() { productsBox.removeAllViews(); if (products.isEmpty()) productsBox.addView(TextView(requireContext()).apply { text = "هنوز کالایی ثبت نشده است." }); products.forEach { p -> productsBox.addView(Button(requireContext()).apply { text = p.name + listOf(p.brand, p.model).filter { it.isNotBlank() }.joinToString(" · ", prefix = "\n"); gravity = Gravity.START; setOnClickListener { showMarket(p) } }) } }
    private fun field(box: LinearLayout, hint: String, value: String = ""): EditText = EditText(requireContext()).also { it.hint = hint; it.setText(value); box.addView(it) }
    private fun showAddProduct() { val box = form(); val name = field(box, "نام کالا *"); val category = field(box, "دسته‌بندی"); val brand = field(box, "برند"); val model = field(box, "مدل"); val barcode = field(box, "بارکد"); AlertDialog.Builder(requireContext()).setTitle("کالای جدید").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> if (name.text.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { manager.addProduct(name.text.toString(), category.text.toString(), brand.text.toString(), model.text.toString(), barcode.text.toString()) } else toast("نام کالا الزامی است.") }.show() }
    private fun showMarket(product: MarketProduct) { val box = form(); box.addView(TextView(requireContext()).apply { text = "قیمت‌های دستی محلی‌اند؛ دریافت زنده فقط از مسیر Apps Script و آداپترهای مجاز انجام می‌شود." }); val kind = Spinner(requireContext()).also { it.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("عمده", "خرده")); box.addView(it) }; box.addView(Button(requireContext()).apply { text = "دریافت قیمت آنلاین"; setOnClickListener { val remoteType = if (kind.selectedItemPosition == 0) "wholesale" else "retail"; viewLifecycleOwner.lifecycleScope.launch { val rows = MarketBackendClient.search(requireContext(), product.name, remoteType); if (rows.isNullOrEmpty()) toast("نتیجه‌ای دریافت نشد؛ آداپتر منبع را در Apps Script فعال کنید.") else { rows.filter { it.price > 0 }.forEach { manager.addQuote(product.id, it.source, if (it.priceType.lowercase() == "wholesale") "WHOLESALE" else "RETAIL", it.price, it.min, it.max, it.confidence) }; toast("${rows.size} قیمت بازار ثبت شد.") } } } }); val price = field(box, "قیمت دستی (تومان)"); val source = field(box, "منبع (مثلاً ترب یا کانال فروشنده)"); AlertDialog.Builder(requireContext()).setTitle(product.name).setView(box).setNeutralButton("ثبت خرید") { _, _ -> showPurchase(product) }.setNegativeButton("لغو", null).setPositiveButton("ثبت قیمت") { _, _ -> val amount = price.text.toString().cleanNumber(); if (amount != null && amount > 0) viewLifecycleOwner.lifecycleScope.launch { manager.addQuote(product.id, source.text.toString().ifBlank { "ثبت دستی" }, if (kind.selectedItemPosition == 0) "WHOLESALE" else "RETAIL", amount) } else toast("قیمت معتبر وارد کنید.") }.show() }
    private fun showPurchase(product: MarketProduct) { val box = form(); val price = field(box, "قیمت خرید هر واحد (تومان) *"); val quantity = field(box, "تعداد", "1"); val supplier = field(box, "فروشنده"); AlertDialog.Builder(requireContext()).setTitle("ثبت خرید: ${product.name}").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> val p = price.text.toString().cleanNumber(); val q = quantity.text.toString().cleanNumber() ?: 1.0; if (p != null && p > 0) viewLifecycleOwner.lifecycleScope.launch { manager.addPurchase(product.id, p, q, supplier.text.toString()) } else toast("قیمت معتبر وارد کنید.") }.show() }
    private fun showSources() { val box = form(); val name = field(box, "نام منبع *"); val url = field(box, "آدرس کانال / لینک منبع *"); val kind = Spinner(requireContext()).also { it.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("عمده", "خرده")); box.addView(it) }; AlertDialog.Builder(requireContext()).setTitle("افزودن منبع").setView(box).setNeutralButton("ثبت پیام فروشنده") { _, _ -> showPasteMessage() }.setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> if (name.text.isNotBlank() && url.text.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { manager.addSource(name.text.toString(), url.text.toString(), if (kind.selectedItemPosition == 0) "WHOLESALE" else "RETAIL") } else toast("نام و آدرس الزامی است.") }.show() }
    private fun showPasteMessage() { chooseProduct { product -> val box = form(); val message = field(box, "متن پیام فروشنده"); AlertDialog.Builder(requireContext()).setTitle("خواندن پیام فروشنده").setView(box).setNegativeButton("لغو", null).setPositiveButton("استخراج و ثبت") { _, _ -> val prices = MarketAssistantManager.pricesFromText(message.text.toString()); if (prices.isEmpty()) toast("قیمت قابل تشخیصی پیدا نشد.") else viewLifecycleOwner.lifecycleScope.launch { prices.forEach { manager.addQuote(product.id, "پیام فروشنده", "WHOLESALE", it, prices.minOrNull(), prices.maxOrNull(), .45) }; toast("${prices.size} قیمت ثبت شد.") } }.show() } }
    private fun showAdvice(product: MarketProduct) { viewLifecycleOwner.lifecycleScope.launch {
        val purchases = manager.purchases(product.id).first()
        val quotes = manager.quotes(product.id).first()
        AlertDialog.Builder(requireContext()).setTitle("مشاور خرید: ${product.name}")
            .setMessage(MarketAssistantManager.recommendation(purchases, quotes)).setPositiveButton("متوجه شدم", null).show()
    } }
    private fun chooseProduct(action: (MarketProduct) -> Unit) { if (products.isEmpty()) { toast("ابتدا یک کالا ثبت کنید."); return }; AlertDialog.Builder(requireContext()).setTitle("انتخاب کالا").setItems(products.map { it.name }.toTypedArray()) { _, i -> action(products[i]) }.show() }
    private fun form() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 0, 36, 0) }
    private fun String.cleanNumber(): Double? = replace(",", "").replace("٫", "").map { if (it in '۰'..'۹') ('0'.code + it.code - '۰'.code).toChar() else it }.joinToString("").toDoubleOrNull()
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
}
