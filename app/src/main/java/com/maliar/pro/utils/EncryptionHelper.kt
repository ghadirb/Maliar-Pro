package com.maliar.pro.utils

import android.util.Base64
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 20000
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    fun decrypt(encryptedBase64: String, password: String): String {
        try {
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
            
            val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
            val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val ciphertext = encryptedData.copyOfRange(SALT_LENGTH + IV_LENGTH, encryptedData.size)
            
            val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM)
            val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionException("Decryption error: ${e.message}", e)
        }
    }

    class DecryptionException(message: String, cause: Throwable) : Exception(message, cause)
}
