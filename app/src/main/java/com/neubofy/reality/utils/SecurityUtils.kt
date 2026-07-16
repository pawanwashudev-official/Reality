package com.neubofy.reality.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

/**
 * Security utilities for password hashing and validation.
 * Centralizes all security-related functions to prevent code duplication.
 */
object SecurityUtils {
    
    // Kept for backward compatibility validation only
    private fun hashPasswordOld(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hashes a password using PBKDF2WithHmacSHA256
     * @param password The plain text password to hash
     * @return The formatted hash string pbkdf2_sha256$iterations$saltBase64$hashBase64
     */
    fun hashPassword(password: String): String {
        val iterations = 10000
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "pbkdf2_sha256\$$iterations\$$saltBase64\$$hashBase64"
    }

    /**
     * Verifies a password against a stored hash (supports new PBKDF2 or old SHA-256)
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("pbkdf2_sha256$")) {
            // Fallback for old 64-char SHA-256 hashes
            return hashPasswordOld(password) == storedHash
        }

        try {
            val parts = storedHash.split("$")
            if (parts.size != 4) return false

            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val storedHashBytes = Base64.decode(parts[3], Base64.NO_WRAP)

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val computedHash = factory.generateSecret(spec).encoded

            return MessageDigest.isEqual(storedHashBytes, computedHash)
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
            return false
        }
    }
    
    /**
     * Validates password strength
     * @param password The password to validate
     * @return Pair of (isValid, errorMessage)
     */
    fun validatePassword(password: String): Pair<Boolean, String> {
        return when {
            password.length < 6 -> Pair(false, "Password must be at least 6 characters")
            password.length > 50 -> Pair(false, "Password too long")
            else -> Pair(true, "")
        }
    }
    
    /**
     * Validates keywords input
     * Filters empty, duplicates, and too-long keywords
     * @param input Comma-separated keywords string
     * @param maxKeywords Maximum number of keywords allowed
     * @param maxLength Maximum length per keyword
     * @return Cleaned list of keywords
     */
    fun validateKeywords(
        input: String, 
        maxKeywords: Int = 10, 
        maxLength: Int = 50
    ): List<String> {
        return input
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length <= maxLength }
            .distinct()
            .take(maxKeywords)
    }
}
