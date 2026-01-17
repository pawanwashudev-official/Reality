package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tapasya Session Entity
 * 
 * Stores completed Tapasya meditation/focus sessions.
 * ID format: {startTime}_{endTime} for uniqueness
 */
@Entity(tableName = "tapasya_sessions")
data class TapasyaSession(
    @PrimaryKey
    val sessionId: String, // Format: startTime_endTime (unique)
    
    val name: String, // Session name (user-set or from calendar)
    val targetTimeMs: Long, // Target duration in ms (15-min multiples)
    val startTime: Long, // When session started (epoch ms)
    val endTime: Long, // When session ended (epoch ms)
    val effectiveTimeMs: Long, // Effective time (floor to 15-min)
    val totalPauseMs: Long, // Total time spent paused
    val pauseLimitMs: Long, // Configured pause limit
    val wasAutoStopped: Boolean, // True if auto-stopped due to pause limit
    val calendarEventId: String? = null // Link to calendar event if started from calendar
) {
    companion object {
        fun generateId(startTime: Long, endTime: Long): String = "${startTime}_${endTime}"
        
        /**
         * Calculate effective time (floor to nearest 15 minutes)
         */
        fun calculateEffectiveTime(elapsedMs: Long): Long {
            val fifteenMins = 15 * 60 * 1000L
            return (elapsedMs / fifteenMins) * fifteenMins
        }
    }
}
