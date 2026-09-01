package com.maliar.pro.ui.mealplan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.MealPlanEntry
import com.maliar.pro.database.MealPlanManager
import com.maliar.pro.database.ShoppingList
import com.maliar.pro.databinding.FragmentMealPlanBinding
import com.maliar.pro.utils.MealType
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

        binding.shoppingListButton.setOnClickListener {
            val planId = viewModel.latestPlan.value?.id ?: return@setOnClickListener
            if (binding.shoppingListCard.visibility == View.VISIBLE) {
                binding.shoppingListCard.visibility = View.GONE
            } else {
                viewModel.loadShoppingList(planId)
            }
        }

        lifecycleScope.launch {
            viewModel.latestPlan.collect { plan ->
                binding.budgetInput.setText(if (plan != null && plan.budget > 0) plan.budget.toLong().toString() else "")
                binding.shoppingListButton.visibility = if (plan != null) View.VISIBLE else View.GONE
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

    private fun formatUnits(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

    private fun formatCurrency(amount: Double): String = String.format("%,.0f ت", amount)
}
