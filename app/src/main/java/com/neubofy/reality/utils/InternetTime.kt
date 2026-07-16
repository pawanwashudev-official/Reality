package com.neubofy.reality.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object InternetTime {
    /**
     * Fetches time from a network source (Google HEAD request).
     * Returns null if network time cannot be determined — callers MUST handle this.
     * 
     * SECURITY: Never silently fallback to System.currentTimeMillis() because
     * that is manipulable by the user (clock change bypass).
     */
    suspend fun getTimeOrNull(): Long? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.google.com")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            val dateStr = conn.getHeaderField("Date")
            if (dateStr != null) {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                val date = format.parse(dateStr)
                if (date != null) {
                    return@withContext date.time
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("INTERNET TIME: Network time fetch failed: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Backwards-compatible wrapper. Falls back to SecureTimeProvider (tamper-resistant)
     * instead of raw System.currentTimeMillis() when network is unavailable.
     */
    suspend fun getTime(context: Context? = null): Long = withContext(Dispatchers.IO) {
        val networkTime = getTimeOrNull()
        if (networkTime != null) return@withContext networkTime
        // Fallback to SecureTimeProvider (anti-tamper monotonic clock) if available
        if (context != null) {
            return@withContext SecureTimeProvider.currentTimeMillis(context)
        }
        // Last resort — still better than nothing for non-security-critical paths
        return@withContext System.currentTimeMillis()
    }
}
