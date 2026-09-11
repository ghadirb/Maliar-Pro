package com.maliar.pro.ui.mealplan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.MealPlanEntry
import com.maliar.pro.database.MealPlanManager
import com.maliar.pro.database.FoodPriceManager
import com.maliar.pro.database.MarketAssistantManager
import com.maliar.pro.database.ShoppingList
import com.maliar.pro.databinding.FragmentMealPlanBinding
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.MarketBackendClient
import com.maliar.pro.utils.MealType
import com.maliar.pro.utils.PreferencesManager
import com.maliar.pro.utils.RecipeCatalog
import com.maliar.pro.viewmodels.MealPlanViewModel
import com.maliar.pro.viewmodels.MealPlanViewModelFactory
import com.maliar.pro.viewmodels.PERSIAN_WEEKDAY_NAMES
import com.maliar.pro.viewmodels.weekStartMillis
import kotlinx.coroutines.launch

class MealPlanFragment : Fragment() {

    private lateinit var binding: FragmentMealPlanBinding
    private val viewModel: MealPlanViewModel by viewModels {
        MealPlanViewModelFactory(MealPlanManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMealPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.generatePlanButton.setOnClickListener {
            val budget = binding.budgetInput.text.toString().toDoubleOrNull() ?: 0.0
            requestOnlinePlanOrFallback(weekStartMillis(), budget)
            binding.shoppingListCard.visibility = View.GONE
        }
        binding.managePricesButton.setOnClickListener { showPriceManager() }

        binding.shoppingListButton.setOnClickListener {
            val planId = viewModel.latestPlan.value?.id ?: return@setOnClickListener
            if (binding.shoppingListCard.visibility == View.VISIBLE) {
                binding.shoppingListCard.visibility = View.GONE
            } else {
                viewModel.loadShoppingList(planId)
            }
        }

        binding.aiTipsButton.setOnClickListener { requestAiTips() }

        lifecycleScope.launch {
            viewModel.latestPlan.collect { plan ->
                binding.budgetInput.setText(if (plan != null && plan.budget > 0) plan.budget.toLong().toString() else "")
                binding.shoppingListButton.visibility = if (plan != null) View.VISIBLE else View.GONE
                val hasActiveAiKey = PreferencesManager(requireContext()).getAPIKeys().any { it.isActive }
                binding.aiTipsButton.visibility = if (plan != null && hasActiveAiKey) View.VISIBLE else View.GONE
                binding.aiTipsText.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.isGenerating.collect { generating ->
                binding.generatePlanButton.isEnabled = !generating
                binding.generatePlanButton.text = if (generating) "در حال ساخت برنامه..." else "ساخت / بازسازی برنامهٔ این هفته"
            }
        }

        lifecycleScope.launch {
            viewModel.entries.collect { entries -> renderPlan(entries) }
        }

        lifecycleScope.launch {
            viewModel.shoppingList.collect { list -> renderShoppingList(list) }
        }
    }

    private fun requestOnlinePlanOrFallback(weekStart: Long, budget: Double) {
        lifecycleScope.launch {
            val prompt = "یک برنامه غذایی هفتگی فارسی تولید کن. فقط JSON معتبر با کلید entries بده؛ هر entry شامل day (0 تا 6)، mealType (BREAKFAST/LUNCH/DINNER/SNACK)، recipeName و estimatedCost عددی به تومان باشد. برای هر روز صبحانه، ناهار، شام و میان‌وعده پیشنهاد بده. بودجه هفتگی: $budget"
            val raw = AIHelper.generateText(requireContext(), "برنامه‌ریز غذای خانوادگی هستی. فقط JSON معتبر و بدون توضیح.", prompt)
            val parsed = raw?.let { parseOnlineEntries(it) }
            if (parsed.isNullOrEmpty()) viewModel.generatePlan(weekStart, budget)
            else viewModel.saveOnlinePlan(weekStart, budget, parsed)
        }
    }

    private fun parseOnlineEntries(raw: String): List<MealPlanEntry> = runCatching {
        val arrayText = raw.substringAfter("[").substringBeforeLast("]", "")
        if (arrayText.isBlank()) return@runCatching emptyList()
        val itemRegex = Regex("\\{[^{}]*\\}")
        itemRegex.findAll(arrayText).mapNotNull { match ->
            val obj = org.json.JSONObject(match.value)
            val day = obj.optInt("day", -1)
            val type = obj.optString("mealType", "").uppercase()
            val name = obj.optString("recipeName", "").trim()
            if (day !in 0..6 || type !in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK") || name.isBlank() || RecipeCatalog.RECIPES.none { it.name == name }) null
            else MealPlanEntry(dayOfWeek = day, mealType = type, recipeName = name, estimatedCost = obj.optDouble("estimatedCost", 0.0), mealPlanId = 0)
        }.toList().takeIf { it.size >= 7 }
    }.getOrDefault(emptyList())

    private fun renderPlan(entries: List<MealPlanEntry>) {
        binding.daysContainer.removeAllViews()
        binding.emptyStateText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.costSummaryText.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        if (entries.isEmpty()) return

        val totalCost = entries.sumOf { it.estimatedCost }
        val budget = viewModel.latestPlan.value?.budget ?: 0.0
        binding.costSummaryText.text = if (budget > 0) {
            val status = if (totalCost <= budget) "✅ در محدودهٔ بودجه" else "⚠️ بیشتر از بودجهٔ تعیین‌شده"
            "هزینهٔ تقریبی برنامه: ${formatCurrency(totalCost)} از بودجهٔ ${formatCurrency(budget)} — $status"
        } else {
            "هزینهٔ تقریبی برنامه: ${formatCurrency(totalCost)}"
        }

        val byDay = entries.groupBy { it.dayOfWeek }.toSortedMap()
        val mealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)

        byDay.forEach { (day, dayEntries) ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_meal_day, binding.daysContainer, false) as MaterialCardView
            card.findViewById<TextView>(R.id.dayNameText).text = PERSIAN_WEEKDAY_NAMES.getOrElse(day) { "" }

            val rows = card.findViewById<LinearLayout>(R.id.mealRowsContainer)
            dayEntries.sortedBy { mealOrder.indexOf(MealType.valueOf(it.mealType)) }.forEach { entry ->
                val row = TextView(requireContext()).apply {
                    val label = MealType.valueOf(entry.mealType).label
                    text = "$label: ${entry.recipeName}  (${formatCurrency(entry.estimatedCost)})"
                    textSize = 13f
                    setPadding(0, 4, 0, 4)
                    setOnClickListener { showEditEntry(entry) }
                }
                rows.addView(row)
            }
            binding.daysContainer.addView(card)
        }
    }

    private fun showEditEntry(entry: MealPlanEntry) {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 8) }
        val name = EditText(requireContext()).apply { hint = "نام غذا"; setText(entry.recipeName); setSingleLine(true) }
        val cost = EditText(requireContext()).apply { hint = "هزینه تقریبی"; setText(entry.estimatedCost.toLong().toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine(true) }
        box.addView(name); box.addView(cost)
        AlertDialog.Builder(requireContext()).setTitle("ویرایش وعده").setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ ->
                if (name.text.isNotBlank()) viewModel.updateEntry(entry, name.text.toString(), cost.text.toString().toDoubleOrNull() ?: entry.estimatedCost)
            }.show()
    }

    private fun showPriceManager() {
        val manager = FoodPriceManager(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
            textDirection = View.TEXT_DIRECTION_RTL
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "عنوان کالا یا ماده غذایی"
            setSingleLine(true)
        }
        val priceInput = EditText(requireContext()).apply {
            hint = "قیمت هر واحد (تومان)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
        val unitInput = EditText(requireContext()).apply {
            hint = "واحد (مثلاً کیلو یا بسته)"
            setSingleLine(true)
        }
        val saveButton = Button(requireContext()).apply { text = "افزودن قیمت" }
        val entriesContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(nameInput)
        root.addView(priceInput)
        root.addView(unitInput)
        root.addView(saveButton)
        root.addView(entriesContainer)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("مدیریت قیمت‌ها")
            .setView(ScrollView(requireContext()).apply { addView(root) })
            .setNegativeButton("بستن", null)
            .create()
        var editingId = 0L
        saveButton.setOnClickListener {
            val price = priceInput.text.toString().toDoubleOrNull()
            lifecycleScope.launch {
                if (manager.upsert(nameInput.text.toString(), price ?: -1.0, unitInput.text.toString(), editingId)) {
                    nameInput.text.clear()
                    priceInput.text.clear()
                    unitInput.text.clear()
                    editingId = 0L
                    saveButton.text = "افزودن قیمت"
                } else {
                    nameInput.error = "عنوان و قیمت معتبر وارد کنید"
                }
            }
        }
        val listJob = lifecycleScope.launch {
            manager.getAll().collect { items ->
                entriesContainer.removeAllViews()
                if (items.isEmpty()) {
                    entriesContainer.addView(TextView(requireContext()).apply {
                        text = "هنوز قیمتی ثبت نشده است."
                        setPadding(0, 24, 0, 16)
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                    })
                }
                items.forEach { item ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 16, 0, 16)
                    }
                    val label = TextView(requireContext()).apply {
                        text = "${item.name} — ${item.pricePerUnit.toLong()} تومان" +
                            if (item.unitLabel.isBlank()) "" else " / ${item.unitLabel}"
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val edit = Button(requireContext()).apply { text = "ویرایش" }
                    val delete = Button(requireContext()).apply { text = "حذف" }
                    edit.setOnClickListener {
                        editingId = item.id
                        nameInput.setText(item.name)
                        priceInput.setText(item.pricePerUnit.toString())
                        unitInput.setText(item.unitLabel)
                        saveButton.text = "ذخیره تغییرات"
                    }
                    delete.setOnClickListener {
                        lifecycleScope.launch { manager.delete(item) }
                    }
                    val actions = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    actions.addView(edit)
                    actions.addView(delete)
                    row.addView(label)
                    row.addView(actions)
                    entriesContainer.addView(row)
                }
            }
        }
        dialog.setOnDismissListener { listJob.cancel() }
        dialog.show()
    }

    private fun renderShoppingList(list: ShoppingList?) {
        if (list == null) {
            binding.shoppingListCard.visibility = View.GONE
            return
        }
        binding.shoppingListCard.visibility = View.VISIBLE
        binding.shoppingListContainer.removeAllViews()
        binding.shoppingListEmptyText.visibility = if (list.items.isEmpty()) View.VISIBLE else View.GONE
        binding.shoppingListTotalText.text = "مجموع تقریبی: ${formatCurrency(list.totalCost)}"

        list.items.forEach { item ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_shopping_list_row, binding.shoppingListContainer, false)
            row.findViewById<TextView>(R.id.itemNameText).text =
                "${item.ingredientName} · ${formatUnits(item.approxUnits)} ${item.unitLabel}"
            row.findViewById<TextView>(R.id.itemPriceNoteText).text = when (item.unitPrice.source) {
                com.maliar.pro.database.FoodPriceSource.MANUAL -> "قیمت ثبت‌شده توسط شما"
                com.maliar.pro.database.FoodPriceSource.EXPENSE_HISTORY -> "بر اساس آخرین خرید شما"
                com.maliar.pro.database.FoodPriceSource.MARKET_CHECK -> "قیمت تقریبی از آخرین بررسی بازار"
                com.maliar.pro.database.FoodPriceSource.CATALOG_ESTIMATE -> "قیمت تقریبی؛ برای دقت بیشتر قیمت را وارد یا بازار را بررسی کنید"
            }
            row.findViewById<TextView>(R.id.itemCostText).text = formatCurrency(item.totalCost)
            // Only the weakest tier (static catalog fallback) gets an online-check
            // affordance - manual/history/market-check prices are already better than a
            // fresh AI guess, so offering it there would be noise, not help.
            val checkButton = row.findViewById<TextView>(R.id.itemCheckOnlineText)
            if (item.unitPrice.source == com.maliar.pro.database.FoodPriceSource.CATALOG_ESTIMATE) {
                checkButton.visibility = View.VISIBLE
                checkButton.setOnClickListener { checkPriceOnline(item.ingredientName, checkButton) }
            } else {
                checkButton.visibility = View.GONE
            }
            binding.shoppingListContainer.addView(row)
        }
    }

    /**
     * User-initiated only (tapping 🔎 next to one shopping-list item) - never triggered
     * automatically while building a plan. Reuses the same local-first product price
     * engine ("قیمت کالاها" / بازاریار) that the rest of the app uses: it checks
     * Torob/Digikala first and falls back to the AI (grok-4) web-search proxy only if
     * those come back empty, then saves the result so this and future plans pick it up
     * as a "بررسی بازار" price instead of the static catalog guess.
     */
    private fun checkPriceOnline(ingredientName: String, trigger: TextView) {
        trigger.isEnabled = false
        trigger.text = "…"
        lifecycleScope.launch {
            val context = requireContext()
            val manager = MarketAssistantManager(context)
            val product = manager.ensureProduct(ingredientName, category = "خوراکی")
            val result = product?.let { MarketBackendClient.search(context, ingredientName, "retail") }
            if (product != null && result != null && result.quotes.isNotEmpty()) {
                result.quotes.filter { it.price > 0 }.forEach {
                    manager.addQuote(product.id, it.source, "RETAIL", it.price, it.min, it.max, it.confidence)
                }
                viewModel.latestPlan.value?.id?.let { viewModel.loadShoppingList(it) }
            } else {
                trigger.isEnabled = true
                trigger.text = "🔎"
                val message = result?.errorMessage ?: "قیمتی برای «$ingredientName» پیدا نشد."
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Optional, user-initiated only (never called automatically) AI tips for the current
     * week's plan. Reuses AIHelper.generateText - the same already-reviewed code path the
     * in-app assistant uses (the person's own stored API key, or the app's rate-limited
     * proxy) - rather than adding any new network/key-handling code. Only ever sends the
     * recipe names, meal types and aggregate costs already visible on this screen - no
     * personal, financial-account, or location data leaves the device for this feature.
     */
    private fun requestAiTips() {
        val entries = viewModel.entries.value
        if (entries.isEmpty()) return
        val budget = viewModel.latestPlan.value?.budget ?: 0.0
        val totalCost = entries.sumOf { it.estimatedCost }

        binding.aiTipsButton.isEnabled = false
        binding.aiTipsButton.text = "در حال دریافت نکات..."
        binding.aiTipsText.visibility = View.GONE

        lifecycleScope.launch {
            val recipeList = entries.joinToString("، ") { "${MealType.valueOf(it.mealType).label}: ${it.recipeName}" }
            val budgetLine = if (budget > 0) "بودجهٔ هفتگی: ${formatCurrency(budget)}." else "بودجه‌ای تعیین نشده."
            val userPrompt = "برنامهٔ غذایی این هفته: $recipeList. هزینهٔ تقریبی کل: ${formatCurrency(totalCost)}. $budgetLine"
            val systemPrompt = "شما یک دستیار برنامه‌ریزی غذایی خانگی فارسی‌زبان هستید. بر اساس برنامهٔ هفتگی زیر، " +
                "حداکثر ۳ نکتهٔ خیلی کوتاه (هرکدام یک خط) برای صرفه‌جویی یا تنوع بیشتر پیشنهاد بده. فقط فارسی، بدون مقدمه."

            val result = AIHelper.generateText(requireContext(), systemPrompt, userPrompt)

            binding.aiTipsButton.isEnabled = true
            binding.aiTipsButton.text = "🧠 نکات هوشمند برای این هفته (اختیاری)"
            binding.aiTipsText.visibility = View.VISIBLE
            binding.aiTipsText.text = result
                ?: "دریافت نکات هوشمند ممکن نشد. لطفاً یک کلید API فعال در پروفایل ← کلیدهای API بررسی کنید."
        }
    }

    private fun formatUnits(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

    private fun formatCurrency(amount: Double): String = com.maliar.pro.utils.CurrencyFormatter.format(amount, "ت")
}
