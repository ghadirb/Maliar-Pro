package com.maliar.pro.utils

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the current [Announcement] from a hosted JSON URL. Any failure (no internet,
 * unreachable host, malformed JSON) returns null quietly - a startup announcement must
 * never be able to block or break opening the app itself.
 */
object AnnouncementManager {
    private const val TAG = "AnnouncementManager"

    /** The link given in Settings/product notes - update the JSON there any time. */
    const val ANNOUNCEMENT_URL = "https://abrehamrahi.ir/o/public/sWpkqe6t"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Blocking network call - always call this from a background dispatcher. */
    fun fetch(url: String = ANNOUNCEMENT_URL): Announcement? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                Gson().fromJson(body, Announcement::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch remote announcement (this is fine, app continues normally)", e)
            null
        }
    }
}
