package com.neubofy.reality.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar
import java.time.LocalDate
import java.time.ZoneId

object UsageUtils {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
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
        return getUsageForDate(context, java.time.LocalDate.now())
    }

    /**
     * Get usage for a specific date.
     */
    fun getUsageForDate(context: Context, date: java.time.LocalDate): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val startOfDay = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        return try {
            val statsMap = usm.queryAndAggregateUsageStats(startOfDay, endOfDay)
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
    data class ProUsageMetrics(
        val screenTimeMs: Long,
        val pickupCount: Int,
        val longestStreakMs: Long
    )

    /**
     * Efficiently calculates Screen Time, Pickups, and Longest Unplugged Streak in one pass.
     */
    fun getProUsageMetrics(context: Context): ProUsageMetrics {
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
        var lastOffTime = 0L // For streak calculation
        
        var unlocks = 0
        var maxStreak = 0L
        
        // Process events
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            
            // 1. Screen Time & Streak Logic
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE || 
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                 
                 // Deduplicate events (sometimes both fire)
                 if (lastOnTime == 0L || (event.timeStamp - lastOnTime > 1000)) {
                     lastOnTime = event.timeStamp
                     if (event.timeStamp >= midnight) {
                         unlocks++
                     }
                     
                     // Calculate Streak (Gap since last OFF)
                     if (lastOffTime != 0L) {
                         val streakStart = if (lastOffTime < midnight) midnight else lastOffTime
                         val streakEnd = event.timeStamp
                         
                         if (streakEnd > streakStart) {
                             val streak = streakEnd - streakStart
                             if (streak > maxStreak) maxStreak = streak
                         }
                     }
                 }
                 
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
                 lastOffTime = event.timeStamp
            }
        }
        
        // Handle current state
        if (lastOnTime != 0L) {
            val start = if (lastOnTime < midnight) midnight else lastOnTime
            if (now > start) {
                totalScreenTime += (now - start)
            }
        } else if (lastOffTime != 0L) {
            // Currently unplugged - streak potentially continuing
             val streakStart = if (lastOffTime < midnight) midnight else lastOffTime
             if (now > streakStart) {
                 val currentStreak = now - streakStart
                 if (currentStreak > maxStreak) maxStreak = currentStreak
             }
        }
        
        return ProUsageMetrics(totalScreenTime, unlocks, maxStreak)
    }

    /**
     * Calculates Pro Metrics for a specific date (historical calculation).
     */
    fun getProUsageMetrics(context: Context, date: LocalDate): ProUsageMetrics {
        if (!hasUsageStatsPermission(context)) return ProUsageMetrics(0, 0, 0)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Calculate start and end of specific date
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfQuery = if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        }
        
        // Start from startOfDay. (Maybe subtract 2h to catch ongoing streaks from prev day? 
        // But for strict daily reporting, let's start at midnight to verify "Usage ON that day")
        // Actually, existing getProUsageMetrics uses midnight-2h. 
        // Let's mimic that consistency: start query 2h before to catch "Last ON" event if it crossed midnight.
        val queryStart = startOfDay - (120 * 60 * 1000L)

        val events = usageStatsManager.queryEvents(queryStart, endOfQuery)
        val event = UsageEvents.Event()

        var unlockCount = 0
        var lastLockTime = 0L 
        var lastOffTime = 0L
        var maxStreakMillis = 0L
        var isScreenOn = false 
        
        // We only care about unlocks happening AFTER startOfDay
        // But we need earlier events to know initial state (isScreenOn) at startOfDay?
        // Simpler approach: Just count unlocks strictly within the window.
        
        // Loop
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            val isInsideDay = event.timeStamp >= startOfDay
            
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE || 
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                
                // Deduplicate unlocks (similar to today logic)
                if (isInsideDay) {
                    if (lastOffTime != 0L && (event.timeStamp - lastOffTime > 1000)) {
                         unlockCount++
                         
                         // Streak check: Time from last LOCK to this UNLOCK
                         val streakEnd = event.timeStamp
                         val streakStart = if (lastLockTime < startOfDay) startOfDay else lastLockTime
                         
                         if (streakEnd > streakStart) {
                             val streak = streakEnd - streakStart
                             if (streak > maxStreakMillis) maxStreakMillis = streak
                         }
                    }
                }
                
                isScreenOn = true
                
            } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                lastLockTime = event.timeStamp
                lastOffTime = event.timeStamp
                isScreenOn = false
            }
        }
        
        // Final streak check (if screen stayed off until end of the day)
        if (!isScreenOn && lastLockTime != 0L) {
             val streakEnd = endOfQuery
             val streakStart = if (lastLockTime < startOfDay) startOfDay else lastLockTime
             if (streakEnd > streakStart) {
                 val currentStreak = streakEnd - streakStart
                 if (currentStreak > maxStreakMillis) maxStreakMillis = currentStreak
             }
        }

        // Screen time: Use official aggregate API for device-wide total (Gold Standard)
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startOfDay, endOfQuery)
        val totalScreenTimeMs = statsMap.values.sumOf { it.totalTimeInForeground }
        
        return ProUsageMetrics(totalScreenTimeMs, unlockCount, maxStreakMillis)
    }

    fun getFocusedAppsUsage(context: Context): Long {
        return getFocusedAppsUsageForDate(context, java.time.LocalDate.now())
    }

    fun getFocusedAppsUsageForDate(context: Context, date: java.time.LocalDate): Long {
        if (!hasUsageStatsPermission(context)) return 0L

        val prefsLoader = SavedPreferencesLoader(context)
        val focusData = prefsLoader.getFocusModeData()
        // Defensive copy with manual null checks to handle Gson/R8 potential failures
        val apps = focusData.selectedApps
        val legacyApps = prefsLoader.getFocusModeSelectedApps()
        
        val allSelected = if (apps != null && apps.isNotEmpty()) apps else HashSet(legacyApps)

        val affectedPkgs = allSelected.filter { pkg ->
            prefsLoader.getBlockedAppConfig(pkg).blockInFocus
        }

        if (affectedPkgs.isEmpty()) return 0L

        val usageMap = getUsageForDate(context, date)
        var total = 0L
        affectedPkgs.forEach { pkg ->
            total += usageMap[pkg] ?: 0L
        }
        return total
    }

    /**
     * UNIFIED: Get usage for a specific set of blocked apps on a given date.
     * Use this when you already have the blocklist from loadBlockedApps().
     */
    fun getBlockedAppsUsageForDate(context: Context, date: java.time.LocalDate, blockedApps: Set<String>): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        if (blockedApps.isEmpty()) return 0L

        val usageMap = getUsageForDate(context, date)
        var total = 0L
        blockedApps.forEach { pkg ->
            total += usageMap[pkg] ?: 0L
        }
        return total
    }
}
