package com.neubofy.reality.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageUtils {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /**
     * Accurately calculates usage by processing strict foreground transitions.
     * Prevents double-counting by tracking only ONE active app at a time.
     */
    /**
     * Calculates app usage using the System's aggregation for the specific time range.
     * This method is robust because 'queryAndAggregateUsageStats' automatically truncates 
     * stats to the given start/end interval, matching how Android Settings calculates "Daily" usage.
     */
    fun getUsageSinceMidnight(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val midnight = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        // "queryAndAggregateUsageStats" merges multiple intervals and truncates to the range.
        // This handles the "session spanning midnight" logic natively.
        val statsMap = usm.queryAndAggregateUsageStats(midnight, now)
        
        val result = mutableMapOf<String, Long>()
        for ((pkg, stats) in statsMap) {
            if (pkg == "com.android.systemui" || pkg.contains("launcher")) continue
            
            if (stats.totalTimeInForeground > 0) {
                result[pkg] = stats.totalTimeInForeground
            }
        }
        return result
    }

    /**
     * Calculates total screen-on time using system SCREEN_INTERACTIVE events.
     * This is the 'Gold Standard' for digital wellbeing apps, independent of individual app usage.
     */
    fun getScreenTimeSinceMidnight(context: Context): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val midnight = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        // Query events from 2 hours before midnight to capture ongoing sessions
        val events = usm.queryEvents(midnight - (120 * 60 * 1000L), now)
        
        var totalScreenTime = 0L
        var lastOnTime = 0L
        
        // Check initial state (was screen on at midnight?)
        // We can't easily know, but usually queryEvents includes a snapshot if we query slightly before.
        // A simple heuristic: iterate events.
        
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                 lastOnTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                 if (lastOnTime != 0L) {
                      // Calculate duration
                      val start = if (lastOnTime < midnight) midnight else lastOnTime
                      val end = if (event.timeStamp < midnight) midnight else event.timeStamp
                      
                      if (end > start) {
                          totalScreenTime += (end - start)
                      }
                      lastOnTime = 0L
                 }
            }
        }
        
        // If screen is still on
        if (lastOnTime != 0L) {
            val start = if (lastOnTime < midnight) midnight else lastOnTime
            if (now > start) {
                totalScreenTime += (now - start)
            }
        }
        
        return totalScreenTime
    }
}
