package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.BudgetManager
import com.maliar.pro.database.Expense
import com.maliar.pro.database.PeriodicPayment
import com.maliar.pro.database.PeriodicPaymentManager
import com.maliar.pro.utils.PersianCalendarHelper.PERSIAN_MONTH_NAMES
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Runs once a day (while "پیشنهادهای هوشمند مالی" is on) and posts at most one notification
 * with the single most useful insight it can find for that day, in priority order: a
 * category spending swing vs. the previous Jalali month (e.g. "هزینه حمل‌ونقل شما در مرداد
 * نسبت به تیر ۲۳٪ افزایش داشته است"); a meaningful day-over-day swing in the gold/currency
 * rate from [MarketRateClient] (e.g. "نرخ طلا نسبت به آخرین بررسی حدود ۴٪ افزایش داشته
 * است") - reported only as a percentage, since that needs no assumption about the rate's
 * exact unit or quotation basis, unlike a derived "you can afford N grams" figure would;
 * or, if nothing swung enough to be worth mentioning, a projected end-of-month
 * surplus/deficit based on the average daily net so far this month. The deterministic
 * numbers are always computed locally first (so the feature works even with no AI
 * credentials/connectivity); [AIHelper.generateText] is used only to phrase the final
 * sentence more naturally when it's available, with the local sentence as a guaranteed
 * fallback either way.
 */
class FinancialInsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isFinancialInsightsEnabled()) return Result.success()

        return try {
            val accountingManager = AccountingManager(applicationContext)
            val financialManager = com.maliar.pro.database.FinancialStatusManager(applicationContext)
            val expenses = accountingManager.getAllExpensesList()
            val incomes = accountingManager.getAllIncomesList()

            // One shared rate fetch feeds three things below: the swing insight (compared
            // against what was cached *before* this call), today's history snapshot for the
            // reports trend chart, and re-pricing any weight-based gold assets - instead of
            // each doing its own redundant network round-trip.
            val previousRates = prefs.getCachedMarketRates()
                ?.let { runCatching { com.google.gson.Gson().fromJson(it, MarketRates::class.java) }.getOrNull() }
            val currentRates = runCatching { MarketRateClient(applicationContext).fetch() }.getOrNull()
            if (currentRates != null) {
                runCatching { financialManager.recordMarketRateSnapshot(currentRates) }
                runCatching { financialManager.refreshGoldAssetValues() }
            }

            val marketInsight = buildMarketRateInsight(prefs, previousRates, currentRates)
            val periodicPaymentInsight = buildPeriodicPaymentInsight(
                PeriodicPaymentManager(applicationContext).getAllList()
            )
            val budgetInsight = buildBudgetInsight(applicationContext, expenses)
            val message = periodicPaymentInsight ?: budgetInsight ?: buildCategorySwingInsight(expenses) ?: marketInsight ?: buildProjectionInsight(incomes, expenses)
            if (message != null) {
                val finalMessage = tryRephraseWithAi(message) ?: message
                NotificationHelper.notifyFinancialInsight(applicationContext, finalMessage, isMarketInsight = message == marketInsight)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute financial insight", e)
            Result.success() // best-effort feature; never worth retrying/crashing over
        }
    }

    private fun buildPeriodicPaymentInsight(payments: List<PeriodicPayment>): String? {
        val now = System.currentTimeMillis()
        val due = payments
            .filter { it.isActive && it.nextPaymentAt <= now + it.reminderDaysBefore.coerceIn(0, 30) * DAY_MILLIS }
            .minByOrNull { it.nextPaymentAt }
            ?: return null
        val remainingDays = ((due.nextPaymentAt - now) / DAY_MILLIS).toInt()
        val amount = String.format("%,.0f", due.amount)
        return when {
            remainingDays < 0 -> "پرداخت دوره‌ای «${due.title}» به مبلغ $amount تومان سررسید شده است."
            remainingDays == 0 -> "امروز موعد پرداخت دوره‌ای «${due.title}» به مبلغ $amount تومان است."
            else -> "$remainingDays روز دیگر پرداخت دوره‌ای «${due.title}» به مبلغ $amount تومان سررسید می‌شود."
        }
    }

    /** Local budget alert. It is evaluated before online/AI insights and never sends
     * a notification when no explicit budget exists. */
    private suspend fun buildBudgetInsight(context: Context, expenses: List<Expense>): String? {
        val (year, month, _) = PersianCalendarHelper.getCurrentJalaliDate()
        val budgets = BudgetManager(context).getForMonthList(year, month).filter { it.isEnabled && it.amount > 0.0 }
        if (budgets.isEmpty()) return null
        val monthStart = PersianCalendarHelper.jalaliToGregorianMillis(year, month, 1)
        val spentByCategory = expenses
            .filter { it.date >= monthStart }
            .groupBy { it.category.trim().ifBlank { "عمومی" }.lowercase() }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        val candidate = budgets.mapNotNull { budget ->
            val spent = spentByCategory[budget.category.trim().ifBlank { "عمومی" }.lowercase()] ?: 0.0
            val ratio = spent / budget.amount
            if (ratio >= budget.hardThreshold / 100.0) Triple(budget, spent, ratio) else null
        }.maxByOrNull { it.third }
        if (candidate != null) {
            val (budget, spent, ratio) = candidate
            val percent = (ratio * 100).toInt()
            return if (spent > budget.amount) {
                "هشدار بودجه: هزینهٔ «${budget.category}» به ${percent}٪ بودجهٔ این ماه رسیده و از سقف عبور کرده است."
            } else {
                "هشدار بودجه: حدود ${percent}٪ بودجهٔ «${budget.category}» مصرف شده است."
            }
        }
        val nearing = budgets.mapNotNull { budget ->
            val spent = spentByCategory[budget.category.trim().ifBlank { "عمومی" }.lowercase()] ?: 0.0
            val ratio = spent / budget.amount
            if (ratio >= budget.softThreshold / 100.0) Triple(budget, spent, ratio) else null
        }.maxByOrNull { it.third }
        return nearing?.let { (budget, _, ratio) ->
            "بودجهٔ «${budget.category}» تقریباً به سقف نزدیک شده است (${(ratio * 100).toInt()}٪ مصرف)."
        }
    }

    /** Compares this Jalali month's per-category spend against last month's and reports the
     *  category with the largest percentage swing, if it's at least [MIN_SWING_PERCENT] and
     *  both months have enough data (>0) in that category to make a percentage meaningful. */
    private fun buildCategorySwingInsight(expenses: List<Expense>): String? {
        val (thisYear, thisMonth, _) = PersianCalendarHelper.getCurrentJalaliDate()
        val (lastMonthYear, lastMonth) = if (thisMonth == 1) (thisYear - 1) to 12 else thisYear to (thisMonth - 1)

        val thisMonthStart = PersianCalendarHelper.jalaliToGregorianMillis(thisYear, thisMonth, 1)
        val lastMonthStart = PersianCalendarHelper.jalaliToGregorianMillis(lastMonthYear, lastMonth, 1)

        val thisMonthByCategory = expenses.filter { it.date >= thisMonthStart && it.category.isNotBlank() }
            .groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        val lastMonthByCategory = expenses.filter { it.date in lastMonthStart until thisMonthStart && it.category.isNotBlank() }
            .groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }

        var bestCategory: String? = null
        var bestSwing = 0.0
        var bestIncreased = true

        for ((category, thisAmount) in thisMonthByCategory) {
            val lastAmount = lastMonthByCategory[category] ?: continue
            if (lastAmount <= 0) continue
            val swingPercent = ((thisAmount - lastAmount) / lastAmount) * 100
            if (kotlin.math.abs(swingPercent) > kotlin.math.abs(bestSwing)) {
                bestSwing = swingPercent
                bestCategory = category
                bestIncreased = swingPercent > 0
            }
        }

        if (bestCategory == null || kotlin.math.abs(bestSwing) < MIN_SWING_PERCENT) return null

        val thisMonthName = PERSIAN_MONTH_NAMES.getOrElse(thisMonth - 1) { "" }
        val lastMonthName = PERSIAN_MONTH_NAMES.getOrElse(lastMonth - 1) { "" }
        val percentText = kotlin.math.abs(bestSwing).roundToInt()
        val direction = if (bestIncreased) "افزایش" else "کاهش"

        return "هزینه \"$bestCategory\" شما در $thisMonthName نسبت به $lastMonthName حدود $percentText٪ $direction داشته است."
    }

    /** Compares [currentRates] (this run's shared fetch) against [previousRates] (what was
     *  cached *before* that fetch, i.e. genuinely an earlier point in time) and reports
     *  whichever of gold or currency swung more, if at least [PreferencesManager.getMarketSwingThresholdPercent].
     *  Best-effort: a missing previous/current value just means no insight this run. */
    private fun buildMarketRateInsight(
        prefs: PreferencesManager,
        previousRates: MarketRates?,
        currentRates: MarketRates?
    ): String? {
        if (previousRates == null || currentRates == null) return null
        val thresholdPercent = prefs.getMarketSwingThresholdPercent().toDouble()
        return marketSwingSentence("طلا", previousRates.gold, currentRates.gold, thresholdPercent)
            ?: marketSwingSentence("دلار", previousRates.currency, currentRates.currency, thresholdPercent)
    }

    private fun marketSwingSentence(label: String, previousValue: Double?, currentValue: Double?, thresholdPercent: Double): String? {
        if (previousValue == null || currentValue == null || previousValue <= 0) return null
        val swingPercent = ((currentValue - previousValue) / previousValue) * 100
        if (kotlin.math.abs(swingPercent) < thresholdPercent) return null
        val direction = if (swingPercent > 0) "افزایش" else "کاهش"
        val percentText = kotlin.math.abs(swingPercent).roundToInt()
        return "نرخ $label نسبت به آخرین بررسی حدود $percentText٪ $direction داشته است."
    }

    /** Projects the month-end balance from the average daily net (income - expense) recorded
     *  so far this Jalali month, extrapolated across the remaining days. */
    private fun buildProjectionInsight(incomes: List<com.maliar.pro.database.Income>, expenses: List<Expense>): String? {
        val (year, month, day) = PersianCalendarHelper.getCurrentJalaliDate()
        if (day < 3) return null // too little data in the first couple of days to project usefully

        val monthStart = PersianCalendarHelper.jalaliToGregorianMillis(year, month, 1)
        val incomeSoFar = incomes.filter { it.date >= monthStart }.sumOf { it.amount }
        val expenseSoFar = expenses.filter { it.date >= monthStart }.sumOf { it.amount }
        val netSoFar = incomeSoFar - expenseSoFar

        val daysInMonth = PersianCalendarHelper.daysInJalaliMonth(year, month)
        val dailyAverage = netSoFar / day
        val projectedNet = dailyAverage * daysInMonth

        if (kotlin.math.abs(projectedNet) < MIN_PROJECTION_AMOUNT) return null

        val roundedMillion = (kotlin.math.abs(projectedNet) / 1_000_000.0)
        val amountText = if (roundedMillion >= 1) {
            String.format("%.1f میلیون تومان", roundedMillion)
        } else {
            String.format("%,.0f تومان", kotlin.math.abs(projectedNet))
        }

        return if (projectedNet >= 0) {
            "با توجه به درآمد و هزینه‌های ثبت‌شده، احتمالاً تا پایان ماه حدود $amountText مازاد خواهید داشت."
        } else {
            "با توجه به روند فعلی هزینه‌ها، احتمالاً تا پایان ماه حدود $amountText کسری خواهید داشت."
        }
    }

    /** Best-effort: asks the configured AI provider to rephrase the deterministic sentence
     *  more naturally. Returns null (falls back to the original sentence) on any failure,
     *  including no API key configured or no network. */
    private suspend fun tryRephraseWithAi(baseMessage: String): String? {
        return try {
            AIHelper.generateText(
                applicationContext,
                systemPrompt = "تو یک دستیار مالی فارسی‌زبان هستی. جمله‌ی زیر را کوتاه، دوستانه و طبیعی بازنویسی کن، بدون افزودن اطلاعات جدید یا تغییر اعداد آن.",
                userPrompt = baseMessage
            )?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "FinancialInsightWorker"
        private const val UNIQUE_WORK_NAME = "maliar_pro_financial_insights"
        private const val MIN_SWING_PERCENT = 15.0
        private const val MIN_PROJECTION_AMOUNT = 50_000.0
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun schedule(context: Context, runImmediately: Boolean = false) {
            val request = PeriodicWorkRequestBuilder<FinancialInsightWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            if (runImmediately) {
                // Periodic work may legally wait up to the first interval before its
                // initial run. Run one best-effort analysis immediately only when the
                // user explicitly enables the feature, never on every app startup.
                val immediate = OneTimeWorkRequestBuilder<FinancialInsightWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "${UNIQUE_WORK_NAME}_immediate",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    immediate
                )
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("${UNIQUE_WORK_NAME}_immediate")
        }
    }
}
