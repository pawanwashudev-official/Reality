package com.neubofy.reality.utils

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SecureTimeProvider - A clock-tamper-proof time source.
 * 
 * Instead of trusting local system time (System.currentTimeMillis()), this class uses:
 * 1. An online HTTP check to fetch network time (Google HEAD request).
 * 2. Monotonic elapsed system time (SystemClock.elapsedRealtime()) to calculate
 *    time forward since the last known sync, resisting manual system clock alterations.
 * 3. Persistence of the calculated offset so secure time works offline and survive restarts.
 * 4. A monotonic fallback that guarantees time never moves backward.
 */
object SecureTimeProvider {

    private const val PREFS_NAME = "secure_time_prefs"
    private const val KEY_TIME_OFFSET = "time_offset"
    private const val KEY_LAST_KNOWN_ELAPSED = "last_known_elapsed"
    private const val KEY_LAST_RECORDED_TIME = "last_recorded_time"

    @Volatile
    private var cachedOffset: Long? = null

    @Volatile
    private var lastSaveTime = 0L

    /**
     * Get the current time in milliseconds. Guaranteed to be monotonic and protected against clock manipulation.
     */
    fun currentTimeMillis(context: Context): Long {
        val elapsedRealtime = SystemClock.elapsedRealtime()

        val prefs = SecurePreferences.get(context, PREFS_NAME)
        
        // Load offset
        val offset = cachedOffset ?: synchronized(this) {
            var savedOffset = prefs.getLong(KEY_TIME_OFFSET, 0L)
            if (savedOffset == 0L) {
                savedOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
                prefs.edit().putLong(KEY_TIME_OFFSET, savedOffset).apply()
            }
            cachedOffset = savedOffset
            savedOffset
        }

        // Calculated time: current elapsed system time + established offset from network sync
        var calculatedTime = elapsedRealtime + offset

        // Retrieve last known elapsed time and recorded time to detect reboot
        val lastKnownElapsed = prefs.getLong(KEY_LAST_KNOWN_ELAPSED, 0L)
        val lastRecordedTime = prefs.getLong(KEY_LAST_RECORDED_TIME, 0L)

        // If elapsedRealtime has reset (reboot) or is lower than last known elapsed,
        // we recalculate the offset using the last recorded time as the minimum baseline time.
        if (elapsedRealtime < lastKnownElapsed) {
            val newOffset = lastRecordedTime - elapsedRealtime
            cachedOffset = newOffset
            prefs.edit()
                .putLong(KEY_TIME_OFFSET, newOffset)
                .putLong(KEY_LAST_KNOWN_ELAPSED, elapsedRealtime)
                .apply()
            calculatedTime = lastRecordedTime
        } else {
            // Check if user set system clock backward below last recorded time
            if (calculatedTime < lastRecordedTime) {
                calculatedTime = lastRecordedTime
            }
            
            // Save state periodically / incrementally (at most once every 15 seconds to prevent hammering disk/prefs)
            val currentElapsed = SystemClock.elapsedRealtime()
            if (currentElapsed - lastSaveTime > 15000) {
                lastSaveTime = currentElapsed
                prefs.edit()
                    .putLong(KEY_LAST_KNOWN_ELAPSED, elapsedRealtime)
                    .putLong(KEY_LAST_RECORDED_TIME, calculatedTime)
                    .apply()
            }
        }

        return calculatedTime
    }

    /**
     * Synchronizes local elapsed offset with network time.
     * Called on background task (e.g. during sync, heartbeat, or app start).
     */
    suspend fun syncWithNetwork(context: Context): Boolean = withContext(Dispatchers.IO) {
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
                    val networkTime = date.time
                    val elapsedRealtime = SystemClock.elapsedRealtime()
                    
                    // Offset = Network Time - Monotonic System Time
                    val offset = networkTime - elapsedRealtime
                    
                    synchronized(SecureTimeProvider) {
                        cachedOffset = offset
                    }

                    SecurePreferences.get(context, PREFS_NAME).edit()
                        .putLong(KEY_TIME_OFFSET, offset)
                        .putLong(KEY_LAST_KNOWN_ELAPSED, elapsedRealtime)
                        .putLong(KEY_LAST_RECORDED_TIME, networkTime)
                        .apply()
                    
                    TerminalLogger.log("SECURE TIME: Synced with network. Time: " + format.format(date))
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("SECURE TIME ERROR: Network sync failed: ${e.message}")
        }
        return@withContext false
    }
}
