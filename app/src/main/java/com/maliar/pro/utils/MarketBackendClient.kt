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
    data class RemoteQuote(val source: String, val priceType: String, val price: Double, val min: Double?, val max: Double?, val confidence: Double)

    /** [error] is the raw machine code from the server (e.g. "ai_daily_limit_reached",
     * "ai_provider_not_configured") when [quotes] came back empty because of an explicit
     * failure, as opposed to a search that genuinely found nothing. Use [errorMessage] for
     * a ready-to-show Persian explanation. */
    data class SearchResponse(val quotes: List<RemoteQuote>, val error: String?) {
        val errorMessage: String? get() = error?.let { humanReadableError(it) }
    }

    private fun humanReadableError(code: String): String = when {
        code == "ai_daily_limit_reached" -> "سهمیهٔ امروز جست‌وجوی آنلاین هوش مصنوعی تمام شده. سقف روزانه (AI_DAILY_LIMIT) را می‌توانید در تنظیمات Apps Script بالا ببرید، یا فردا دوباره امتحان کنید."
        code == "ai_provider_not_configured" -> "کلید هوش مصنوعی روی سرور تنظیم نشده (GAPGPT_API_KEY در Script Properties)."
        code == "market_web_search_requires_gapgpt" -> "جست‌وجوی آنلاین فقط با AI_PROVIDER=gapgpt کار می‌کند."
        code == "ai_unavailable" -> "سرویس هوش مصنوعی موقتاً در دسترس نیست."
        code == "query is required" -> "نام کالا خالی بود؛ دوباره امتحان کنید."
        code == "backend_not_configured" -> "آدرس سرور (AI_BACKEND_URL) تنظیم نشده."
        code.startsWith("http_") -> "سرور خطای HTTP ${code.removePrefix("http_")} برگرداند."
        code.startsWith("exception:") -> "ارتباط با سرور برقرار نشد: ${code.removePrefix("exception:")}"
        else -> "خطای سرور: $code"
    }

    /**
     * [sources] are the user's own saved entries from "منابع عمده و خرده" (name + url) for
     * the requested [priceType]. Public marketplaces (Torob/Digikala) are always searched
     * for retail on the server side; any Telegram channel among [sources] is searched too,
     * for either price type, since that's where most Iranian wholesale pricing actually lives.
     */
    suspend fun search(context: Context, query: String, priceType: String, sources: List<Pair<String, String>> = emptyList()): SearchResponse = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching SearchResponse(emptyList(), "query is required")
            val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
            if (root.isBlank() || root.contains("CHANGE-ME", true)) return@runCatching SearchResponse(emptyList(), "backend_not_configured")
            val endpoint = if (root.contains("script.google.com", true)) "$root?path=marketSearch" else "$root/market/search"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }
            val sourcesJson = JSONArray().apply { sources.forEach { (name, url) -> put(JSONObject().put("name", name).put("url", url)) } }
            val body = JSONObject().put("query", query).put("priceType", priceType).put("sources", sourcesJson).put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching SearchResponse(emptyList(), "http_${connection.responseCode}")
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val serverError = response.optString("error").takeIf { it.isNotBlank() }
            if (serverError != null) android.util.Log.w("MarketBackendClient", "marketSearch($query) server error: $serverError")
            val localResults = response.toRemoteQuotes(priceType)
            if (localResults.isNotEmpty()) SearchResponse(localResults, null) else searchWithGrokFallback(root, query, priceType, context)
        }.getOrElse { SearchResponse(emptyList(), "exception:${it.message ?: it::class.simpleName}") }
    }
    private suspend fun searchWithGrokFallback(root: String, query: String, priceType: String, context: Context): SearchResponse = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = if (root.contains("script.google.com", true)) "$root?path=marketAiSearch" else "$root/market/ai-search"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 45_000; setRequestProperty("Content-Type", "application/json") }
            val body = JSONObject().put("query", query).put("priceType", priceType).put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching SearchResponse(emptyList(), "http_${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val serverError = json.optString("error").takeIf { it.isNotBlank() }
            if (serverError != null) android.util.Log.w("MarketBackendClient", "marketAiSearch($query) server error: $serverError")
            SearchResponse(json.toRemoteQuotes(priceType), serverError)
        }.getOrElse { SearchResponse(emptyList(), "exception:${it.message ?: it::class.simpleName}") }
    }
    private fun JSONObject.toRemoteQuotes(defaultType: String): List<RemoteQuote> = optJSONArray("results")?.let { rows -> List(rows.length()) { i -> rows.getJSONObject(i).let { row -> RemoteQuote(row.optString("source", "بازار"), row.optString("priceType", defaultType), row.optDouble("price"), row.optDouble("minPrice").takeIf { n -> !n.isNaN() }, row.optDouble("maxPrice").takeIf { n -> !n.isNaN() }, row.optDouble("confidence", .5)) } } } ?: emptyList()
}
