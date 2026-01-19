package com.neubofy.reality.utils

import android.app.usage.UsageEvents
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
     * PRIMARY METHOD - Uses system's queryAndAggregateUsageStats for app usage.
     * This is the GOLD STANDARD that matches Digital Wellbeing exactly.
     * 
     * The system internally accounts for all foreground/background transitions
     * and provides totalTimeInForeground which is the definitive usage time.
     */
    fun getUsageSinceMidnight(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val midnight = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        return try {
            // queryAndAggregateUsageStats is what Digital Wellbeing uses
            // It returns the official system-calculated totalTimeInForeground
            val statsMap = usm.queryAndAggregateUsageStats(midnight, now)
            statsMap.mapValues { it.value.totalTimeInForeground }
        } catch (e: Exception) {
            emptyMap()
        }
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
    fun getFocusedAppsUsage(context: Context): Long {
        if (!hasUsageStatsPermission(context)) return 0L

        val prefsLoader = SavedPreferencesLoader(context)
        val focusData = prefsLoader.getFocusModeData()
        val allSelected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefsLoader.getFocusModeSelectedApps())

        val affectedPkgs = allSelected.filter { pkg ->
            prefsLoader.getBlockedAppConfig(pkg).blockInFocus
        }

        if (affectedPkgs.isEmpty()) return 0L

        val usageMap = getUsageSinceMidnight(context)
        var total = 0L
        affectedPkgs.forEach { pkg ->
            total += usageMap[pkg] ?: 0L
        }
        return total
    }
}
