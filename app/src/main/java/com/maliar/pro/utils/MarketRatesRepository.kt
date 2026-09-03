package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads a public JSON snapshot of gold/currency/coin prices.
 *
 * The Servix API key never ships in the APK. A separate GitHub Actions job
 * (ghadirb/maliar-market-rates) refreshes rates.json; this class only GETs
 * that public file over HTTPS and parses numbers. No user-supplied URL,
 * no executable payload, no background worker.
 */
data class MarketRates(
    val usdRial: Double,
    val gold18Rial: Double,
    val coinEmamiRial: Double?,
    val coinHalfRial: Double?,
    val coinQuarterRial: Double?,
    val updatedAt: String,
    val source: String
) {
    val usdToman: Double get() = usdRial / 10.0
    val gold18Toman: Double get() = gold18Rial / 10.0
    val coinEmamiToman: Double? get() = coinEmamiRial?.div(10.0)
    val coinHalfToman: Double? get() = coinHalfRial?.div(10.0)
    val coinQuarterToman: Double? get() = coinQuarterRial?.div(10.0)
}

class MarketRatesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = PreferencesManager(appContext)

    suspend fun getRates(forceRefresh: Boolean = false): MarketRates? = withContext(Dispatchers.IO) {
        val cached = prefs.getCachedMarketRatesJson()
        val cachedAt = prefs.getCachedMarketRatesAt()
        val cacheFresh = cached != null && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS
        if (!forceRefresh && cacheFresh) {
            parse(cached!!)?.let { return@withContext it }
        }
        val body = fetchPublicJson()
        if (body != null) {
            val parsed = parse(body)
            if (parsed != null) {
                prefs.setCachedMarketRates(body, System.currentTimeMillis())
                return@withContext parsed
            }
        }
        if (cached != null) parse(cached) else null
    }

    fun buildAnalysis(
        rates: MarketRates,
        liquidToman: Double,
        monthlySurplusToman: Double
    ): String {
        val lines = mutableListOf<String>()
        if (liquidToman > 0) {
            lines += "با نقدینگی ثبت‌شده (${CurrencyFormatter.format(liquidToman)}) تقریباً می‌توانید:"
            if (rates.gold18Toman > 0) {
                val grams = liquidToman / rates.gold18Toman
                lines += "• ${formatQty(grams)} گرم طلای ۱۸ عیار بخرید"
            }
            rates.coinEmamiToman?.takeIf { it > 0 }?.let {
                lines += "• ${formatQty(liquidToman / it)} سکه امامی بخرید"
            }
            if (rates.usdToman > 0) {
                lines += "• معادل حدود ${formatQty(liquidToman / rates.usdToman)} دلار نگه دارید"
            }
        } else {
            lines += "در وضعیت مالی هنوز دارایی نقد/بانک ثبت نشده؛ تحلیل خرید روی نقدینگی واقعی ممکن نیست. نقد و حساب بانکی را در تب وضعیت مالی وارد کنید."
        }
        if (monthlySurplusToman > 0 && rates.gold18Toman > 0) {
            lines += "مازاد این دوره ${CurrencyFormatter.format(monthlySurplusToman)} است؛ یعنی حدود ${formatQty(monthlySurplusToman / rates.gold18Toman)} گرم طلا در هر دوره."
        } else if (monthlySurplusToman < 0) {
            lines += "این دوره ${CurrencyFormatter.format(-monthlySurplusToman)} کسری دارید؛ فعلاً خرید طلا/ارز از محل پس‌انداز ماهانه پیشنهاد نمی‌شود."
        }
        lines += "این عددها تقریبی و بر اساس نرخ لحظه‌ای فایل عمومی است، نه توصیه سرمایه‌گذاری."
        return lines.joinToString("\n")
    }

    fun toPromptBlock(rates: MarketRates?): String {
        if (rates == null) {
            return "- نرخ بازار: در دسترس نیست (فایل عمومی نرخ‌ها خوانده نشد)."
        }
        val coin = rates.coinEmamiToman?.let { CurrencyFormatter.format(it) } ?: "نامشخص"
        return """
            - نرخ دلار: ${CurrencyFormatter.format(rates.usdToman)}
            - نرخ طلای ۱۸ عیار (هر گرم): ${CurrencyFormatter.format(rates.gold18Toman)}
            - نرخ سکه امامی: $coin
            - زمان به‌روزرسانی نرخ: ${rates.updatedAt}
            اگر کاربر درباره طلا، سکه، دلار یا قدرت خرید پرسید، از همین نرخ‌ها و بودجه/دارایی او تحلیل بده. توصیه قطعی خرید/فروش نده.
        """.trimIndent()
    }

    private fun fetchPublicJson(): String? {
        for (url in FEED_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP ${response.code} from $url")
                        return@use
                    }
                    val body = response.body?.string()?.trim().orEmpty()
                    if (body.startsWith("{")) return body
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed $url", e)
            }
        }
        return null
    }

    private fun parse(raw: String): MarketRates? = try {
        val json = JSONObject(raw)
        val usd = json.optDouble("currency", Double.NaN)
        val gold = json.optDouble("gold", Double.NaN)
        if (usd.isNaN() || gold.isNaN() || usd <= 0 || gold <= 0) null
        else MarketRates(
            usdRial = usd,
            gold18Rial = gold,
            coinEmamiRial = json.optionalPositive("coinEmami"),
            coinHalfRial = json.optionalPositive("coinHalf"),
            coinQuarterRial = json.optionalPositive("coinQuarter"),
            updatedAt = json.optString("updatedAt").ifBlank { "نامشخص" },
            source = json.optString("source").ifBlank { "public-json" }
        )
    } catch (e: Exception) {
        Log.e(TAG, "Invalid rates JSON", e)
        null
    }

    private fun JSONObject.optionalPositive(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() && it > 0 }
    }

    private fun formatQty(value: Double): String =
        if (value >= 10) String.format(java.util.Locale.US, "%,.0f", value)
        else String.format(java.util.Locale.US, "%,.2f", value)

    companion object {
        private const val TAG = "MarketRates"
        private const val CACHE_TTL_MS = 2L * 60 * 60 * 1000
        private val FEED_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/ghadirb/maliar-market-rates@main/rates.json",
            "https://raw.githubusercontent.com/ghadirb/maliar-market-rates/main/rates.json"
        )
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
