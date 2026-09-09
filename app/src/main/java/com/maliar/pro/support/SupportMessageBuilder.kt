package com.maliar.pro.support

import android.os.Build
import com.maliar.pro.BuildConfig

/**
 * Builds the auto-generated report/suggestion text for the support screen (spec
 * sections 3-4). Only ever includes the person's own free-text plus non-sensitive
 * device/app metadata (app version, Android version, device model) - deliberately never
 * touches accounts, balances, transactions, passwords, tokens, or any other financial or
 * credential data, so there's nothing sensitive to accidentally leak here even though
 * the person is the one who ultimately sends it.
 */
object SupportMessageBuilder {

    fun buildIssueReport(description: String): String = buildString {
        appendLine("گزارش مشکل مالیار پرو")
        appendLine()
        appendLine("شرح مشکل:")
        appendLine(description.trim())
        appendLine()
        appendLine("نسخه برنامه:")
        appendLine(appVersion())
        appendLine()
        appendLine("نسخه اندروید:")
        appendLine(androidVersion())
        appendLine()
        appendLine("مدل دستگاه:")
        appendLine(deviceModel())
        appendLine()
        append("لطفاً مشکل را بررسی کنید.")
    }

    fun buildFeatureSuggestion(description: String): String = buildString {
        appendLine("پیشنهاد قابلیت برای مالیار پرو")
        appendLine()
        appendLine("پیشنهاد:")
        appendLine(description.trim())
        appendLine()
        appendLine("نسخه برنامه:")
        append(appVersion())
    }

    private fun appVersion(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.STORE_CHANNEL}"
    private fun androidVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    private fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
