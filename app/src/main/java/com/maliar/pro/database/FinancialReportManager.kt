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
    val trend: List<ReportPoint>
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

    suspend fun buildReport(period: ReportPeriod, topN: Int = 5, trendBuckets: Int = 7): FinancialReport {
        val allExpenses = accountingManager.getAllExpensesList()
        val allIncomes = accountingManager.getAllIncomesList()

        val (rangeStart, _) = periodRange(period)
        val expensesInPeriod = allExpenses.filter { it.date >= rangeStart }
        val incomesInPeriod = allIncomes.filter { it.date >= rangeStart }

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

        val trend = buildTrend(allIncomes, allExpenses, period, trendBuckets)

        return FinancialReport(period, totalIncome, totalExpense, topExpenses, topIncomes, topCategory, trend)
    }

    /** Returns the epoch-millis start of the given [period]'s current window, e.g. start of
     *  today for DAILY, start of this Jalali week/month/year otherwise. */
    private fun periodRange(period: ReportPeriod): Pair<Long, Long> {
        val (jy, _, _) = PersianCalendarHelper.getCurrentJalaliDate()
        val now = System.currentTimeMillis()
        val start = when (period) {
            ReportPeriod.DAILY -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.WEEKLY -> now - 7L * 24 * 60 * 60 * 1000
            ReportPeriod.MONTHLY -> PersianCalendarHelper.currentFinancialPeriodStartMillis(periodStartDay)
            ReportPeriod.YEARLY -> PersianCalendarHelper.jalaliToGregorianMillis(jy, 1, 1)
        }
        return start to now
    }

    /** Buckets the full history into [buckets] recent periods (e.g. last 7 days, last 7
     *  weeks, last 6 months, last 5 years) so the report screen can plot an income-vs-expense
     *  trend line, not just a single current total. */
    private fun buildTrend(
        incomes: List<Income>,
        expenses: List<Expense>,
        period: ReportPeriod,
        buckets: Int
    ): List<ReportPoint> {
        val bucketMillis = when (period) {
            ReportPeriod.DAILY -> 24L * 60 * 60 * 1000
            ReportPeriod.WEEKLY -> 7L * 24 * 60 * 60 * 1000
            ReportPeriod.MONTHLY -> 30L * 24 * 60 * 60 * 1000
            ReportPeriod.YEARLY -> 365L * 24 * 60 * 60 * 1000
        }
        val now = System.currentTimeMillis()
        val points = mutableListOf<ReportPoint>()
        for (i in (buckets - 1) downTo 0) {
            val bucketEnd = now - i * bucketMillis
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
