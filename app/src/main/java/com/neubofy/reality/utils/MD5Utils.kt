package com.neubofy.reality.utils

import java.security.MessageDigest

object MD5Utils {
    /**
     * Generates an MD5 hash of the given email and returns the first 16 characters.
     * This acts as the unique User ID.
     */
    fun getUserIdFromEmail(email: String): String {
        if (email.isBlank()) return "Unknown"

        try {
            val md = MessageDigest.getInstance("MD5")
            val hashBytes = md.digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()

            for (byte in hashBytes) {
                val hex = Integer.toHexString(0xFF and byte.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }

            // Return first 16 characters
            return if (hexString.length >= 16) hexString.substring(0, 16) else hexString.toString()

        } catch (e: Exception) {
            e.printStackTrace()
            return "ErrorGeneratingId"
        }
    }
}
