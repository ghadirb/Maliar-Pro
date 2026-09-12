package com.maliar.pro.database

import android.content.Context
import com.maliar.pro.utils.FoodCatalog
import com.maliar.pro.utils.MealType
import com.maliar.pro.utils.Recipe
import com.maliar.pro.utils.RecipeCatalog
import com.maliar.pro.utils.MarketBackendClient
import kotlinx.coroutines.flow.Flow

/** [amount] is per one FoodCatalog "standard purchase unit" (see FoodItemDef.unitLabel).
 *  [isEstimated] = true means this is the static fallback price, not something the person
 *  actually paid - the UI must always label it "تقریبی" per the spec, never show it as a
 *  confirmed price. */
enum class FoodPriceSource { MANUAL, EXPENSE_HISTORY, MARKET_CHECK, CATALOG_ESTIMATE }

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

    private val appContext = context.applicationContext
    private var automaticOnlineLookups = 0
    private val automaticOnlineLookupLimit = 8

    private val dao = AppDatabase.getDatabase(context).mealPlanDao()
    private val accountingManager = AccountingManager(context)
    private val foodPriceManager = FoodPriceManager(context)
    private val productPricing = MarketAssistantManager(context)

    fun getAllPlans(): Flow<List<MealPlan>> = dao.getAllPlans()
    fun getLatestPlan(): Flow<MealPlan?> = dao.getLatestPlan()
    fun getEntries(planId: Long): Flow<List<MealPlanEntry>> = dao.getEntries(planId)

    suspend fun updateEntry(entry: MealPlanEntry, recipeName: String, estimatedCost: Double) {
        dao.updateEntry(entry.copy(recipeName = recipeName.trim(), estimatedCost = estimatedCost.coerceAtLeast(0.0)))
    }

    suspend fun saveCustomPlan(weekStartDate: Long, budget: Double, entries: List<MealPlanEntry>): Long {
        dao.getPlanForWeek(weekStartDate)?.let { old -> dao.deleteEntriesForPlan(old.id); dao.deletePlan(old) }
        val id = dao.insertPlan(MealPlan(weekStartDate = weekStartDate, budget = budget))
        dao.insertEntries(entries.map { it.copy(id = 0, mealPlanId = id) })
        return id
    }

    /** All of the person's own Expense rows recognized as food purchases (item #1 of the
     *  spec) - matched via [FoodCatalog.isLikelyFoodExpense], which requires the
     *  expense's own category to be blank/food-adjacent before scanning its description
     *  (so e.g. a "خودرو"-categorized "تعویض روغن موتور" entry is never mistaken for a
     *  cooking-oil purchase just because "روغن" appears in its text). Newest first. */
    private suspend fun getFoodExpenses(): List<Pair<Expense, String>> {
        return accountingManager.getAllExpensesList()
            .filter { FoodCatalog.isLikelyFoodExpense(it.category, it.description) }
            .mapNotNull { expense -> FoodCatalog.findMatch(expense.description)?.let { expense to it.name } }
            .sortedByDescending { it.first.date }
    }

    /** Priority order: manual food price, purchase history, cached market quote, a
     * bounded automatic online lookup (when enabled by the caller), then the static
     * catalog estimate. Market quotes are cached by MarketAssistantManager.
     * All online values remain marked as estimates because they are market observations,
     * not confirmed invoices. */
    suspend fun getPriceFor(ingredientName: String, allowAutomaticOnlineLookup: Boolean = false): FoodPrice {
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
        if (lastUserPrice != null) {
            return FoodPrice(lastUserPrice, isEstimated = false, unitLabel = unitLabel, source = FoodPriceSource.EXPENSE_HISTORY)
        }
        productPricing.localPrice(ingredientName)?.let { quote ->
            return FoodPrice(quote.price, isEstimated = true, unitLabel = unitLabel, source = FoodPriceSource.MARKET_CHECK)
        }
        if (allowAutomaticOnlineLookup && automaticOnlineLookups < automaticOnlineLookupLimit) {
            automaticOnlineLookups++
            fetchOnlinePrice(ingredientName, unitLabel)?.let { quote ->
                return FoodPrice(quote.price, isEstimated = true, unitLabel = unitLabel, source = FoodPriceSource.MARKET_CHECK)
            }
        }
        return FoodPrice(catalogItem?.fallbackPricePerUnit ?: 0.0, isEstimated = true, unitLabel = unitLabel, source = FoodPriceSource.CATALOG_ESTIMATE)
    }

    private suspend fun fetchOnlinePrice(ingredientName: String, unitLabel: String): MarketPriceQuote? {
        val product = productPricing.ensureProduct(ingredientName, category = "خوراکی") ?: return null
        val response = MarketBackendClient.search(appContext, ingredientName, "retail")
        response.quotes.filter { it.price > 0 }.forEach { quote ->
            productPricing.addQuote(product.id, quote.source, quote.priceType.uppercase(), quote.price, quote.min, quote.max, quote.confidence, quote.sourceUrl)
        }
        return productPricing.localPrice(ingredientName)
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
        automaticOnlineLookups = 0
        dao.getPlanForWeek(weekStartDate)?.let { existing ->
            dao.deleteEntriesForPlan(existing.id)
            dao.deletePlan(existing)
        }

        val recentIngredients = getFoodExpenses()
            .filter { it.first.date >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 }
            .map { it.second }
            .toSet()

        val allIngredientNames = RecipeCatalog.RECIPES.flatMap { it.ingredients.map { i -> i.name } }.toSet()
        val prices = allIngredientNames.associateWith { getPriceFor(it, allowAutomaticOnlineLookup = true) }

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
        automaticOnlineLookups = 0
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
                val price = getPriceFor(name, allowAutomaticOnlineLookup = true)
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
