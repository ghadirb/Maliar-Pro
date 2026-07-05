package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A single remote announcement/what's-new message. Hosted as a plain JSON file at whatever
 * URL is passed to [AnnouncementManager.fetch] - no backend or app update needed to change
 * what's shown, just edit the file at that URL.
 *
 * Expected JSON shape:
 * {
 *   "id": "welcome-1",
 *   "title": "به مالیار پرو خوش آمدید",
 *   "message": "متن کامل توضیحات، می‌تواند چند خط باشد...",
 *   "button_text": "متوجه شدم",
 *   "enabled": true
 * }
 *
 * - "id" is what decides whether this has already been seen - change it to any new value
 *   whenever the message content changes, and everyone will see the new one once more,
 *   even people who already dismissed an older id.
 * - "enabled": false hides it for everyone without deleting the file.
 * - "button_text" is optional (defaults to "متوجه شدم" if left out).
 */
data class Announcement(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("button_text") val buttonText: String? = null,
    @SerializedName("enabled") val enabled: Boolean = true
)
