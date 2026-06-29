package com.maliar.pro.utils

import android.content.Context
import android.util.Log
import com.maliar.pro.models.AIProvider
import com.maliar.pro.models.APIKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AutoProvisioningManager {
    
    private const val TAG = "AutoProvisioning"
    private const val DEFAULT_PASSWORD = "12345"
    private const val OLD_KEYS_URL = "https://abrehamrahi.ir/o/public/eUFcsXOX/"
    private const val GIST_KEYS_URL = "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/c93c06d1b2f38c65ee30f092c134a89998326d12/keys.txt"

    suspend fun autoProvision(context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting auto-provisioning with fallback...")

            // Try old URL first
            val oldResult = tryLoadFromUrl(OLD_KEYS_URL, "Old URL", context)
            if (oldResult.isSuccess && hasRequiredKeys(oldResult.getOrThrow())) {
                Log.d(TAG, "✅ Required keys found from old URL")
                return@withContext oldResult
            }

            // Fallback to new URL
            Log.d(TAG, "⚠️ Old URL incomplete, trying new URL...")
            val newResult = tryLoadFromUrl(GIST_KEYS_URL, "New URL (Gist)", context)
            if (newResult.isSuccess) {
                Log.d(TAG, "✅ Keys loaded from new URL")
                return@withContext newResult
            }

            // If old URL had some valid keys, keep them
            if (oldResult.isSuccess) {
                val oldKeys = oldResult.getOrNull().orEmpty()
                if (oldKeys.isNotEmpty()) {
                    Log.w(TAG, "⚠️ New URL failed; using valid keys from old URL: ${oldKeys.size}")
                    return@withContext oldResult
                }
            }

            return@withContext Result.failure(Exception("No key sources responded"))

        } catch (e: Exception) {
            Log.e(TAG, "Loading error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun tryLoadFromUrl(url: String, sourceName: String, context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Downloading encrypted file from $sourceName: $url")

            // 1) Download
            val encryptedData = runCatching {
                DriveHelper.downloadFromUrl(url)
            }.getOrElse { e ->
                Log.e(TAG, "❌ Download error from $sourceName: ${e.message}")
                return@withContext Result.failure(e)
            }

            if (encryptedData.isBlank()) {
                Log.e(TAG, "❌ Downloaded file from $sourceName is empty")
                return@withContext Result.failure(Exception("Keys file is empty"))
            }

            // 2) Decrypt
            val decryptedData = runCatching {
                EncryptionHelper.decrypt(encryptedData, DEFAULT_PASSWORD)
            }.onFailure {
                Log.e(TAG, "❌ Decryption error from $sourceName: ${it.message}")
                Log.e(TAG, "Downloaded (preview): ${encryptedData.take(120)}")
            }.getOrElse { e ->
                return@withContext Result.failure(e)
            }

            if (decryptedData.isBlank()) {
                Log.e(TAG, "❌ Decrypted file from $sourceName is empty")
                return@withContext Result.failure(Exception("Decryption failed (empty output)"))
            }

            Log.d(TAG, "📝 Decrypted content from $sourceName:")
            decryptedData.lines().forEach { line ->
                Log.d(TAG, "  > $line")
            }

            // 3) Parse and normalize
            val parsed = parseAPIKeys(decryptedData)
            if (parsed.isEmpty()) {
                Log.w(TAG, "⚠️ No valid keys found from $sourceName")
                return@withContext Result.failure(Exception("No valid keys found in file"))
            }

            val processedKeys = parsed.map { key ->
                val inferredProvider = key.provider
                val defaultBase = when {
                    inferredProvider == AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                    inferredProvider == AIProvider.AIML -> "https://api.aimlapi.com/v1"
                    inferredProvider == AIProvider.GLADIA -> "https://api.gladia.io"
                    inferredProvider == AIProvider.GAPGPT -> "https://api.gapgpt.app/v1"
                    inferredProvider == AIProvider.OPENROUTER && key.key.startsWith("hf_") ->
                        "https://router.huggingface.co/models/openai/whisper-large-v3"
                    inferredProvider == AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
                    inferredProvider == AIProvider.OPENAI -> "https://api.openai.com/v1"
                    else -> key.baseUrl
                }
                key.copy(
                    isActive = true,
                    baseUrl = key.baseUrl ?: defaultBase
                )
            }

            Log.d(TAG, "✅ Parsed keys from $sourceName: ${processedKeys.size}")
            processedKeys.forEach { key ->
                Log.d(TAG, "  - ${key.provider.name}: ${key.key.take(10)}... base=${key.baseUrl}")
            }

            // 4) Save and activate in Preferences
            val prefsManager = PreferencesManager(context)
            prefsManager.saveAPIKeys(processedKeys)
            Log.d(TAG, "✅ ${processedKeys.size} keys saved and activated in prefs")

            Result.success(processedKeys)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from $sourceName: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun hasRequiredKeys(keys: List<APIKey>): Boolean {
        val hasGapgpt = keys.any { it.provider == AIProvider.GAPGPT }
        val hasLiara = keys.any { it.provider == AIProvider.LIARA }
        
        Log.d(TAG, "🔍 Checking required keys: GAPGPT=$hasGapgpt, Liara=$hasLiara")
        
        return hasGapgpt && hasLiara
    }
    
    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            val (provider, key, baseUrl) = parseKeyLine(trimmed)
            if (provider != null && key.isNotBlank()) {
                keys.add(
                    APIKey(
                        provider = provider,
                        key = key,
                        baseUrl = baseUrl,
                        isActive = false
                    )
                )
                Log.d(TAG, "✓ Parsed: ${provider.name}")
            } else {
                Log.w(TAG, "Invalid line: $trimmed")
            }
        }
        
        return keys
    }
    
    private fun parseKeyLine(line: String): Triple<AIProvider?, String, String?> {
        val parts = line.split(":", limit = 3).map { it.trim() }
        
        if (parts.size >= 2) {
            val provider = when (parts[0].lowercase()) {
                "liara" -> AIProvider.LIARA
                "openai", "gpt" -> AIProvider.OPENAI
                "anthropic", "claude" -> AIProvider.ANTHROPIC
                "openrouter" -> AIProvider.OPENROUTER
                "aiml", "aimlapi" -> AIProvider.AIML
                "gladia" -> AIProvider.GLADIA
                "huggingface", "hf" -> AIProvider.OPENROUTER
                "gapgpt" -> AIProvider.GAPGPT
                else -> null
            }
            
            if (provider != null) {
                val key = parts.getOrNull(1) ?: ""
                val baseUrl = parts.getOrNull(2)
                return Triple(provider, key, baseUrl)
            }
        }

        val inferredProvider = inferProviderFromRawKey(line)
        return Triple(inferredProvider, line, null)
    }

    private fun inferProviderFromRawKey(raw: String): AIProvider? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()

        if (lower.startsWith("sk-or")) return AIProvider.OPENROUTER
        if (lower.startsWith("aiml") || lower.startsWith("sk-aiml")) return AIProvider.AIML
        if (trimmed.startsWith("eyJ")) return AIProvider.LIARA
        if (trimmed.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$")))
            return AIProvider.GLADIA
        if (lower.startsWith("hf_")) return AIProvider.OPENROUTER
        if (trimmed.matches(Regex("^[a-fA-F0-9]{32}\$"))) return AIProvider.AIML
        if (lower.startsWith("sk-") && trimmed.length > 50) return AIProvider.GAPGPT
        if (lower.startsWith("sk-")) return AIProvider.OPENAI
        if (trimmed.startsWith("AIza")) return AIProvider.OPENAI

        return null
    }
}
