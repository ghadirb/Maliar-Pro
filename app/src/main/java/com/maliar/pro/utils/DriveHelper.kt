package com.maliar.pro.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object DriveHelper {

    private const val TAG = "DriveHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download error: ${response.code}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }
}
