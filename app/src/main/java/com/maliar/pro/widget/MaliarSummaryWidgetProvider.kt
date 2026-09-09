package com.maliar.pro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.maliar.pro.MainActivity
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A simple home-screen widget: total balance ("تراز کل") + this period's balance
 * ("تراز دوره") + the next upcoming reminder. Read-only and refreshed periodically
 * (every 30 min, the Android-enforced minimum-adjacent interval) plus on-demand via
 * [requestUpdate] whenever accounting/reminder data actually changes, so it doesn't rely
 * only on the slow periodic refresh. Tapping it opens the app - no other interaction, no
 * background service, no new permissions.
 */
class MaliarSummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_maliar_summary)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetBalanceText, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetPeriodBalanceText, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetNextReminderText, pendingIntent)

            // Show something immediately (avoids a blank widget while the async load below
            // finishes), then update again once real data is ready.
            appWidgetManager.updateAppWidget(widgetId, views)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val accountingManager = AccountingManager(context)
                    val balance = accountingManager.getBalance()
                    val periodBalance = accountingManager.getPeriodBalance()
                    val allActiveReminders = SmartReminderManager(context).reconcileRecurringReminders()
                        .filter { !it.isCompleted }
                        .sortedBy { it.triggerTime }
                    val nextReminder = allActiveReminders.firstOrNull()
                    val upcomingCount = allActiveReminders.count {
                        it.triggerTime > System.currentTimeMillis() && it.triggerTime < System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                    }
                    val rates = runCatching { com.maliar.pro.utils.MarketRateClient(context).fetch() }.getOrNull()

                    views.setTextViewText(R.id.widgetBalanceText, com.maliar.pro.utils.CurrencyFormatter.format(balance))
                    views.setTextViewText(R.id.widgetPeriodBalanceText, com.maliar.pro.utils.CurrencyFormatter.format(periodBalance))
                    views.setTextColor(
                        R.id.widgetPeriodBalanceText,
                        if (periodBalance < 0) android.graphics.Color.parseColor("#FFCDD2") else android.graphics.Color.WHITE
                    )
                    if (rates != null && (rates.gold != null || rates.currency != null)) {
                        val toToman = { rial: Double -> rial / com.maliar.pro.utils.MarketRateClient.RIAL_TO_TOMAN }
                        val goldText = rates.gold?.let { com.maliar.pro.utils.CurrencyFormatter.format(toToman(it), "") } ?: "-"
                        val currencyText = rates.currency?.let { com.maliar.pro.utils.CurrencyFormatter.format(toToman(it), "") } ?: "-"
                        views.setTextViewText(R.id.widgetMarketRateText, "طلا: $goldText | دلار: $currencyText")
                        views.setViewVisibility(R.id.widgetMarketRateText, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widgetMarketRateText, android.view.View.GONE)
                    }
                    views.setTextViewText(
                        R.id.widgetNextReminderText,
                        if (nextReminder != null) {
                            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(nextReminder.triggerTime)
                            val suffix = if (upcomingCount > 1) " (+$upcomingCount تا هفته آینده)" else ""
                            "⏰ ${nextReminder.title} - ${PersianCalendarHelper.formatJalali(y, m, d)}$suffix"
                        } else {
                            "یادآوری فعالی وجود ندارد"
                        }
                    )
                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (e: Throwable) {
                    // Widget just keeps showing its last successfully-loaded values; never
                    // let a data-layer failure surface as a broken/blank widget.
                }
            }
        } catch (e: Throwable) {
            // Never let a widget-render failure show the system's "problem loading
            // widget" placeholder - fall back to a minimal static view instead.
            val fallback = RemoteViews(context.packageName, R.layout.widget_maliar_summary)
            try {
                appWidgetManager.updateAppWidget(widgetId, fallback)
            } catch (ignored: Throwable) {
                // Nothing more we can do here.
            }
        }
    }

    companion object {
        /** Call this after any write that could change the balance or reminder list (e.g.
         *  from AccountingManager/SmartReminderManager callers) so the widget doesn't have
         *  to wait for its next periodic refresh to reflect a fresh entry. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, MaliarSummaryWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, MaliarSummaryWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

