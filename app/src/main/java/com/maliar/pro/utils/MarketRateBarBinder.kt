package com.maliar.pro.utils

import android.content.Context
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Populates the "طلا: ... | دلار: ..." strip (see view_market_rate_bar.xml) included at
 * the top of the accounting and financial-status screens. A best-effort read: uses
 * whatever [MarketRateClient] returns (live or its own cached fallback) and simply keeps
 * the strip hidden if nothing is available - it never shows a placeholder/dash row, since
 * that would just be visual noise on a screen that isn't primarily about market rates.
 */
object MarketRateBarBinder {
    fun bind(cardView: View, textView: TextView, lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope, context: Context) {
        lifecycleScope.launch {
            val rates = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) { MarketRateClient(context).fetch() }
            }.getOrNull()

            if (rates == null || (rates.gold == null && rates.currency == null)) {
                cardView.visibility = View.GONE
                return@launch
            }

            val toToman = { rial: Double -> rial / MarketRateClient.RIAL_TO_TOMAN }
            val parts = mutableListOf<String>()
            rates.gold?.let { parts.add("طلا: ${CurrencyFormatter.format(toToman(it), "")}") }
            rates.currency?.let { parts.add("دلار: ${CurrencyFormatter.format(toToman(it), "")}") }
            textView.text = "🪙 " + parts.joinToString(" | ")
            cardView.visibility = View.VISIBLE
        }
    }
}
