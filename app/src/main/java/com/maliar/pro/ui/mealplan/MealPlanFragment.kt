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
import com.maliar.pro.database.ShoppingList
import com.maliar.pro.databinding.FragmentMealPlanBinding
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.MealType
import com.maliar.pro.utils.PreferencesManager
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
            viewModel.generatePlan(weekStartMillis(), budget)
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
                }
                rows.addView(row)
            }
            binding.daysContainer.addView(card)
        }
    }

    private fun showPriceManager() {
        val manager = FoodPriceManager(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "عنوان کالا یا ماده غذایی"
            singleLine = true
        }
        val priceInput = EditText(requireContext()).apply {
            hint = "قیمت هر واحد (تومان)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            singleLine = true
        }
        val unitInput = EditText(requireContext()).apply {
            hint = "واحد (مثلاً کیلو یا بسته)"
            singleLine = true
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
                items.forEach { item ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                    }
                    val label = TextView(requireContext()).apply {
                        text = "${item.name} — ${item.pricePerUnit.toLong()} تومان" +
                            if (item.unitLabel.isBlank()) "" else " / ${item.unitLabel}"
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
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
                    row.addView(label)
                    row.addView(edit)
                    row.addView(delete)
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
            row.findViewById<TextView>(R.id.itemPriceNoteText).text =
                if (item.unitPrice.isEstimated) "قیمت تقریبی (بدون سابقهٔ خرید)" else "بر اساس آخرین خرید شما"
            row.findViewById<TextView>(R.id.itemCostText).text = formatCurrency(item.totalCost)
            binding.shoppingListContainer.addView(row)
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
