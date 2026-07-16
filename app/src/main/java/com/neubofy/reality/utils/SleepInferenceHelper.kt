package com.neubofy.reality.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Premium Sleep Inference Algorithm
 * 
 * Approach: Merged Gap Analysis
 * 1. Collect all screen-off periods within the bedtime window
 * 2. Merge gaps where screen-on duration between them is < 5 minutes (brief phone checks)
 * 3. Find the longest merged gap
 * 4. Validate minimum sleep duration (3+ hours)
 * 5. Score by overlap with planned bedtime for tie-breaking
 */
object SleepInferenceHelper {

    private const val MERGE_THRESHOLD_MS = 5 * 60 * 1000L  // 5 minutes - brief check threshold
    private const val MIN_SLEEP_DURATION_MS = 3 * 60 * 60 * 1000L  // 3 hours minimum sleep

    data class ScreenGap(
        var startMs: Long,
        var endMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * @param date The "wake up" date (e.g., Jan 26 means sleep from Jan 25 night to Jan 26 morning)
     * @param force If true, bypasses the timing guard (useful when triggered by alarm)
     * @return Pair of (sleepStart, sleepEnd)
     */
    suspend fun inferSleepSession(context: Context, date: LocalDate, force: Boolean = false): Pair<Instant, Instant>? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager 
            ?: return null
        val loader = SavedPreferencesLoader(context)
        val bedtimeData = loader.getBedtimeData()
        
        val planStartMins = bedtimeData.startTimeInMins
        val planEndMins = bedtimeData.endTimeInMins
        
        if (!bedtimeData.isEnabled) {
            TerminalLogger.log("SleepInference: Bedtime schedule is disabled. Not inferring.")
            return null
        }
        
        // ========== TIMING GUARD: Only run after bedtime ends ==========
        if (bedtimeData.isEnabled && !force) {
            val nowTime = java.time.LocalTime.now()
            val nowMins = (nowTime.hour * 60) + nowTime.minute
            
            // If current time is before planned wake-up, don't infer yet
            if (nowMins < planEndMins) {
                TerminalLogger.log("SleepInference: Too early (${nowMins} < ${planEndMins}), skipping")
                return null
            }
        }
        
        // Calculate planned window
        val planEndTime = date.atTime(planEndMins / 60, planEndMins % 60)
            .atZone(ZoneId.systemDefault()).toInstant()

        val planStartTime = if (planStartMins > planEndMins) {
            // Crosses midnight (e.g. 22:00 -> 07:00)
            date.minusDays(1).atTime(planStartMins / 60, planStartMins % 60)
                .atZone(ZoneId.systemDefault()).toInstant()
        } else {
            // Same day (e.g. 01:00 -> 09:00)
            date.atTime(planStartMins / 60, planStartMins % 60)
                .atZone(ZoneId.systemDefault()).toInstant()
        }

        // Expand window for edge detection (+/- 2 hours)
        val windowStart = planStartTime.minusSeconds(7200).toEpochMilli()
        val windowEnd = planEndTime.plusSeconds(7200).toEpochMilli()

        // ========== PHASE 1: Collect Raw Screen-Off Gaps ==========
        val rawGaps = mutableListOf<ScreenGap>()
        val events = usm.queryEvents(windowStart, windowEnd)
        val event = UsageEvents.Event()

        var lastOffTime: Long? = null
        var isScreenOn = true  // Assume screen was on at start (conservative)

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (isScreenOn) {
                        lastOffTime = event.timeStamp
                        isScreenOn = false
                    }
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!isScreenOn && lastOffTime != null) {
                        // Record the gap
                        rawGaps.add(ScreenGap(lastOffTime, event.timeStamp))
                        lastOffTime = null
                    }
                    isScreenOn = true
                }
            }
        }

        // Handle case where screen is still off at end of window
        if (!isScreenOn && lastOffTime != null) {
            rawGaps.add(ScreenGap(lastOffTime, windowEnd))
        }

        // FALLBACK: If no gaps found, user was on phone entire night - Return Planned Bedtime
        if (rawGaps.isEmpty()) {
            TerminalLogger.log("SleepInference: No screen-off gaps found, falling back to plan")
            return Pair(planStartTime, planEndTime)
        }

        // ========== PHASE 2: Merge Adjacent Gaps (Brief Checks) ==========
        val mergedGaps = mutableListOf<ScreenGap>()
        var currentMerged = rawGaps[0].copy()

        for (i in 1 until rawGaps.size) {
            val nextGap = rawGaps[i]
            val screenOnDuration = nextGap.startMs - currentMerged.endMs
            
            if (screenOnDuration <= MERGE_THRESHOLD_MS) {
                // Brief phone check - merge the gaps
                currentMerged.endMs = nextGap.endMs
            } else {
                // Significant phone use - finalize current and start new
                mergedGaps.add(currentMerged)
                currentMerged = nextGap.copy()
            }
        }
        mergedGaps.add(currentMerged)

        TerminalLogger.log("SleepInference: ${rawGaps.size} raw gaps -> ${mergedGaps.size} merged gaps")

        // ========== PHASE 3: Find Best Gap (Longest + Overlaps Bedtime) ==========
        val planStartMs = planStartTime.toEpochMilli()
        val planEndMs = planEndTime.toEpochMilli()

        // Score each gap by duration + overlap with plan
        val scoredGaps = mergedGaps
            .filter { it.durationMs >= MIN_SLEEP_DURATION_MS }  // Must be at least 3 hours
            .map { gap ->
                val overlapStart = maxOf(gap.startMs, planStartMs)
                val overlapEnd = minOf(gap.endMs, planEndMs)
                val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0)
                
                // Score = 70% duration + 30% overlap (prioritize actual sleep length)
                val score = (gap.durationMs * 0.7) + (overlapMs * 0.3)
                Pair(gap, score)
            }
            .sortedByDescending { it.second }

        // If no gap >= 3h found, do NOT blindly suggest the scheduled plan. 
        // This makes the algorithm smart: only suggest if actual sleep-like phone usage is detected.
        if (scoredGaps.isEmpty()) {
            TerminalLogger.log("SleepInference: No gaps >= 3h found, returning null (no sleep detected)")
            return null
        }

        val bestGap = scoredGaps.first().first
        val durationHours = bestGap.durationMs / (1000 * 60 * 60.0)
        TerminalLogger.log("SleepInference: Best gap = ${String.format("%.1f", durationHours)}h")

        return Pair(
            Instant.ofEpochMilli(bestGap.startMs),
            Instant.ofEpochMilli(bestGap.endMs)
        )
    }

    /**
     * Refines a user-provided manual window by looking for exact screen events.
     * Use case: User says "I slept around 2 PM to 4 PM". 
     * Analysis: Finds exact gap e.g. "2:15 PM - 3:50 PM" within that window (+/- 30 mins padding).
     */
    suspend fun refineSleepWindow(context: Context, userStart: Instant, userEnd: Instant): Pair<Instant, Instant>? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager 
            ?: return null

        // Expand search window by 30 mins to catch edge mis-estimations
        val searchStart = userStart.minusSeconds(1800).toEpochMilli()
        val searchEnd = userEnd.plusSeconds(1800).toEpochMilli()
        
        // Query events
        val events = usm.queryEvents(searchStart, searchEnd)
        val event = UsageEvents.Event()
        
        val gaps = mutableListOf<ScreenGap>()
        var lastOffTime: Long? = null
        var isScreenOn = true 

        // Analyze stream
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (isScreenOn) {
                        lastOffTime = event.timeStamp
                        isScreenOn = false
                    }
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!isScreenOn && lastOffTime != null) {
                        gaps.add(ScreenGap(lastOffTime, event.timeStamp))
                        lastOffTime = null
                    }
                    isScreenOn = true
                }
            }
        }
        if (!isScreenOn && lastOffTime != null) {
            gaps.add(ScreenGap(lastOffTime, searchEnd))
        }

        if (gaps.isEmpty()) return null

        // Find the gap that best overlaps with the user's manual input
        // Score = Overlap Duration
        val userDuration = java.time.Duration.between(userStart, userEnd).toMillis()
        val userStartMs = userStart.toEpochMilli()
        val userEndMs = userEnd.toEpochMilli()

        val bestMatch = gaps.maxByOrNull { gap ->
            val overlapStart = maxOf(gap.startMs, userStartMs)
            val overlapEnd = minOf(gap.endMs, userEndMs)
            (overlapEnd - overlapStart).coerceAtLeast(0)
        }

        return bestMatch?.let { 
             // Only suggest if it covers significant portion (e.g. > 30 mins)
             if (it.durationMs > 30 * 60 * 1000) {
                 Pair(Instant.ofEpochMilli(it.startMs), Instant.ofEpochMilli(it.endMs))
             } else null
        }
    }

    /**
     * Auto-confirm sleep when alarm is auto-dismissed (not manually interacted with).
     * Infers sleep and writes directly to Health Connect without user confirmation.
     */
    suspend fun autoConfirmSleep(context: Context) {
        val loader = SavedPreferencesLoader(context)
        if (!loader.isSmartSleepEnabled()) {
            TerminalLogger.log("AutoConfirmSleep: Smart Sleep disabled")
            return
        }
        
        val today = java.time.LocalDate.now()
        val healthManager = com.neubofy.reality.health.HealthManager(context)
        
        // Don't double-sync
        if (healthManager.isSleepSyncedToday(today)) {
            TerminalLogger.log("AutoConfirmSleep: Already synced today")
            return
        }
        
        // Force inference (skip timing guard for auto-confirm since alarm already fired)
        val bedtimeData = loader.getBedtimeData()
        val planStartMins = bedtimeData.startTimeInMins
        val planEndMins = bedtimeData.endTimeInMins
        
        val planEndTime = today.atTime(planEndMins / 60, planEndMins % 60)
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        val planStartTime = if (planStartMins > planEndMins) {
            today.minusDays(1).atTime(planStartMins / 60, planStartMins % 60)
                .atZone(java.time.ZoneId.systemDefault()).toInstant()
        } else {
            today.atTime(planStartMins / 60, planStartMins % 60)
                .atZone(java.time.ZoneId.systemDefault()).toInstant()
        }
        
        // Use planned bedtime as auto-confirm (user didn't interact, assume plan was followed)
        TerminalLogger.log("AutoConfirmSleep: Auto-confirming planned sleep ${planStartTime} to ${planEndTime}")
        
        try {
            healthManager.deleteSleepSessions(
                planStartTime.minus(java.time.Duration.ofHours(2)),
                planEndTime.plus(java.time.Duration.ofHours(2))
            )
            healthManager.writeSleepSession(planStartTime, planEndTime)
            TerminalLogger.log("AutoConfirmSleep: Successfully synced to Health Connect")
        } catch (e: Exception) {
            TerminalLogger.log("AutoConfirmSleep: Error - ${e.message}")
        }
    }

    /**
     * Passive check called when the app is opened (e.g., MainActivity onResume).
     * Waits 60 minutes after wake-up time. If no sleep is recorded in Health Connect,
     * infers sleep and sends a local notification suggesting the user to save it.
     */
    suspend fun checkAndNotifyMissingSleep(context: Context) {
        val loader = SavedPreferencesLoader(context)
        if (!loader.isSmartSleepEnabled()) return
        
        val bedtimeData = loader.getBedtimeData()
        if (!bedtimeData.isEnabled) return
        
        val prefs = context.getSharedPreferences("reality_sleep_prefs", Context.MODE_PRIVATE)
        val todayStr = java.time.LocalDate.now().toString()
        if (prefs.getString("last_notified_date", "") == todayStr) return // Already checked/notified today
        
        val nowTime = java.time.LocalTime.now()
        val nowMins = (nowTime.hour * 60) + nowTime.minute
        
        // Ensure we are at least 60 minutes past the scheduled wake-up time
        val wakeUpMins = bedtimeData.endTimeInMins
        if (nowMins < wakeUpMins + 60) {
            return // Not enough time has passed since wake-up
        }
        
        val healthManager = com.neubofy.reality.health.HealthManager(context)
        val today = java.time.LocalDate.now()
        
        if (healthManager.isSleepSyncedToday(today)) {
            // A wearable already synced it! Do nothing, just mark as checked today.
            prefs.edit().putString("last_notified_date", todayStr).apply()
            return
        }
        
        // Sleep not found in Health Connect 60+ mins after wakeup. Infer it!
        // Immediately mark as checked so we ONLY run this heavy inference once a day
        prefs.edit().putString("last_notified_date", todayStr).apply()
        
        val googleStart = prefs.getLong("google_sleep_start", 0L)
        val googleEnd = prefs.getLong("google_sleep_end", 0L)
        
        if (googleStart > 0 && googleEnd > 0) {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
            val startStr = formatter.format(Instant.ofEpochMilli(googleStart))
            val endStr = formatter.format(Instant.ofEpochMilli(googleEnd))
            
            TerminalLogger.log("SmartSleep: Google Sleep API detected sleep, sending notification")
            sendSleepNotification(context, "Reality detected sleep from $startStr to $endStr using Google Sleep API. Tap to review and save.")
            
            // Clear the Google sleep data so we don't process it again tomorrow
            prefs.edit().remove("google_sleep_start").remove("google_sleep_end").apply()
        } else {
            val inferred = inferSleepSession(context, today, force = true)
            if (inferred != null) {
                TerminalLogger.log("SmartSleep: Inferred sleep from ${inferred.first} to ${inferred.second}, sending notification")
                
                // Format time for notification
                val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
                val startStr = formatter.format(inferred.first)
                val endStr = formatter.format(inferred.second)
                
                sendSleepNotification(context, "Reality detected sleep from $startStr to $endStr. Tap to review and save.")
            }
        }
    }
    
    private fun sendSleepNotification(context: Context, message: String) {
        val channelId = "reality_sleep_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Smart Sleep", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = android.content.Intent(context, com.neubofy.reality.ui.activity.SmartSleepActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.neubofy.reality.R.drawable.ic_launcher_foreground) // Replace with actual icon if available
            .setContentTitle("Add Sleep Session")
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        notificationManager.notify(1001, builder.build())
    }
}
