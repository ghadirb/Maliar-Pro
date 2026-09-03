package com.maliar.pro.utils

/**
 * The "دیکشنری" of food ingredients the meal-planning module recognizes. Used two ways:
 *  1. To scan the person's own Expense.description text and identify which past expenses
 *     were food purchases (item #1 of the spec) - no separate "this was groceries" flag
 *     needed anywhere else in the app.
 *  2. As a last-resort fallback price ([fallbackPricePerUnit]) when the person has never
 *     logged an expense for that ingredient - always shown as "تقریبی" in the UI, never as
 *     a real price (item #1: "قیمت‌های تخمینی باید همیشه با عنوان «تقریبی» نمایش داده شوند").
 *
 * Deliberately a plain in-memory list, not a DB table: it's reference data that ships with
 * the app, not something the person edits, so there's no migration cost to adding items
 * later.
 */
data class FoodItemDef(
    val name: String,
    /** Human label for one "standard purchase unit" of this ingredient - what
     *  [fallbackPricePerUnit] prices, and what a RecipeIngredient's unitFraction is a
     *  fraction of (e.g. 0.2 for برنج means "about a fifth of a typical 1kg bag"). */
    val unitLabel: String,
    /** Rough placeholder price (تومان) for one full standard unit - only ever used when
     *  the person has no purchase history for this ingredient at all. */
    val fallbackPricePerUnit: Double
)

object FoodCatalog {
    val ITEMS: List<FoodItemDef> = listOf(
        FoodItemDef("برنج", "کیلوگرم", 900_000.0),
        FoodItemDef("مرغ", "کیلوگرم", 700_000.0),
        FoodItemDef("گوشت", "کیلوگرم", 3_500_000.0),
        FoodItemDef("تخم‌مرغ", "شانه", 900_000.0),
        FoodItemDef("نان", "بسته", 150_000.0),
        FoodItemDef("شیر", "لیتر", 250_000.0),
        FoodItemDef("ماست", "کیلوگرم", 400_000.0),
        FoodItemDef("پنیر", "کیلوگرم", 1_200_000.0),
        FoodItemDef("عدس", "کیلوگرم", 900_000.0),
        FoodItemDef("لوبیا", "کیلوگرم", 950_000.0),
        FoodItemDef("نخود", "کیلوگرم", 950_000.0),
        FoodItemDef("سبزی", "بسته", 300_000.0),
        FoodItemDef("میوه", "کیلوگرم", 400_000.0),
        FoodItemDef("سیب‌زمینی", "کیلوگرم", 200_000.0),
        FoodItemDef("پیاز", "کیلوگرم", 150_000.0),
        FoodItemDef("گوجه‌فرنگی", "کیلوگرم", 300_000.0),
        FoodItemDef("خیار", "کیلوگرم", 300_000.0),
        FoodItemDef("روغن", "لیتر", 800_000.0),
        FoodItemDef("رب گوجه", "بسته", 400_000.0),
        FoodItemDef("دوغ", "لیتر", 200_000.0)
    )

    private val aliases: Map<String, List<String>> = mapOf(
        "سیب‌زمینی" to listOf("سیب زمینی", "سیبزمینی", "سیب زمینی تازه"),
        "گوجه‌فرنگی" to listOf(
            "گوجه", "گوجه فرنگی", "گوجهفرنگی",
            "گرجه", "گرجه فرنگی", "گرجه‌فرنگی", "گرجهفرنگی"
        ),
        "تخم‌مرغ" to listOf("تخم مرغ", "تخممرغ"),
        "رب گوجه" to listOf("رب گوجه‌فرنگی", "رب گوجه فرنگی", "ربگوجه"),
        "لوبیا" to listOf("لوبیا چیتی", "لوبیا قرمز", "لوبیا سفید"),
        "سبزی" to listOf("سبزی خوردن", "سبزی آش", "سبزی پلو")
    )

    /** A conservative Persian comparison key: spaces, half-spaces and punctuation do not
     * matter; common Arabic letter variants are unified. It intentionally does not apply
     * broad fuzzy matching, so one food is never silently priced as another. */
    fun canonicalKey(value: String): String = value.trim()
        .lowercase(java.util.Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .filter { it.isLetterOrDigit() }
        .toString()

    /** Recognizes a catalog food from an exact item/alias name or a longer expense
     * description that contains one. The longest matching alias wins (e.g. "گوجه فرنگی"
     * before "گوجه"). */
    fun matchName(value: String): FoodItemDef? {
        val key = canonicalKey(value)
        if (key.isBlank()) return null
        val candidates = ITEMS.flatMap { item ->
            (listOf(item.name) + aliases[item.name].orEmpty()).map { alias -> item to canonicalKey(alias) }
        }.filter { it.second.isNotBlank() }
        return candidates.filter { it.second == key }
            .maxByOrNull { it.second.length }
            ?.first
            ?: candidates.filter { key.contains(it.second) }
                .maxByOrNull { it.second.length }
                ?.first
    }

    /** Best-effort match: does this expense description mention a known food ingredient?
     *  Returns the matched [FoodItemDef] or null - a plain substring check, deliberately
     *  simple since the person's own free-text descriptions are short and Persian word
     *  forms vary too much for anything fancier to be reliably better. */
    fun findMatch(description: String): FoodItemDef? {
        return matchName(description)
    }
}
