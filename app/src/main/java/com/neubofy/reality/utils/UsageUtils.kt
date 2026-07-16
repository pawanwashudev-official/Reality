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

    private var cachedUsageMap: Map<String, Long>? = null
    private var lastUsageFetchTime = 0L
    private const val USAGE_CACHE_EXPIRY_MS = 60_000L // 1 minute cache

    /**
     * PRIMARY METHOD - Uses system's queryAndAggregateUsageStats for app usage.
     * This is the GOLD STANDARD that matches Digital Wellbeing exactly.
     * 
     * Includes an automatic 1-minute memory cache to keep the app extremely fast and fluid
     * across different settings pages (like Gamification and Usage Limits) without
     * constantly hammering the system UsageStatsManager.
     */
    fun getUsageSinceMidnight(context: Context, forceRefresh: Boolean = false): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedUsageMap != null && (now - lastUsageFetchTime < USAGE_CACHE_EXPIRY_MS)) {
            return cachedUsageMap!!
        }

        val freshMap = getUsageForDate(context, java.time.LocalDate.now())
        cachedUsageMap = freshMap
        lastUsageFetchTime = now
        return freshMap
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
            // Query starting 2 hours early to catch apps that were opened before midnight
            // but stayed open past midnight.
            val queryStart = startOfDay - (2 * 60 * 60 * 1000L)
            val events = usm.queryEvents(queryStart, endOfDay)
            val event = android.app.usage.UsageEvents.Event()
            
            val appUsageMap = mutableMapOf<String, Long>()
            val lastForegroundEventTime = mutableMapOf<String, Long>()
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg.isNullOrEmpty()) continue
                
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundEventTime[pkg] = event.timeStamp
                } else if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    val lastTime = lastForegroundEventTime[pkg]
                    if (lastTime != null) {
                        // We only care about time spent AFTER startOfDay
                        val effectiveStart = if (lastTime < startOfDay) startOfDay else lastTime
                        val effectiveEnd = if (event.timeStamp < startOfDay) startOfDay else event.timeStamp
                        
                        if (effectiveEnd > effectiveStart) {
                            val duration = effectiveEnd - effectiveStart
                            appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
                        }
                        lastForegroundEventTime.remove(pkg)
                    }
                }
            }
            
            // Handle apps still in foreground right now
            val now = System.currentTimeMillis()
            val endBound = if (endOfDay > now) now else endOfDay
            
            for ((pkg, lastTime) in lastForegroundEventTime) {
                val effectiveStart = if (lastTime < startOfDay) startOfDay else lastTime
                if (endBound > effectiveStart) {
                    val duration = endBound - effectiveStart
                    appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
                }
            }
            
            appUsageMap
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
        var maxStreakMillis = 0L
        var totalScreenTimeMs = 0L
        
        var lastOnTime = 0L
        var lastOffTime = 0L
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE || 
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                
                // Deduplicate events
                if (lastOnTime == 0L || (event.timeStamp - lastOnTime > 1000)) {
                    lastOnTime = event.timeStamp
                    if (event.timeStamp >= startOfDay) {
                        unlockCount++
                    }
                    
                    // Streak check: Time from last OFF to this ON
                    if (lastOffTime != 0L) {
                         val streakStart = if (lastOffTime < startOfDay) startOfDay else lastOffTime
                         val streakEnd = event.timeStamp
                         if (streakEnd > streakStart) {
                             val streak = streakEnd - streakStart
                             if (streak > maxStreakMillis) maxStreakMillis = streak
                         }
                    }
                }
                
            } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                if (lastOnTime != 0L) {
                    val effectiveStart = if (lastOnTime < startOfDay) startOfDay else lastOnTime
                    val effectiveEnd = if (event.timeStamp < startOfDay) startOfDay else event.timeStamp
                    
                    if (effectiveEnd > effectiveStart) {
                        totalScreenTimeMs += (effectiveEnd - effectiveStart)
                    }
                    lastOnTime = 0L
                }
                lastOffTime = event.timeStamp
            }
        }
        
        // Handle if screen stayed ON at the end of the query (e.g. 11:59:59 PM)
        if (lastOnTime != 0L) {
            val effectiveStart = if (lastOnTime < startOfDay) startOfDay else lastOnTime
            val effectiveEnd = if (endOfQuery < startOfDay) startOfDay else endOfQuery
            if (effectiveEnd > effectiveStart) {
                totalScreenTimeMs += (effectiveEnd - effectiveStart)
            }
        } else if (lastOffTime != 0L) {
             // Currently unplugged - streak potentially continuing to end of day
             val streakStart = if (lastOffTime < startOfDay) startOfDay else lastOffTime
             val streakEnd = if (endOfQuery < startOfDay) startOfDay else endOfQuery
             if (streakEnd > streakStart) {
                 val currentStreak = streakEnd - streakStart
                 if (currentStreak > maxStreakMillis) maxStreakMillis = currentStreak
             }
        }

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

        val usageMap = if (date == java.time.LocalDate.now()) {
            getUsageSinceMidnight(context)
        } else {
            getUsageForDate(context, date)
        }
        
        var total = 0L
        blockedApps.forEach { pkg ->
            total += usageMap[pkg] ?: 0L
        }
        return total
    }
}
