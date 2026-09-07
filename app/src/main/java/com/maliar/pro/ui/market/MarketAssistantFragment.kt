package com.maliar.pro.ui.market

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.*
import com.maliar.pro.utils.MarketBackendClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Local-first Market Assistant. Built without a screen-level XML layout, but every
 * styled widget (cards/buttons/section header) is inflated from a tiny dedicated XML
 * resource with `style="@style/Widget.MaliarPro.*"` set declaratively. This is the
 * standard, guaranteed-safe way to apply those style resources to a view - unlike the
 * `ContextThemeWrapper(context, styleRes)` "style-as-theme-overlay" trick this fragment
 * used previously, which is not guaranteed to satisfy Material Components' internal
 * theme/attribute checks on every OEM build and was the suspected cause of a crash the
 * moment this screen opened.
 */
class MarketAssistantFragment : Fragment() {
    private val manager by lazy { MarketAssistantManager(requireContext()) }
    private lateinit var productsBox: LinearLayout
    private var products: List<MarketProduct> = emptyList()

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return try {
            buildScreen(inflater)
        } catch (t: Throwable) {
            // Never let a styling/inflation problem on this screen crash the whole app -
            // fall back to a minimal, unstyled but fully functional screen instead.
            android.util.Log.e("MarketAssistantFragment", "Falling back to plain layout", t)
            buildFallbackScreen()
        }
    }

    private fun buildScreen(inflater: android.view.LayoutInflater): View {
        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 48) }

        root.addView(heroCard(inflater, root))
        root.addView(actionsCard(inflater, root), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        root.addView(sectionHeader(inflater, root, "کالاهای ثبت‌شده"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        productsBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(productsBox)
        scroll.addView(root); return scroll
    }

    /** Plain-widget fallback with no custom styles at all, so this screen can never hard-crash. */
    private fun buildFallbackScreen(): View {
        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 48) }
        root.addView(TextView(requireContext()).apply { text = "بازاریار"; textSize = 22f; setTypeface(null, 1) })
        root.addView(TextView(requireContext()).apply { text = "قیمت خرید خود را با بازار عمده و خرده مقایسه کنید."; setPadding(0, 8, 0, 18) })
        listOf(
            "🔎 جستجوی قیمت بازار" to { chooseProduct { showMarket(it) } },
            "📦 کالاهای من" to { showAddProduct() },
            "🏪 منابع عمده و خرده" to { showSources() },
            "🤖 مشاور خرید" to { chooseProduct { showAdvice(it) } }
        ).forEach { (label, action) ->
            root.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
        root.addView(TextView(requireContext()).apply { text = "کالاهای ثبت‌شده"; textSize = 18f; setTypeface(null, 1); setPadding(0, 18, 0, 8) })
        productsBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(productsBox)
        scroll.addView(root); return scroll
    }

    private fun heroCard(inflater: android.view.LayoutInflater, parent: ViewGroup): MaterialCardView {
        val cardView = inflater.inflate(R.layout.view_market_hero_card, parent, false) as MaterialCardView
        val content = cardView.findViewById<LinearLayout>(R.id.marketHeroContent)
        content.addView(TextView(requireContext()).apply { text = "بازاریار"; textSize = 22f; setTypeface(null, 1); setTextColor(themeColor(R.color.text_primary)) })
        content.addView(TextView(requireContext()).apply { text = "قیمت خرید خود را با بازار عمده و خرده مقایسه کنید."; setTextColor(themeColor(R.color.text_secondary)); setPadding(0, 6, 0, 0) })
        return cardView
    }

    private fun actionsCard(inflater: android.view.LayoutInflater, parent: ViewGroup): MaterialCardView {
        val cardView = inflater.inflate(R.layout.view_market_card, parent, false) as MaterialCardView
        val content = cardView.findViewById<LinearLayout>(R.id.marketCardContent)
        listOf(
            "🔎 جستجوی قیمت بازار" to { chooseProduct { showMarket(it) } },
            "📦 کالاهای من" to { showAddProduct() },
            "🏪 منابع عمده و خرده" to { showSources() },
            "🤖 مشاور خرید" to { chooseProduct { showAdvice(it) } }
        ).forEachIndexed { index, (label, action) ->
            val button = inflater.inflate(if (index == 0) R.layout.view_market_button_primary else R.layout.view_market_button_secondary, content, false) as MaterialButton
            button.text = label
            button.setOnClickListener { action() }
            content.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (index == 0) 0 else 10 })
        }
        return cardView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { viewLifecycleOwner.lifecycleScope.launch { manager.products().collectLatest { items -> products = items; renderProducts() } } }

    private fun renderProducts() {
        productsBox.removeAllViews()
        if (products.isEmpty()) { productsBox.addView(TextView(requireContext()).apply { text = "هنوز کالایی ثبت نشده است."; setTextColor(themeColor(R.color.text_secondary)) }); return }
        val inflater = android.view.LayoutInflater.from(requireContext())
        products.forEach { p ->
            val row = try {
                val cardView = inflater.inflate(R.layout.view_market_transaction_card, productsBox, false) as MaterialCardView
                val content = cardView.findViewById<LinearLayout>(R.id.marketTransactionContent)
                content.addView(TextView(requireContext()).apply { text = p.name; textSize = 16f; setTypeface(null, 1); setTextColor(themeColor(R.color.text_primary)) })
                val meta = listOf(p.category, p.brand, p.model).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) content.addView(TextView(requireContext()).apply { text = meta; setTextColor(themeColor(R.color.text_secondary)); setPadding(0, 4, 0, 0) })
                cardView
            } catch (t: Throwable) {
                android.util.Log.e("MarketAssistantFragment", "Falling back to plain product row", t)
                TextView(requireContext()).apply { text = listOf(p.name, p.category, p.brand, p.model).filter { it.isNotBlank() }.joinToString(" · ") }
            }
            row.setOnClickListener { showMarket(p) }
            productsBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
    }

    private fun sectionHeader(inflater: android.view.LayoutInflater, parent: ViewGroup, label: String): TextView =
        TextView(requireContext(), null, 0, R.style.TextAppearance_MaliarPro_SectionHeader).apply { text = label }
    private fun themeColor(colorRes: Int) = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)

    private fun field(box: LinearLayout, hint: String, value: String = ""): EditText = EditText(requireContext()).also { it.hint = hint; it.setText(value); box.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }) }
    private fun showAddProduct() { val box = form(); val name = field(box, "نام کالا *"); val category = field(box, "دسته‌بندی"); val brand = field(box, "برند"); val model = field(box, "مدل"); val barcode = field(box, "بارکد"); AlertDialog.Builder(requireContext()).setTitle("کالای جدید").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> if (name.text.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { manager.addProduct(name.text.toString(), category.text.toString(), brand.text.toString(), model.text.toString(), barcode.text.toString()) } else toast("نام کالا الزامی است.") }.show() }
    private fun showMarket(product: MarketProduct) {
        val box = form()
        box.addView(TextView(requireContext()).apply { text = "«دریافت قیمت آنلاین» ترب و دیجی‌کالا (برای خرده) و کانال‌های تلگرامی شما را که در «منابع عمده و خرده» ثبت کرده‌اید جست‌وجو می‌کند."; setTextColor(themeColor(R.color.text_secondary)); setPadding(0, 0, 0, 12) })
        val kind = Spinner(requireContext()).also { it.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("عمده", "خرده")); box.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }) }
        box.addView((android.view.LayoutInflater.from(requireContext()).inflate(R.layout.view_market_button_secondary, box, false) as MaterialButton).apply {
            text = "دریافت قیمت آنلاین"
            setOnClickListener {
                val remoteType = if (kind.selectedItemPosition == 0) "wholesale" else "retail"
                val matchingKind = if (remoteType == "wholesale") "WHOLESALE" else "RETAIL"
                viewLifecycleOwner.lifecycleScope.launch {
                    val userSources = manager.sources().first().filter { it.isEnabled && it.priceType == matchingKind }.map { it.name to it.url }
                    val rows = MarketBackendClient.search(requireContext(), product.name, remoteType, userSources)
                    if (rows.isNullOrEmpty()) toast("نتیجه‌ای دریافت نشد؛ ممکن است ترب/دیجی‌کالا موقتاً مسدود باشند یا کانالی برای این نوع ثبت نکرده باشید.")
                    else { rows.filter { it.price > 0 }.forEach { manager.addQuote(product.id, it.source, if (it.priceType.lowercase() == "wholesale") "WHOLESALE" else "RETAIL", it.price, it.min, it.max, it.confidence) }; toast("${rows.size} قیمت بازار ثبت شد.") }
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        val price = field(box, "قیمت دستی (تومان)"); val source = field(box, "منبع (مثلاً ترب یا کانال فروشنده)")
        AlertDialog.Builder(requireContext()).setTitle(product.name).setView(box).setNeutralButton("ثبت خرید") { _, _ -> showPurchase(product) }.setNegativeButton("لغو", null).setPositiveButton("ثبت قیمت") { _, _ -> val amount = price.text.toString().cleanNumber(); if (amount != null && amount > 0) viewLifecycleOwner.lifecycleScope.launch { manager.addQuote(product.id, source.text.toString().ifBlank { "ثبت دستی" }, if (kind.selectedItemPosition == 0) "WHOLESALE" else "RETAIL", amount) } else toast("قیمت معتبر وارد کنید.") }.show()
    }
    private fun showPurchase(product: MarketProduct) { val box = form(); val price = field(box, "قیمت خرید هر واحد (تومان) *"); val quantity = field(box, "تعداد", "1"); val supplier = field(box, "فروشنده"); AlertDialog.Builder(requireContext()).setTitle("ثبت خرید: ${product.name}").setView(box).setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> val p = price.text.toString().cleanNumber(); val q = quantity.text.toString().cleanNumber() ?: 1.0; if (p != null && p > 0) viewLifecycleOwner.lifecycleScope.launch { manager.addPurchase(product.id, p, q, supplier.text.toString()) } else toast("قیمت معتبر وارد کنید.") }.show() }
    private fun showSources() {
        val box = form()
        box.addView(TextView(requireContext()).apply { text = "برای جست‌وجوی خودکار، آدرس کانال تلگرام را به‌صورت t.me/channel وارد کنید (فقط کانال‌های عمومی)."; setTextColor(themeColor(R.color.text_secondary)); setPadding(0, 0, 0, 10) })
        val name = field(box, "نام منبع *"); val url = field(box, "آدرس کانال (t.me/channel) یا لینک منبع *")
        val kind = Spinner(requireContext()).also { it.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("عمده", "خرده")); box.addView(it) }
        AlertDialog.Builder(requireContext()).setTitle("افزودن منبع").setView(box).setNeutralButton("ثبت پیام فروشنده") { _, _ -> showPasteMessage() }.setNegativeButton("لغو", null).setPositiveButton("ذخیره") { _, _ -> if (name.text.isNotBlank() && url.text.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { manager.addSource(name.text.toString(), url.text.toString(), if (kind.selectedItemPosition == 0) "WHOLESALE" else "RETAIL") } else toast("نام و آدرس الزامی است.") }.show()
    }
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
