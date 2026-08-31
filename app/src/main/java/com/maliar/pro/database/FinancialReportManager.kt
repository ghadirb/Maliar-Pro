package com.maliar.pro.database

import com.maliar.pro.utils.PersianCalendarHelper
import java.util.Calendar

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY, YEARLY }

data class ReportPoint(val label: String, val income: Double, val expense: Double) {
    val net: Double get() = income - expense
}

data class CategoryTotal(val category: String, val total: Double)

data class FinancialReport(
    val period: ReportPeriod,
    val totalIncome: Double,
    val totalExpense: Double,
    val topExpenses: List<Expense>,
    val topIncomes: List<Income>,
    val topExpenseCategory: CategoryTotal?,
    val trend: List<ReportPoint>,
    /** Every category's share of this period's total expense, sorted descending - used for
     *  the "مقایسه با میانگین" benchmark card, not just the single top category above. */
    val categoryBreakdown: List<CategoryTotal> = emptyList()
) {
    val net: Double get() = totalIncome - totalExpense
}

/**
 * Turns the raw expense/income tables into the "گزارش‌های مالی حرفه‌ای" the user asked for:
 * daily/weekly/monthly/yearly breakdowns, the biggest individual expenses and incomes, the
 * category that ate the most money, and a short trend series suitable for a line/bar chart.
 * Pure computation over in-memory lists so it works the same regardless of which period is
 * currently selected on screen.
 */
class FinancialReportManager(
    private val accountingManager: AccountingManager,
    private val periodStartDay: Int = 1
) {

    /** @param offset 0 = the current period, 1 = one period back, 2 = two back, etc. -
     *  lets the reports screen's previous/next navigator show and export any past window,
     *  not just the current one. */
    suspend fun buildReport(period: ReportPeriod, offset: Int = 0, topN: Int = 5, trendBuckets: Int = 7): FinancialReport {
        val allExpenses = accountingManager.getAllExpensesList()
        val allIncomes = accountingManager.getAllIncomesList()

        val (rangeStart, rangeEnd) = periodRange(period, offset)
        val expensesInPeriod = allExpenses.filter { it.date >= rangeStart && it.date < rangeEnd }
        val incomesInPeriod = allIncomes.filter { it.date >= rangeStart && it.date < rangeEnd }

        val totalExpense = expensesInPeriod.sumOf { it.amount }
        val totalIncome = incomesInPeriod.sumOf { it.amount }

        val topExpenses = expensesInPeriod.sortedByDescending { it.amount }.take(topN)
        val topIncomes = incomesInPeriod.sortedByDescending { it.amount }.take(topN)

        val topCategory = expensesInPeriod
            .filter { it.category.isNotBlank() }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }
            ?.let { CategoryTotal(it.key, it.value) }

        val trend = buildTrend(allIncomes, allExpenses, period, trendBuckets, rangeEnd)

        val categoryBreakdown = expensesInPeriod
            .filter { it.category.isNotBlank() }
            .groupBy { it.category }
            .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amount }) }
            .sortedByDescending { it.total }

        return FinancialReport(period, totalIncome, totalExpense, topExpenses, topIncomes, topCategory, trend, categoryBreakdown)
    }

    /** Human-readable "from - to" label for the given period/offset window, e.g.
     *  "۱ تا ۳۰ مرداد ۱۴۰۴" for a monthly period or "۱۴۰۳" for a yearly one - shown next
     *  to the previous/next navigator on the reports screen. */
    fun rangeLabel(period: ReportPeriod, offset: Int): String {
        val (start, endExclusive) = periodRange(period, offset)
        val lastDayMillis = endExclusive - 1
        val (sy, sm, sd) = PersianCalendarHelper.gregorianMillisToJalali(start)
        val (ey, em, ed) = PersianCalendarHelper.gregorianMillisToJalali(lastDayMillis.coerceAtLeast(start))
        return when (period) {
            ReportPeriod.YEARLY -> sy.toString()
            ReportPeriod.MONTHLY -> if (sy == ey && sm == em) {
                "$sd تا $ed ${PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(sm - 1) { "" }} $sy"
            } else {
                "${PersianCalendarHelper.formatJalali(sy, sm, sd)} تا ${PersianCalendarHelper.formatJalali(ey, em, ed)}"
            }
            ReportPeriod.DAILY -> PersianCalendarHelper.formatJalali(sy, sm, sd)
            ReportPeriod.WEEKLY -> "${PersianCalendarHelper.formatJalali(sy, sm, sd)} تا ${PersianCalendarHelper.formatJalali(ey, em, ed)}"
        }
    }

    /** Returns the epoch-millis [start, endExclusive) of the given [period]'s window, where
     *  [offset] = 0 is the current/ongoing window (ending "now") and 1, 2, ... step back
     *  through previous, already-closed windows of the same kind. */
    private fun periodRange(period: ReportPeriod, offset: Int = 0): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (period) {
            ReportPeriod.DAILY -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                val start = cal.timeInMillis
                val end = if (offset == 0) now else start + 24L * 60 * 60 * 1000
                start to end
            }
            ReportPeriod.WEEKLY -> {
                val weekMillis = 7L * 24 * 60 * 60 * 1000
                val end = if (offset == 0) now else now - offset * weekMillis
                val start = end - weekMillis
                start to end
            }
            ReportPeriod.MONTHLY -> {
                val start = PersianCalendarHelper.financialPeriodStartMillisForOffset(periodStartDay, offset)
                val end = if (offset == 0) now else PersianCalendarHelper.financialPeriodStartMillisForOffset(periodStartDay, offset - 1)
                start to end
            }
            ReportPeriod.YEARLY -> {
                val (jy, _, _) = PersianCalendarHelper.getCurrentJalaliDate()
                val targetYear = jy - offset
                val start = PersianCalendarHelper.jalaliToGregorianMillis(targetYear, 1, 1)
                val end = if (offset == 0) now else PersianCalendarHelper.jalaliToGregorianMillis(targetYear + 1, 1, 1)
                start to end
            }
        }
    }

    /** Buckets history up to [anchorEnd] into [buckets] consecutive periods (e.g. last 7
     *  days, last 7 weeks, last 6 months, last 5 years ending at the selected window) so the
     *  report screen can plot an income-vs-expense trend line for whichever period/offset is
     *  currently selected, not just always the current one. */
    private fun buildTrend(
        incomes: List<Income>,
        expenses: List<Expense>,
        period: ReportPeriod,
        buckets: Int,
        anchorEnd: Long
    ): List<ReportPoint> {
        val bucketMillis = when (period) {
            ReportPeriod.DAILY -> 24L * 60 * 60 * 1000
            ReportPeriod.WEEKLY -> 7L * 24 * 60 * 60 * 1000
            ReportPeriod.MONTHLY -> 30L * 24 * 60 * 60 * 1000
            ReportPeriod.YEARLY -> 365L * 24 * 60 * 60 * 1000
        }
        val points = mutableListOf<ReportPoint>()
        for (i in (buckets - 1) downTo 0) {
            val bucketEnd = anchorEnd - i * bucketMillis
            val bucketStart = bucketEnd - bucketMillis
            val incomeSum = incomes.filter { it.date in bucketStart until bucketEnd }.sumOf { it.amount }
            val expenseSum = expenses.filter { it.date in bucketStart until bucketEnd }.sumOf { it.amount }
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(bucketEnd)
            val label = when (period) {
                ReportPeriod.DAILY -> "$d/$m"
                ReportPeriod.WEEKLY -> "$d/$m"
                ReportPeriod.MONTHLY -> PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(m - 1) { "" }
                ReportPeriod.YEARLY -> y.toString()
            }
            points += ReportPoint(label, incomeSum, expenseSum)
        }
        return points
    }
}
