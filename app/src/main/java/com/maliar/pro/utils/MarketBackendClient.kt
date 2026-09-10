package com.maliar.pro.utils

import android.content.Context
import com.maliar.pro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Market data crosses the network only through the configured Apps Script proxy. */
object MarketBackendClient {
    /**
     * [sources] are the user's own saved entries from "منابع عمده و خرده" (name + url) for
     * the requested [priceType]. Public marketplaces (Torob/Digikala) are always searched
     * for retail on the server side; any Telegram channel among [sources] is searched too,
     * for either price type, since that's where most Iranian wholesale pricing actually lives.
     */
    suspend fun search(context: Context, query: String, priceType: String, sources: List<Pair<String, String>> = emptyList()): List<RemoteQuote>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
            if (root.isBlank() || root.contains("CHANGE-ME", true)) return@runCatching null
            val endpoint = if (root.contains("script.google.com", true)) "$root?path=marketSearch" else "$root/market/search"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }
            val sourcesJson = JSONArray().apply { sources.forEach { (name, url) -> put(JSONObject().put("name", name).put("url", url)) } }
            val body = JSONObject().put("query", query).put("priceType", priceType).put("sources", sourcesJson).put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching null
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            response.optString("error").takeIf { it.isNotBlank() }?.let {
                android.util.Log.w("MarketBackendClient", "marketSearch($query) server error: $it")
            }
            val localResults = response.toRemoteQuotes(priceType)
            if (localResults.isNotEmpty()) localResults else searchWithGrokFallback(root, query, priceType, context)
        }.getOrNull()
    }
    private suspend fun searchWithGrokFallback(root: String, query: String, priceType: String, context: Context): List<RemoteQuote> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = if (root.contains("script.google.com", true)) "$root?path=marketAiSearch" else "$root/market/ai-search"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 45_000; setRequestProperty("Content-Type", "application/json") }
            val body = JSONObject().put("query", query).put("priceType", priceType).put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            json.optString("error").takeIf { it.isNotBlank() }?.let {
                android.util.Log.w("MarketBackendClient", "marketAiSearch($query) server error: $it")
            }
            json.toRemoteQuotes(priceType)
        }.getOrDefault(emptyList())
    }
    private fun JSONObject.toRemoteQuotes(defaultType: String): List<RemoteQuote> = optJSONArray("results")?.let { rows -> List(rows.length()) { i -> rows.getJSONObject(i).let { row -> RemoteQuote(row.optString("source", "بازار"), row.optString("priceType", defaultType), row.optDouble("price"), row.optDouble("minPrice").takeIf { n -> !n.isNaN() }, row.optDouble("maxPrice").takeIf { n -> !n.isNaN() }, row.optDouble("confidence", .5)) } } } ?: emptyList()
    data class RemoteQuote(val source: String, val priceType: String, val price: Double, val min: Double?, val max: Double?, val confidence: Double)
}
