package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.Expense
import com.maliar.pro.utils.PersianCalendarHelper.PERSIAN_MONTH_NAMES
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Runs once a day (while "پیشنهادهای هوشمند مالی" is on) and posts at most one notification
 * with the single most useful insight it can find for that day: either a category spending
 * swing vs. the previous Jalali month (e.g. "هزینه حمل‌ونقل شما در مرداد نسبت به تیر ۲۳٪
 * افزایش داشته است"), or, if nothing swung enough to be worth mentioning, a projected
 * end-of-month surplus/deficit based on the average daily net so far this month. The
 * deterministic numbers are always computed locally first (so the feature works even with
 * no AI credentials/connectivity); [AIHelper.generateText] is used only to phrase the
 * final sentence more naturally when it's available, with the local sentence as a
 * guaranteed fallback either way.
 */
class FinancialInsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isFinancialInsightsEnabled()) return Result.success()

        return try {
            val accountingManager = AccountingManager(applicationContext)
            val expenses = accountingManager.getAllExpensesList()
            val incomes = accountingManager.getAllIncomesList()

            val message = buildCategorySwingInsight(expenses) ?: buildProjectionInsight(incomes, expenses)
            if (message != null) {
                val finalMessage = tryRephraseWithAi(message) ?: message
                NotificationHelper.notifyFinancialInsight(applicationContext, finalMessage)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute financial insight", e)
            Result.success() // best-effort feature; never worth retrying/crashing over
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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FinancialInsightWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
