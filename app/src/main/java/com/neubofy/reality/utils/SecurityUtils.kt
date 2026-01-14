package com.neubofy.reality.utils

import java.security.MessageDigest

/**
 * Security utilities for password hashing and validation.
 * Centralizes all security-related functions to prevent code duplication.
 */
object SecurityUtils {
    
    /**
     * Hashes a password using SHA-256
     * @param password The plain text password to hash
     * @return The hex-encoded SHA-256 hash
     */
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Validates password strength
     * @param password The password to validate
     * @return Pair of (isValid, errorMessage)
     */
    fun validatePassword(password: String): Pair<Boolean, String> {
        return when {
            password.length < 4 -> Pair(false, "Password must be at least 4 characters")
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
