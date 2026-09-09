package com.maliar.pro.utils

/**
 * A tiny in-memory hand-off for "tap a notification, land on the دستیار هوشمند tab with a
 * question already sent" (see the market-rate-swing notification in
 * [FinancialInsightWorker]). [MainActivity] sets [pendingQuestion] when it sees the
 * notification's intent extra and navigates to the assistant tab; [AssistantFragment]
 * consumes (reads then clears) it once its view is ready and sends it like any other
 * quick-action button. Process-local and intentionally not persisted - if the app process
 * is gone the notification tap just cold-starts the app to the assistant tab with no
 * canned question, which is a fine fallback rather than something worth over-engineering.
 */
object PendingAssistantQuestion {
    var pendingQuestion: String? = null

    fun consume(): String? {
        val question = pendingQuestion
        pendingQuestion = null
        return question
    }
}
