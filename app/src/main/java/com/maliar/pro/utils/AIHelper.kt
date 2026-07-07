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
     * Transcribes a short audio file (recorded for the voice-command notification feature)
     * using GAPGPT's Whisper endpoint. Returns null if there's no active GAPGPT key or the
     * request fails for any reason - callers must handle that as "couldn't understand".
     */
    suspend fun transcribeAudio(context: android.content.Context, audioFile: java.io.File): String? =
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val gapgptKey = prefs.getAPIKeys().firstOrNull {
                    it.isActive && it.provider == com.maliar.pro.models.AIProvider.GAPGPT
                } ?: return@withContext null
                if (gapgptKey.isAutoProvisioned && !com.maliar.pro.utils.SubscriptionManager.canUseAi(context)) {
                    return@withContext null
                }

                val boundary = "----MaliarProBoundary${System.currentTimeMillis()}"
                val url = URL("https://api.gapgpt.app/v1/audio/transcriptions")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.setRequestProperty("Authorization", "Bearer ${gapgptKey.key}")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                connection.outputStream.use { out ->
                    fun writeField(name: String, value: String) {
                        out.write("--$boundary\r\n".toByteArray())
                        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                        out.write("$value\r\n".toByteArray())
                    }
                    writeField("model", "whisper-1")
                    out.write("--$boundary\r\n".toByteArray())
                    out.write(
                        "Content-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n".toByteArray()
                    )
                    out.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
                    audioFile.inputStream().use { it.copyTo(out) }
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    if (gapgptKey.isAutoProvisioned) com.maliar.pro.utils.SubscriptionManager.recordAiUsage(context)
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(response).optString("text").trim().takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Synthesizes Persian speech via GAPGPT's cloud TTS instead of the device's built-in
     * TextToSpeech engine - this is what actually lets smart reminders speak Persian
     * reliably on any device, regardless of whether that device has a Persian voice pack
     * installed locally (most don't, which was the real reason spoken reminders were
     * silent even though everything else - audio routing, volume, permissions - was
     * correct).
     *
     * Tries "gpt-4o-mini-tts" first (better quality/more natural Persian per GAPGPT's own
     * docs), and only falls back to "tts-1" if that specific model/request fails - so a
     * temporary problem with one model doesn't make reminders silent when the other would
     * have worked. Returns null (never throws) if no GAPGPT key is active or if both
     * models fail, so callers must always have a non-cloud fallback (e.g. device TTS or a
     * plain alarm tone) ready.
     */
    suspend fun synthesizeSpeech(context: android.content.Context, text: String): java.io.File? =
        withContext(Dispatchers.IO) {
            val prefs = PreferencesManager(context)
            val gapgptKey = prefs.getAPIKeys().firstOrNull {
                it.isActive && it.provider == com.maliar.pro.models.AIProvider.GAPGPT
            } ?: return@withContext null
            if (gapgptKey.isAutoProvisioned && !com.maliar.pro.utils.SubscriptionManager.canUseAi(context)) {
                return@withContext null
            }

            val result = synthesizeWithModel(context, gapgptKey.key, text, "gpt-4o-mini-tts")
                ?: synthesizeWithModel(context, gapgptKey.key, text, "tts-1")
            if (result != null && gapgptKey.isAutoProvisioned) {
                com.maliar.pro.utils.SubscriptionManager.recordAiUsage(context)
            }
            result
        }

    private fun synthesizeWithModel(
        context: android.content.Context,
        apiKey: String,
        text: String,
        model: String
    ): java.io.File? {
        return try {
            val url = URL("https://api.gapgpt.app/v1/audio/speech")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("model", model)
                put("voice", "alloy")
                put("input", text)
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val outFile = java.io.File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.mp3")
                connection.inputStream.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (outFile.length() > 0) outFile else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
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
                val allActive = prefs.getAPIKeys().filter { it.isActive }
                val personalActive = allActive.filterNot { it.isAutoProvisioned }
                val active = personalActive.ifEmpty { allActive }
                if (active.isEmpty()) return@withContext null

                // Prefer GAPGPT, then Liara, then anything else active
                val key = active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.GAPGPT }
                    ?: active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.LIARA }
                    ?: active.first()

                if (key.isAutoProvisioned && !com.maliar.pro.utils.SubscriptionManager.canUseAi(context)) {
                    return@withContext null
                }

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
                    if (key.isAutoProvisioned) com.maliar.pro.utils.SubscriptionManager.recordAiUsage(context)
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
