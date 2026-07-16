package com.neubofy.reality.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object InternetTime {
    suspend fun getTime(): Long = withContext(Dispatchers.IO) {
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
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
        }
        return@withContext System.currentTimeMillis()
    }
}
