package com.maliar.pro.database

/**
 * Canonical expense/budget category names, shared by manual expense entry, the
 * receipt-OCR review dialog, voice entry, and the budget screen's "create budget"
 * dialog - so the same string is always used for the same category everywhere.
 *
 * Before this, every one of those screens had its own free-text category field, so
 * matching an expense to a budget (see BudgetFragment.categoryMatches) depended on the
 * person happening to type the exact same text twice (or on ad-hoc fallback matching
 * like FoodCatalog, which only ever covered "خوراک"). A car expense with category
 * "خودرو " (trailing space) or "خودرو‌" (stray ZWNJ) simply wouldn't match a "خودرو"
 * budget at all. Using this shared list as the picker everywhere removes that whole
 * class of mismatch for the common case, while every picker still allows typing a
 * custom value for anything genuinely not covered here.
 */
object ExpenseCategory {
    const val FOOD = "خوراک"
    const val TRANSPORT = "حمل‌ونقل"
    const val CAR = "خودرو"
    const val HOUSING = "مسکن"
    const val BILLS = "قبوض"
    const val ENTERTAINMENT = "تفریح"
    const val HEALTH = "درمان"
    const val SHOPPING = "خرید"
    const val CLOTHING = "پوشاک"
    const val OTHER = "سایر"

    /** In display order; [OTHER] is deliberately last as the catch-all. */
    val ALL: List<String> = listOf(FOOD, TRANSPORT, CAR, HOUSING, BILLS, ENTERTAINMENT, HEALTH, SHOPPING, CLOTHING, OTHER)
}
