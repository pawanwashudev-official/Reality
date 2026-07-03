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


    private fun getSecretKey(context: android.content.Context): SecretKey {
        val email = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: ""
        val password = com.neubofy.reality.utils.IdentityManager.getBackupPassword(context, email)

        val keyMaterial = password.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val rawKey = md.digest(keyMaterial)
        return javax.crypto.spec.SecretKeySpec(rawKey, "AES")
    }

    fun encrypt(context: android.content.Context, data: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(context))

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)

            return "ENC:$ivBase64:$encryptedDataBase64"
        } catch (e: Exception) {
            e.printStackTrace()
            return data
        }
    }

    fun decrypt(context: android.content.Context, encryptedString: String): String {
        try {
            if (!encryptedString.startsWith("ENC:")) return encryptedString

            val parts = encryptedString.substring(4).split(":")
            if (parts.size != 2) return encryptedString

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedData = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(context), spec)

            val decryptedData = cipher.doFinal(encryptedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return encryptedString
        }
    }
}
