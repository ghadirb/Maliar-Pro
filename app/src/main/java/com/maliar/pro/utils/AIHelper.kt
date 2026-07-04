package com.maliar.pro.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small, dependency-free helper for one-off "ask the AI for text" calls, shared between
 * AssistantViewModel-style chat and non-chat features like smart reminders (which need a
 * short natural-language phrase generated from a title/description, not a full assistant
 * conversation).
 */
object AIHelper {

    private fun baseUrlFor(provider: com.maliar.pro.models.AIProvider, stored: String?): String {
        if (!stored.isNullOrBlank()) return stored
        return when (provider) {
            com.maliar.pro.models.AIProvider.GAPGPT -> "https://api.gapgpt.app/v1"
            com.maliar.pro.models.AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
            else -> "https://api.openai.com/v1"
        }
    }

    private fun modelFor(baseUrl: String): String = when {
        baseUrl.contains("gapgpt.app") -> "gpt-4o-mini"
        baseUrl.contains("liara.ir") -> "openai/gpt-4o-mini"
        else -> "gpt-3.5-turbo"
    }

    /**
     * Sends a short prompt to whichever active AI key is available and returns the
     * generated text, or null if there is no active key or the call fails for any reason.
     * Callers should always have a non-AI fallback ready.
     */
    suspend fun generateText(context: android.content.Context, systemPrompt: String, userPrompt: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val active = prefs.getAPIKeys().filter { it.isActive }
                if (active.isEmpty()) return@withContext null

                // Prefer GAPGPT, then Liara, then anything else active
                val key = active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.GAPGPT }
                    ?: active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.LIARA }
                    ?: active.first()

                val baseUrl = baseUrlFor(key.provider, key.baseUrl)
                val model = modelFor(baseUrl)

                val url = URL("$baseUrl/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${key.key}")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    })
                    put("max_tokens", 200)
                    put("temperature", 0.8)
                }

                OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
}
