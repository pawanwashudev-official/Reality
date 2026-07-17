package com.neubofy.reality.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BackupEncryption {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "RealityBackupEncryptionKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128


    fun getSecretKeyFromPassword(password: String?): SecretKey {
        require(!password.isNullOrEmpty()) { "Encryption password must not be null or empty" }

        val md = java.security.MessageDigest.getInstance("SHA-256")
        val rawKey = md.digest(password.toByteArray())
        return javax.crypto.spec.SecretKeySpec(rawKey, "AES")
    }

    private fun getSecretKey(context: android.content.Context): SecretKey {
        val password = com.neubofy.reality.utils.IdentityManager.getBackupPassword(context)
        return getSecretKeyFromPassword(password)
    }


    fun encrypt(context: android.content.Context, data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(context))

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)

        return "ENC:$ivBase64:$encryptedDataBase64"
    }

    fun decrypt(context: android.content.Context, encryptedString: String, overridePassword: String? = null): String {
        try {
            if (!encryptedString.startsWith("ENC:")) {
                return encryptedString // Not encrypted
            }

            val parts = encryptedString.substring(4).split(":")
            if (parts.size != 2) return encryptedString

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedData = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, if (overridePassword != null) getSecretKeyFromPassword(overridePassword) else getSecretKey(context), spec)

            val decryptedData = cipher.doFinal(encryptedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failed with the static key. 
            // Fallback: try decrypting with the rotating subscription password (connectionSecret)
            // for backups that were created during the subscription era.
            try {
                if (overridePassword == null) {
                    val fallbackPassword = com.neubofy.reality.utils.IdentityManager.getConnectionSecret(context)
                    val parts = encryptedString.substring(4).split(":")
                    if (parts.size == 2) {
                        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                        val encryptedData = Base64.decode(parts[1], Base64.NO_WRAP)
                        val cipherFallback = Cipher.getInstance(TRANSFORMATION)
                        val specFallback = GCMParameterSpec(TAG_LENGTH, iv)
                        cipherFallback.init(Cipher.DECRYPT_MODE, getSecretKeyFromPassword(fallbackPassword), specFallback)
                        val decryptedData = cipherFallback.doFinal(encryptedData)
                        return String(decryptedData, Charsets.UTF_8)
                    }
                }
            } catch (fallbackE: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("Fallback ERROR: ${fallbackE.message}")
            }

            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
            // Return original if decryption fails (might be plain text that happened to start with ENC:)
            return encryptedString
        }
    }
}
