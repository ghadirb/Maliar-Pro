package com.maliar.pro.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small, dependency-free helper for one-off "ask the AI for text/speech/transcription"
 * calls, shared between non-chat features like smart reminders (a short natural-language
 * phrase generated from a title/description) and the voice-command notification feature -
 * as opposed to AssistantViewModel's full chat conversation.
 *
 * Every function here follows the exact same priority AssistantViewModel already uses for
 * the main chat assistant: the person's own personal key first (Settings -> کلیدهای هوش
 * مصنوعی) if they've added and activated one, calling the provider directly; otherwise the
 * shared/free tier via [AIBackendClient], the server-side Apps Script proxy that holds the
 * real provider key in Script Properties and never ships it in the APK (see
 * AutoProvisioningManager for why). An earlier version of this file only ever checked for a
 * personal GAPGPT key and never fell back to the proxy - which silently broke smart
 * reminders and voice commands for every person relying on the shared/free tier (i.e.
 * anyone who hasn't manually added their own key), since that's most people.
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

    /** The person's own active, non-shared key(s), preferring GAPGPT then Liara then
     *  whatever else is active - or null if they haven't added one, meaning callers should
     *  fall back to the shared proxy. */
    private fun personalKey(context: android.content.Context): com.maliar.pro.models.APIKey? {
        val active = PreferencesManager(context).getAPIKeys().filter { it.isActive && !it.isAutoProvisioned }
        return active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.GAPGPT }
            ?: active.firstOrNull { it.provider == com.maliar.pro.models.AIProvider.LIARA }
            ?: active.firstOrNull()
    }

    /**
     * Transcribes a short audio file (recorded for the voice-command notification feature).
     * Personal key first, then the shared proxy; returns null if neither works, so callers
     * must handle that as "couldn't understand".
     */
    suspend fun transcribeAudio(context: android.content.Context, audioFile: java.io.File): String? =
        withContext(Dispatchers.IO) {
            val key = personalKey(context)
            if (key != null) {
                return@withContext transcribeWithPersonalKey(key, audioFile)
            }
            if (!SubscriptionManager.canUseAi(context)) return@withContext null
            val result = AIBackendClient.transcribe(context, audioFile)
            if (result != null) SubscriptionManager.recordAiUsage(context)
            result
        }

    private fun transcribeWithPersonalKey(key: com.maliar.pro.models.APIKey, audioFile: java.io.File): String? {
        return try {
            val boundary = "----MaliarProBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.gapgpt.app/v1/audio/transcriptions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("Authorization", "Bearer ${key.key}")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { out ->
                fun writeField(name: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write("$value\r\n".toByteArray())
                }
                writeField("model", "whisper-1")
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n".toByteArray())
                out.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
                audioFile.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
     * Synthesizes Persian speech - via GAPGPT directly if there's a personal key, otherwise
     * via the shared server-side proxy - instead of the device's built-in TextToSpeech
     * engine, since most devices don't have a Persian voice pack installed. Returns null
     * (never throws) if neither path works, so callers must always have a non-cloud
     * fallback (e.g. a plain alarm tone) ready.
     */
    suspend fun synthesizeSpeech(context: android.content.Context, text: String): java.io.File? =
        withContext(Dispatchers.IO) {
            val key = personalKey(context)
            if (key != null && key.provider == com.maliar.pro.models.AIProvider.GAPGPT) {
                val result = synthesizeWithModel(context, key.key, text, "gpt-4o-mini-tts")
                    ?: synthesizeWithModel(context, key.key, text, "tts-1")
                if (result != null) return@withContext result
                // Fall through to the shared proxy rather than giving up, in case the
                // personal key itself is the problem (expired, out of quota, etc.).
            }
            if (!SubscriptionManager.canUseAi(context)) return@withContext null
            val result = AIBackendClient.synthesize(context, text)
            if (result != null) SubscriptionManager.recordAiUsage(context)
            result
        }

    private fun synthesizeWithModel(context: android.content.Context, apiKey: String, text: String, model: String): java.io.File? {
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
     * Sends a short prompt to the person's personal key if they have one, otherwise the
     * shared proxy, and returns the generated text - or null if neither works. Callers
     * should always have a non-AI fallback ready.
     */
    suspend fun generateText(context: android.content.Context, systemPrompt: String, userPrompt: String): String? =
        withContext(Dispatchers.IO) {
            val key = personalKey(context)
            if (key != null) {
                val result = generateWithPersonalKey(key, systemPrompt, userPrompt)
                if (result != null) return@withContext result
                // Same reasoning as synthesizeSpeech: don't give up just because the
                // person's own key happened to fail this one time.
            }
            if (!SubscriptionManager.canUseAi(context)) return@withContext null
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
            }
            val result = AIBackendClient.chat(context, messages)
            if (result != null) SubscriptionManager.recordAiUsage(context)
            result
        }

    private fun generateWithPersonalKey(
        key: com.maliar.pro.models.APIKey,
        systemPrompt: String,
        userPrompt: String
    ): String? {
        return try {
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
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
                })
                put("max_tokens", 200)
                put("temperature", 0.8)
            }
            OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
