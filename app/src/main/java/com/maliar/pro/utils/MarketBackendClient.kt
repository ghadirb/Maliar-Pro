package com.maliar.pro.utils

import android.content.Context
import com.maliar.pro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Market data crosses the network only through the configured Apps Script proxy. */
object MarketBackendClient {
    suspend fun search(context: Context, query: String, priceType: String): List<RemoteQuote>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
            if (root.isBlank() || root.contains("CHANGE-ME", true)) return@runCatching null
            val endpoint = if (root.contains("script.google.com", true)) "$root?path=marketSearch" else "$root/market/search"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().put("query", query).put("priceType", priceType).put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching null
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            response.optJSONArray("results")?.let { rows -> List(rows.length()) { i -> rows.getJSONObject(i).let { row -> RemoteQuote(row.optString("source", "بازار"), row.optString("priceType", priceType), row.optDouble("price"), row.optDouble("minPrice").takeIf { n -> !n.isNaN() }, row.optDouble("maxPrice").takeIf { n -> !n.isNaN() }, row.optDouble("confidence", .5)) } } } ?: emptyList()
        }.getOrNull()
    }
    data class RemoteQuote(val source: String, val priceType: String, val price: Double, val min: Double?, val max: Double?, val confidence: Double)
}
