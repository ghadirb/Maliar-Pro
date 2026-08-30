package com.maliar.pro.utils

/**
 * Rough, general household-budgeting benchmark percentages by expense category, used for
 * the "مقایسه با میانگین" card in financial reports. These are approximate reference
 * points, NOT real aggregated data from other Maliar Pro users - the app has no backend
 * that collects or aggregates anyone's spending, and it never will just for this feature.
 * The UI must always label this clearly as "میانگین تقریبی" so it's never mistaken for a
 * real benchmark against actual people.
 */
object BenchmarkData {

    /** Percent of total monthly expense, for a few of the most common category names used
     *  in this app. Categories the person typed themselves that don't match one of these
     *  (custom category names) simply don't get a benchmark row - better to show nothing
     *  than a made-up number for a category we have no reference for. */
    private val approxPercentByCategory: Map<String, Double> = mapOf(
        "خوراک" to 30.0,
        "مسکن" to 25.0,
        "حمل و نقل" to 12.0,
        "پوشاک" to 6.0,
        "درمان" to 8.0,
        "سلامت" to 8.0,
        "تفریح" to 5.0,
        "قبوض" to 10.0,
        "آموزش" to 6.0,
        "ارتباطات" to 4.0
    )

    fun approxPercentFor(category: String): Double? = approxPercentByCategory[category.trim()]
}
