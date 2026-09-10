package com.maliar.pro.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which time window the big "تراز"/"موجودی کل" number on the Home and Accounting tabs
 * shows. A single shared preference on purpose - قدیر asked for one choice that governs
 * both screens together, not two independent toggles that could disagree with each other.
 */
enum class BalancePeriod {
    YEAR, MONTH;

    companion object {
        fun fromStored(value: String?): BalancePeriod = if (value == MONTH.name) MONTH else YEAR
    }
}

object BalancePeriodPreference {

    private const val PREFS_NAME = "balance_period_prefs"
    private const val KEY_PERIOD = "selected_period"

    // In-memory cache so every screen reacts to a change immediately without needing to
    // re-read SharedPreferences or restart the fragment - Home and Accounting each hold
    // their own StateFlow-backed toggle, but both seed from and write back to this one.
    private var cached: MutableStateFlow<BalancePeriod>? = null

    private fun flow(context: Context): MutableStateFlow<BalancePeriod> {
        return cached ?: MutableStateFlow(readFromDisk(context)).also { cached = it }
    }

    private fun readFromDisk(context: Context): BalancePeriod {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BalancePeriod.fromStored(prefs.getString(KEY_PERIOD, null))
    }

    fun current(context: Context): StateFlow<BalancePeriod> = flow(context).asStateFlow()

    fun get(context: Context): BalancePeriod = flow(context).value

    fun set(context: Context, period: BalancePeriod) {
        flow(context).value = period
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERIOD, period.name)
            .apply()
        runCatching { com.maliar.pro.widget.MaliarSummaryWidgetProvider.requestUpdate(context) }
    }
}
