package com.maliar.pro.database

import android.content.Context
import com.maliar.pro.utils.FoodCatalog
import com.maliar.pro.utils.MealType
import com.maliar.pro.utils.Recipe
import com.maliar.pro.utils.RecipeCatalog
import kotlinx.coroutines.flow.Flow

/** [amount] is per one FoodCatalog "standard purchase unit" (see FoodItemDef.unitLabel).
 *  [isEstimated] = true means this is the static fallback price, not something the person
 *  actually paid - the UI must always label it "تقریبی" per the spec, never show it as a
 *  confirmed price. */
enum class FoodPriceSource { MANUAL, EXPENSE_HISTORY, CATALOG_ESTIMATE }

data class FoodPrice(
    val amount: Double,
    val isEstimated: Boolean,
    val unitLabel: String,
    val source: FoodPriceSource
)

data class ShoppingListItem(
    val ingredientName: String,
    val approxUnits: Double,
    val unitLabel: String,
    val unitPrice: FoodPrice,
    val totalCost: Double
)

data class ShoppingList(val items: List<ShoppingListItem>, val totalCost: Double)

class MealPlanManager(context: Context) {

    private val dao = AppDatabase.getDatabase(context).mealPlanDao()
    private val accountingManager = AccountingManager(context)
    private val foodPriceManager = FoodPriceManager(context)

    fun getAllPlans(): Flow<List<MealPlan>> = dao.getAllPlans()
    fun getLatestPlan(): Flow<MealPlan?> = dao.getLatestPlan()
    fun getEntries(planId: Long): Flow<List<MealPlanEntry>> = dao.getEntries(planId)

    /** All of the person's own Expense rows recognized as food purchases (item #1 of the
     *  spec) - matched by scanning [Expense.description] against FoodCatalog, since
     *  category text is free-form and can't be relied on. Newest first. */
    private suspend fun getFoodExpenses(): List<Pair<Expense, String>> {
        return accountingManager.getAllExpensesList()
            .mapNotNull { expense -> FoodCatalog.findMatch(expense.description)?.let { expense to it.name } }
            .sortedByDescending { it.first.date }
    }

    /** Priority order per the spec: (1) the person's own last recorded price for this
     *  ingredient, otherwise (4) the static catalog fallback, clearly marked estimated.
     *  Tiers 2 (average) and 3 (online price) are intentionally not implemented yet - this
     *  is the offline "پایه" stage; AI/online pricing is a later stage. */
    suspend fun getPriceFor(ingredientName: String): FoodPrice {
        val catalogItem = FoodCatalog.ITEMS.find { it.name == ingredientName }
        val unitLabel = catalogItem?.unitLabel ?: ""
        foodPriceManager.find(ingredientName)?.let { manual ->
            return FoodPrice(
                amount = manual.pricePerUnit,
                isEstimated = false,
                unitLabel = manual.unitLabel.ifBlank { unitLabel },
                source = FoodPriceSource.MANUAL
            )
        }
        val lastUserPrice = getFoodExpenses()
            .filter { it.second == ingredientName }
            .maxByOrNull { it.first.date }
            ?.first?.amount
        return if (lastUserPrice != null) {
            FoodPrice(lastUserPrice, isEstimated = false, unitLabel = unitLabel, source = FoodPriceSource.EXPENSE_HISTORY)
        } else {
            FoodPrice(
                catalogItem?.fallbackPricePerUnit ?: 0.0,
                isEstimated = true,
                unitLabel = unitLabel,
                source = FoodPriceSource.CATALOG_ESTIMATE
            )
        }
    }

    private fun recipeCost(recipe: Recipe, prices: Map<String, FoodPrice>): Double =
        recipe.ingredients.sumOf { (prices[it.name]?.amount ?: 0.0) * it.unitFraction }

    /**
     * Builds and persists a plan for the week starting [weekStartDate] (any existing plan
     * for that exact week is replaced). Deterministic, no AI/network involved (item #7 -
     * "Offline بودن"):
     *  1. Prefer recipes that reuse ingredients the person bought in the last 30 days
     *     (item #4), then prefer cheaper ones.
     *  2. Avoid repeating the same recipe on back-to-back days.
     *  3. If a [budget] > 0 is given and the plan comes out over budget, repeatedly swap
     *     the priciest entry for the cheapest same-meal-type alternative until it fits or
     *     no more swaps help (item #3).
     * Returns the new plan's id.
     */
    suspend fun generateWeeklyPlan(weekStartDate: Long, budget: Double, includeSnack: Boolean = true): Long {
        dao.getPlanForWeek(weekStartDate)?.let { existing ->
            dao.deleteEntriesForPlan(existing.id)
            dao.deletePlan(existing)
        }

        val recentIngredients = getFoodExpenses()
            .filter { it.first.date >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 }
            .map { it.second }
            .toSet()

        val allIngredientNames = RecipeCatalog.RECIPES.flatMap { it.ingredients.map { i -> i.name } }.toSet()
        val prices = allIngredientNames.associateWith { getPriceFor(it) }

        val mealTypes = if (includeSnack) MealType.values().toList() else MealType.values().filter { it != MealType.SNACK }
        val lastUsedByMealType = mutableMapOf<MealType, MutableList<String>>()

        data class Slot(val day: Int, val recipe: Recipe, val cost: Double)
        val slots = mutableListOf<Slot>()

        for (day in 0..6) {
            for (mealType in mealTypes) {
                val candidates = RecipeCatalog.forMealType(mealType)
                if (candidates.isEmpty()) continue
                val recentlyUsed = lastUsedByMealType.getOrPut(mealType) { mutableListOf() }
                val ranked = candidates.sortedWith(
                    compareByDescending<Recipe> { recipe -> recipe.ingredients.count { it.name in recentIngredients } }
                        .thenBy { recipeCost(it, prices) }
                )
                val pick = ranked.firstOrNull { it.name !in recentlyUsed } ?: ranked.first()
                recentlyUsed.add(pick.name)
                if (recentlyUsed.size > 2) recentlyUsed.removeAt(0)
                slots += Slot(day, pick, recipeCost(pick, prices))
            }
        }

        // Budget-fitting pass (item #3): swap the priciest slot for the cheapest same-type
        // alternative, repeatedly, capped so it can never loop forever.
        if (budget > 0) {
            var guard = slots.size * 2
            while (slots.sumOf { it.cost } > budget && guard-- > 0) {
                val worstIndex = slots.indices.maxByOrNull { slots[it].cost } ?: break
                val worst = slots[worstIndex]
                val cheapest = RecipeCatalog.forMealType(worst.recipe.mealType)
                    .minByOrNull { recipeCost(it, prices) } ?: break
                val cheapestCost = recipeCost(cheapest, prices)
                if (cheapestCost >= worst.cost) break
                slots[worstIndex] = Slot(worst.day, cheapest, cheapestCost)
            }
        }

        val planId = dao.insertPlan(MealPlan(weekStartDate = weekStartDate, budget = budget))
        dao.insertEntries(
            slots.map { slot ->
                MealPlanEntry(
                    mealPlanId = planId,
                    dayOfWeek = slot.day,
                    mealType = slot.recipe.mealType.name,
                    recipeName = slot.recipe.name,
                    estimatedCost = slot.cost
                )
            }
        )
        return planId
    }

    /**
     * The "لیست خرید این هفته" for a plan (item #5): every ingredient the plan's recipes
     * need, added up across the week, *except* ingredients the person already bought
     * recently (last 5 days - assumed to still be in the kitchen), since the spec only
     * wants what's actually missing.
     */
    suspend fun getShoppingList(planId: Long): ShoppingList {
        val entries = dao.getEntriesList(planId)
        val recipesByName = RecipeCatalog.RECIPES.associateBy { it.name }

        val fractionByIngredient = mutableMapOf<String, Double>()
        entries.forEach { entry ->
            recipesByName[entry.recipeName]?.ingredients?.forEach { ing ->
                fractionByIngredient[ing.name] = (fractionByIngredient[ing.name] ?: 0.0) + ing.unitFraction
            }
        }

        val recentlyPurchased = getFoodExpenses()
            .filter { it.first.date >= System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000 }
            .map { it.second }
            .toSet()

        val items = fractionByIngredient
            .filterKeys { it !in recentlyPurchased }
            .filterValues { it > 0 }
            .map { (name, fraction) ->
                val price = getPriceFor(name)
                val roundedUnits = Math.ceil(fraction * 10) / 10.0
                ShoppingListItem(
                    ingredientName = name,
                    approxUnits = roundedUnits,
                    unitLabel = price.unitLabel,
                    unitPrice = price,
                    totalCost = fraction * price.amount
                )
            }
            .sortedByDescending { it.totalCost }

        return ShoppingList(items, items.sumOf { it.totalCost })
    }
}
