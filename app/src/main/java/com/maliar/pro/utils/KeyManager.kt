package com.maliar.pro.utils

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KeyManager {
    private const val KEYS_URL = "https://abrehamrahi.ir/o/public/eUFcsXOX"
    private const val BACKUP_URL = "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/c93c06d1b2f38c65ee30f092c134a89998326d12/keys.txt"
    private const val ENCRYPTION_KEY = "maliar_pro_secret_key_2026" // Should be securely managed

    data class ApiKeys(
        val gapgpt: String,
        val liara: String
    )

    private var cachedKeys: ApiKeys? = null

    suspend fun loadKeys(context: Context): ApiKeys {
        cachedKeys?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val keysJson = downloadKeys()
                val decrypted = decrypt(keysJson)
                val keys = parseKeys(decrypted)
                cachedKeys = keys
                keys
            } catch (e: Exception) {
                // Fallback or error handling
                ApiKeys("default_gap_key", "default_liara_key")
            }
        }
    }

    private fun downloadKeys(): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(KEYS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun decrypt(encrypted: String): String {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(ENCRYPTION_KEY.toByteArray())
            .copyOf(16)
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decoded = Base64.decode(encrypted, Base64.DEFAULT)
        return String(cipher.doFinal(decoded))
    }

    private fun parseKeys(json: String): ApiKeys {
        val type = object : TypeToken<ApiKeys>() {}.type
        return Gson().fromJson(json, type)
    }

    fun getGapGptKey(): String = cachedKeys?.gapgpt ?: ""
    fun getLiaraKey(): String = cachedKeys?.liara ?: ""
}
