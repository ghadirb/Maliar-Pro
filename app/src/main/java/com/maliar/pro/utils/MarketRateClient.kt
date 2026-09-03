package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

data class MarketRates(
    @SerializedName("gold") val gold: Double? = null,
    @SerializedName("currency") val currency: Double? = null,
    @SerializedName("coinEmami") val coinEmami: Double? = null,
    @SerializedName("coinHalf") val coinHalf: Double? = null,
    @SerializedName("coinQuarter") val coinQuarter: Double? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("status") val status: String? = null
)

/**
 * Reads gold/currency market rates. By default this reads the small public `rates.json`
 * file published by a companion GitHub repo (ghadirb/maliar-market-rates); a scheduled
 * GitHub Action there refreshes the file from the Servix API using a GitHub Actions
 * secret that never leaves that repo's server-side workflow. The app therefore only ever
 * does a plain, unauthenticated HTTPS GET of a public JSON file it doesn't control the
 * content of - no paid API key, secret, or per-user credential is stored in or sent from
 * the app, so there is nothing here that Google Play's key-in-client-scanning would flag.
 *
 * Advanced users may still point this at their own HTTPS endpoint (e.g. a private Apps
 * Script proxy) from Settings; when they do, that overrides the public default.
 */
class MarketRateClient(context: Context) {
    private val prefs = PreferencesManager(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetch(): MarketRates? = withContext(Dispatchers.IO) {
        val customEndpoint = prefs.getMarketRatesEndpoint().trim()
        val endpoint = if (customEndpoint.startsWith("https://")) customEndpoint else DEFAULT_ENDPOINT
        val token = prefs.getMarketRatesToken().trim()
        runCatching {
            val parsedUrl = endpoint.toHttpUrlOrNull() ?: return@runCatching null
            val url = parsedUrl.newBuilder().apply {
                if (token.isNotBlank()) addQueryParameter("token", token)
            }.build()
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { body ->
                    val rates = gson.fromJson(body, MarketRates::class.java)
                    if (rates.gold == null && rates.currency == null) null else rates
                }
            }
        }.onFailure { e -> Log.w(TAG, "Could not fetch market rates (falling back to cache)", e) }
            .getOrNull()?.also { prefs.cacheMarketRates(gson.toJson(it)) }
            ?: prefs.getCachedMarketRates()?.let { runCatching { gson.fromJson(it, MarketRates::class.java) }.getOrNull() }
    }

    companion object {
        private const val TAG = "MarketRateClient"

        /** Public, read-only JSON published by the companion repo - no key required. */
        const val DEFAULT_ENDPOINT = "https://raw.githubusercontent.com/ghadirb/maliar-market-rates/main/rates.json"
    }
}
