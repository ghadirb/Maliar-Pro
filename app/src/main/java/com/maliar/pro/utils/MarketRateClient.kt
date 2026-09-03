package com.maliar.pro.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class MarketRates(
    @SerializedName("gold") val gold: Double? = null,
    @SerializedName("currency") val currency: Double? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("status") val status: String? = null
)

/**
 * Reads market rates only through a user/admin supplied HTTPS proxy (for example a
 * deployed Google Apps Script). No Servix key is stored in the app or sent from it.
 */
class MarketRateClient(context: Context) {
    private val prefs = PreferencesManager(context)
    private val http = OkHttpClient()
    private val gson = Gson()

    suspend fun fetch(): MarketRates? = withContext(Dispatchers.IO) {
        val endpoint = prefs.getMarketRatesEndpoint().trim()
        if (!endpoint.startsWith("https://")) return@withContext null
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
        }.getOrNull()?.also { prefs.cacheMarketRates(gson.toJson(it)) }
            ?: prefs.getCachedMarketRates()?.let { runCatching { gson.fromJson(it, MarketRates::class.java) }.getOrNull() }
    }
}
