package com.maliar.pro.utils

enum class MealType(val label: String) {
    BREAKFAST("صبحانه"),
    LUNCH("ناهار"),
    DINNER("شام"),
    SNACK("میان‌وعده")
}

/** [unitFraction]: roughly what fraction of one FoodCatalog "standard purchase unit" this
 *  recipe uses per household serving (e.g. 0.2 برنج ≈ a fifth of a typical 1kg bag). This
 *  is deliberately approximate - the whole meal-planning module is explicitly "تقریبی" by
 *  spec, and a rough fraction is enough to (a) estimate a recipe's cost and (b) size a
 *  weekly shopping list, without needing per-gram nutrition-app-grade precision. Ingredient
 *  [name] must match a FoodCatalog.ITEMS entry exactly - that's how cost lookup connects. */
data class RecipeIngredientQty(val name: String, val unitFraction: Double)

data class Recipe(val name: String, val mealType: MealType, val ingredients: List<RecipeIngredientQty>)

object RecipeCatalog {
    val RECIPES: List<Recipe> = listOf(
        // صبحانه
        Recipe("نان، پنیر و چای", MealType.BREAKFAST, listOf(
            RecipeIngredientQty("نان", 0.15), RecipeIngredientQty("پنیر", 0.1)
        )),
        Recipe("نیمرو", MealType.BREAKFAST, listOf(
            RecipeIngredientQty("تخم‌مرغ", 0.1), RecipeIngredientQty("نان", 0.1), RecipeIngredientQty("روغن", 0.02)
        )),
        Recipe("شیر و نان", MealType.BREAKFAST, listOf(
            RecipeIngredientQty("شیر", 0.3), RecipeIngredientQty("نان", 0.1)
        )),
        Recipe("ماست و عسل با نان", MealType.BREAKFAST, listOf(
            RecipeIngredientQty("ماست", 0.15), RecipeIngredientQty("نان", 0.1)
        )),

        // ناهار
        Recipe("قورمه سبزی با برنج", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("گوشت", 0.15),
            RecipeIngredientQty("سبزی", 0.3), RecipeIngredientQty("لوبیا", 0.1), RecipeIngredientQty("پیاز", 0.05)
        )),
        Recipe("جوجه کباب با برنج", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("مرغ", 0.3),
            RecipeIngredientQty("پیاز", 0.05), RecipeIngredientQty("گوجه‌فرنگی", 0.1)
        )),
        Recipe("عدس پلو", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("عدس", 0.15), RecipeIngredientQty("پیاز", 0.05)
        )),
        Recipe("لوبیا پلو", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("لوبیا", 0.15),
            RecipeIngredientQty("گوشت", 0.1), RecipeIngredientQty("پیاز", 0.05)
        )),
        Recipe("خورشت با برنج", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("گوشت", 0.1),
            RecipeIngredientQty("پیاز", 0.05), RecipeIngredientQty("گوجه‌فرنگی", 0.15)
        )),
        Recipe("کوکو سیب‌زمینی", MealType.LUNCH, listOf(
            RecipeIngredientQty("سیب‌زمینی", 0.4), RecipeIngredientQty("تخم‌مرغ", 0.15), RecipeIngredientQty("پیاز", 0.05)
        )),
        Recipe("خورش نخود و سیب‌زمینی", MealType.LUNCH, listOf(
            RecipeIngredientQty("برنج", 0.2), RecipeIngredientQty("نخود", 0.15),
            RecipeIngredientQty("سیب‌زمینی", 0.2), RecipeIngredientQty("پیاز", 0.05)
        )),

        // شام
        Recipe("ماست و خیار با نان", MealType.DINNER, listOf(
            RecipeIngredientQty("ماست", 0.2), RecipeIngredientQty("خیار", 0.2), RecipeIngredientQty("نان", 0.1)
        )),
        Recipe("تخم‌مرغ آب‌پز و نان", MealType.DINNER, listOf(
            RecipeIngredientQty("تخم‌مرغ", 0.15), RecipeIngredientQty("نان", 0.1)
        )),
        Recipe("سوپ مرغ و سبزیجات", MealType.DINNER, listOf(
            RecipeIngredientQty("مرغ", 0.1), RecipeIngredientQty("پیاز", 0.05), RecipeIngredientQty("سیب‌زمینی", 0.1)
        )),
        Recipe("نیمرو و دوغ", MealType.DINNER, listOf(
            RecipeIngredientQty("تخم‌مرغ", 0.1), RecipeIngredientQty("نان", 0.1), RecipeIngredientQty("دوغ", 0.2)
        )),

        // میان‌وعده
        Recipe("میوه فصل", MealType.SNACK, listOf(RecipeIngredientQty("میوه", 0.2))),
        Recipe("ماست و خیار", MealType.SNACK, listOf(
            RecipeIngredientQty("ماست", 0.1), RecipeIngredientQty("خیار", 0.1)
        ))
    )

    fun forMealType(type: MealType): List<Recipe> = RECIPES.filter { it.mealType == type }
}
