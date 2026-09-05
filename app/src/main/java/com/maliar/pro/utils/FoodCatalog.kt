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
        "سیب‌زمینی" to listOf("سیب زمینی", "سیبزمینی", "سیب زمینی تازه", "سیب زمینی سرخ کرده", "چیپس سیب زمینی"),
        "گوجه‌فرنگی" to listOf(
            "گوجه", "گوجه فرنگی", "گوجهفرنگی",
            "گرجه", "گرجه فرنگی", "گرجه‌فرنگی", "گرجهفرنگی",
            "گوجه گیلاسی", "گوجه ریز", "رب گوجه فرنگی"
        ),
        "تخم‌مرغ" to listOf("تخم مرغ", "تخممرغ"),
        "رب گوجه" to listOf("رب گوجه‌فرنگی", "رب گوجه فرنگی", "ربگوجه", "رب"),
        "لوبیا" to listOf("لوبیا چیتی", "لوبیا قرمز", "لوبیا سفید", "لوبیا سبز"),
        "سبزی" to listOf("سبزی خوردن", "سبزی آش", "سبزی پلو", "سبزیجات"),
        "میوه" to listOf("میوه جات", "میوهجات", "ميو"),
        "برنج" to listOf("برنج ایرانی", "برنج هندی", "برنج دم سیاه", "برنج طارم", "برنج هاشمی", "برنج کیلویی"),
        "مرغ" to listOf("مرغ کامل", "مرغ قطعه", "مرغ زنده", "ران مرغ", "سینه مرغ", "جوجه", "جوجه کباب"),
        "گوشت" to listOf("گوشت قرمز", "گوشت گوسفند", "گوشت گوساله", "گوشت چرخکرده", "گوشت چرخ کرده", "گوشت کیلویی"),
        "شیر" to listOf("شیر پرچرب", "شیر کم چرب", "شیر استریلیزه", "شیر پاستوریزه", "شیر کاکائو", "شیر موز"),
        "ماست" to listOf("ماست چکیده", "ماست موسیر", "ماست ساده", "ماست میوه", "ماست یونانی", "ماست کیسه ای"),
        "پنیر" to listOf("پنیر سفید", "پنیر پر چرب", "پنیر کم چرب", "پنیر پیتزا", "پنیر موزارلا", "پنیر لیقوان", "پنیر کوزه"),
        "روغن" to listOf("روغن مایع", "روغن جامد", "روغن زیتون", "روغن کنجد", "روغن کانولا", "روغن نارگیل", "روغن حیوانی"),
        "نان" to listOf("نان بربری", "نان سنگک", "نان تافتون", "نان لواش", "نان باگت", "نان تست", "نان جو"),
        "عدس" to listOf("عدس دال", "عدس سبز", "عدس قرمز"),
        "نخود" to listOf("نخودچی", "نخود آبگوشتی", "نخود فرنگی"),
        "دوغ" to listOf("دوغ گازدار", "دوغ بدون گاز", "دوغ سنتی"),
        "پیاز" to listOf("پیاز زرد", "پیاز قرمز", "پیاز سفید", "پیازچه", "پیاز کیلویی"),
        "خیار" to listOf("خیار شور", "خیار سالادی", "خیار بوته", "خیار چنبر"),
        "موز" to listOf("موز کیلویی", "موز اکوادوری"),
        "سیب" to listOf("سیب قرمز", "سیب زرد", "سیب سبز", "سیب کیلویی"),
        "پرتقال" to listOf("پرتقال کیلویی", "پرتقال توسرخ", "پرتقال والنسیا", "نارنج"),
        "هویج" to listOf("هویج کیلویی", "هویج فرنگی", "هویج ایرانی"),
        "کاهو" to listOf("کاهو سالادی", "کاهو پیچ"),
        "کرفس" to listOf("کرفس ساقه"),
        "فلفل" to listOf("فلفل دلمه", "فلفل سبز", "فلفل قرمز", "فلفل تند"),
        "بادمجان" to listOf("بادمجان کیلویی", "بادمجان قلمی"),
        "کدو" to listOf("کدو سبز", "کدو حلوایی", "کدو تنبل"),
        "قارچ" to listOf("قارچ کیلویی", "قارچ فله"),
        "لیمو" to listOf("لیمو ترش", "لیمو شیرین", "لیمو شيرين", "آبلیمو"),
        "خرما" to listOf("خرما کیلویی", "خرما مضافتی", "خرما پیارم"),
        "گردو" to listOf("گردو کیلویی", "گردو مغز"),
        "بادام" to listOf("بادام کیلویی", "بادام درختی", "بادام زمینی")
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

