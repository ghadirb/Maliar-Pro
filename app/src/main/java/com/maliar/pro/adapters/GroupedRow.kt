package com.maliar.pro.adapters

import com.maliar.pro.utils.PersianCalendarHelper

/**
 * A single row in a date-grouped transaction list: either a Jalali month/year section
 * header (carrying that month's subtotal) or a real item.
 *
 * This exists so every "list of dated money entries" screen in the app (income, expense,
 * and any future one - debts, a transfers log, whatever comes next) can reuse
 * [groupByJalaliMonth] and a small sectioned RecyclerView.Adapter instead of each screen
 * growing its own flat, ever-longer, ungrouped list as years of data pile up. The
 * grouping/sub-totalling logic only has to be written and tested once here; adapters just
 * call [groupByJalaliMonth] inside their own submitList() so nothing about how a Fragment
 * calls that adapter has to change.
 */
sealed class GroupedRow<out T> {
    data class Header(val label: String, val subtotal: Double) : GroupedRow<Nothing>()
    data class Item<T>(val data: T) : GroupedRow<T>()
}

/**
 * Groups [items] (expected newest-first, which is how every accounting DAO query in this
 * app already returns them - see AccountingDao's "ORDER BY date DESC" queries) into Jalali
 * month/year sections, each carrying a subtotal of [amountOf] over that month's items.
 * Items keep their existing relative order within a section.
 */
fun <T> groupByJalaliMonth(
    items: List<T>,
    timestampOf: (T) -> Long,
    amountOf: (T) -> Double
): List<GroupedRow<T>> {
    if (items.isEmpty()) return emptyList()

    val result = mutableListOf<GroupedRow<T>>()
    var currentLabel: String? = null
    var currentBucket = mutableListOf<T>()

    fun flushBucket() {
        val label = currentLabel ?: return
        if (currentBucket.isEmpty()) return
        result.add(GroupedRow.Header(label, currentBucket.sumOf(amountOf)))
        currentBucket.forEach { result.add(GroupedRow.Item(it)) }
        currentBucket = mutableListOf()
    }

    for (item in items) {
        val (year, month, _) = PersianCalendarHelper.gregorianMillisToJalali(timestampOf(item))
        val monthName = PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(month - 1) { "" }
        val label = "$monthName $year"
        if (label != currentLabel) {
            flushBucket()
            currentLabel = label
        }
        currentBucket.add(item)
    }
    flushBucket()

    return result
}
