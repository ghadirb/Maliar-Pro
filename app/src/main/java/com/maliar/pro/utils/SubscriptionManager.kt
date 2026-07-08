package com.maliar.pro.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Decides whether the person is allowed to make an AI-backed request right now, and talks
 * to the (separately deployed) payment backend to activate/refresh a premium subscription.
 *
 * The model: everything that doesn't cost the app owner money (reminders, calendar,
 * accounting, the full-screen alarm, etc.) is always free and unlimited. Only calls that
 * use the *shared, auto-provisioned* AI key (see APIKey.isAutoProvisioned) are metered,
 * because that's the only usage the app owner actually pays for. Anyone who added their
 * own personal AI key, or who is on an active premium subscription, is unlimited.
 *
 * IMPORTANT (TODO for the app owner): set [BACKEND_BASE_URL] to your deployed backend's
 * real URL (see the /server folder for a ready-to-deploy Node.js project) once you've
 * registered a Zarinpal merchant account and deployed it, e.g. to Liara.
 */
object SubscriptionManager {

    // TODO: replace these with your real deployed backend URLs. Both work exactly the
    // same way (a plain GET returning JSON), whether the backend is:
    //  - a Node.js server (e.g. the one in /server, deployed to Liara), where these are
    //    two separate paths on the same host, e.g.:
    //      "https://maliar-billing.liara.run/subscription/status"
    //      "https://maliar-billing.liara.run/payment/request"
    //  - a Google Apps Script Web App (see /server/apps-script), where both point at the
    //    SAME exec URL but with a different "path" query param already baked in, e.g.:
    //      "https://script.google.com/macros/s/XXXXX/exec?path=status"
    //      "https://script.google.com/macros/s/XXXXX/exec?path=request"
    const val STATUS_URL = "https://CHANGE-ME/subscription/status"
    const val REQUEST_URL = "https://CHANGE-ME/payment/request"

    const val FREE_AI_LIFETIME_LIMIT = 15

    enum class Plan(val apiValue: String, val days: Int, val label: String) {
        MONTHLY("monthly", 30, "اشتراک ماهانه"),
        YEARLY("yearly", 365, "اشتراک سالانه")
    }

    fun isPremium(context: Context): Boolean {
        val prefs = PreferencesManager(context)
        return prefs.getPremiumUntil() > System.currentTimeMillis()
    }

    /** True if the person has added at least one active AI key themselves (not the
     *  shared/free one this app auto-provisions) - their own usage, their own cost. */
    fun hasPersonalKey(context: Context): Boolean {
        return PreferencesManager(context).getAPIKeys().any { it.isActive && !it.isAutoProvisioned }
    }

    /** Remaining free shared-key AI calls for this install/device lifetime.
     *
     * This is intentionally no longer a daily counter: the free shared-key allowance is
     * a one-time trial. A local SharedPreferences counter cannot reliably survive app
     * uninstall/reinstall; hard anti-reset enforcement must be done by the billing backend
     * against a signed-in account or server-side device/install identity. */
    fun remainingFreeLifetime(context: Context): Int {
        val used = PreferencesManager(context).getDailyAiCount()
        return (FREE_AI_LIFETIME_LIMIT - used).coerceAtLeast(0)
    }

    /** Call this BEFORE making a shared-key AI request. Personal keys and active premium
     *  subscriptions always return true without touching the daily counter at all. */
    fun canUseAi(context: Context): Boolean {
        if (isPremium(context)) return true
        if (hasPersonalKey(context)) return true
        return remainingFreeLifetime(context) > 0
    }

    /** Call this AFTER a successful shared-key AI request so the daily counter reflects
     *  real usage. Safe to call even when premium/personal - it becomes a no-op then. */
    fun recordAiUsage(context: Context) {
        if (isPremium(context) || hasPersonalKey(context)) return
        val prefs = PreferencesManager(context)
        val current = prefs.getDailyAiCount()
        prefs.setDailyAiCount(current + 1, "lifetime")
    }

    /** A short Persian message explaining why the AI is unavailable right now, meant to be
     *  shown directly in the chat/assistant UI in place of an actual AI reply. */
    fun upgradeMessage(context: Context): String {
        return "⚠️ سهمیه رایگان اولیه ($FREE_AI_LIFETIME_LIMIT پیام) شما تمام شده است.\n" +
            "برای ادامه‌ی استفاده از دستیار هوشمند، می‌توانید:\n" +
            "• از تنظیمات → کلیدهای هوش مصنوعی، کلید شخصی خودتان را اضافه کنید (نامحدود و رایگان از طرف ما)\n" +
            "• یا با ارتقا به اشتراک پریمیوم، محدودیت را کاملاً بردارید"
    }

    /** Appends a query parameter to a URL, using "?" if it has none yet or "&" if it
     *  already does (needed because Apps Script URLs already contain "?path=..."). */
    private fun appendParam(url: String, key: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        val encoded = URLEncoder.encode(value, "UTF-8")
        return "$url$separator$key=$encoded"
    }

    /**
     * Asks the backend for this device's current subscription status and updates the
     * local cache. Safe to call often (e.g. app open, subscription screen open) - if the
     * network call fails for any reason, the previously cached value is left untouched so
     * the person doesn't lose premium access just because of a bad connection.
     */
    suspend fun refreshFromServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            val url = URL(appendParam(STATUS_URL, "deviceId", deviceId))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val premiumUntil = json.optLong("premiumUntil", 0L)
                val prefs = PreferencesManager(context)
                prefs.setPremiumUntil(premiumUntil)
                prefs.setLastSubscriptionCheck(System.currentTimeMillis())
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionManager", "refreshFromServer failed: ${e.message}")
            false
        }
    }

    /**
     * Asks the backend to start a Zarinpal payment for [plan] and returns the payment page
     * URL to open in a browser, or null if the request failed. The backend is responsible
     * for the actual Zarinpal PaymentRequest.json/PaymentVerification.json calls and for
     * activating the subscription once Zarinpal confirms payment - see /server/README-fa.md.
     */
    suspend fun requestPayment(context: Context, plan: Plan): String? = withContext(Dispatchers.IO) {
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            var url = appendParam(REQUEST_URL, "deviceId", deviceId)
            url = appendParam(url, "plan", plan.apiValue)

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("paymentUrl").takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionManager", "requestPayment failed: ${e.message}")
            null
        }
    }

    /** Human-readable Persian label for the premium expiry, or null if not premium. */
    fun premiumExpiryLabel(context: Context): String? {
        val until = PreferencesManager(context).getPremiumUntil()
        if (until <= System.currentTimeMillis()) return null
        val formatted = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(until))
        return "اشتراک پریمیوم شما تا $formatted فعال است"
    }
}
