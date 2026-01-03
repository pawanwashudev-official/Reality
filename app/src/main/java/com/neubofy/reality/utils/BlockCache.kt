package com.neubofy.reality.utils

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BlockCache - THE SINGLE SOURCE OF TRUTH for blocking decisions.
 * 
 * This is THE BOX. If an app is in this box, it should be blocked. Period.
 * No fallback logic. No real-time calculations. Just check the box.
 * 
 * Box is refreshed:
 * - Every 3 minutes (by BlockCacheWorker)
 * - When Focus Mode starts/stops
 * - When any Schedule starts/stops
 * - When Blocklist changes
 * - When usage limit is crossed
 */
object BlockCache {
    
    private val mutex = Mutex()
    
    /**
     * THE BOX - Map of packageName to list of reasons why it should be blocked.
     * If a package is in this map, it MUST be blocked.
     * Multiple reasons can apply to the same package.
     */
    private val blockedApps = mutableMapOf<String, MutableSet<String>>()
    
    /** Last time the cache was updated */
    var lastUpdateTime = 0L
        private set
    
    /** Emergency Session End Time - Checked BEFORE the box */
    @Volatile var emergencySessionEndTime: Long = 0L
    
    /** Is any blocking mode currently active */
    var isAnyBlockingModeActive = false
        private set
    
    // === INSTANT CHECK (O(1)) ===
    
    /**
     * Check if app should be blocked. O(1) time.
     * This is the ONLY function that should be called to decide blocking.
     * 
     * @return Pair of (shouldBlock, reasons) or (false, emptyList)
     */
    fun shouldBlock(packageName: String): Pair<Boolean, List<String>> {
        // 1. Emergency Mode Override - Always allow during emergency
        if (emergencySessionEndTime > System.currentTimeMillis()) {
            return Pair(false, emptyList())
        }
        
        // 2. Maintenance Window (00:00 - 00:10) - Always allow
        if (StrictLockUtils.isMaintenanceWindow()) {
            return Pair(false, emptyList())
        }
        
        // 3. Check THE BOX
        val reasons = blockedApps[packageName]
        return if (reasons != null && reasons.isNotEmpty()) {
            Pair(true, reasons.toList())
        } else {
            Pair(false, emptyList())
        }
    }
    
    /**
     * Get all currently blocked apps with reasons.
     */
    fun getAllBlockedApps(): Map<String, Set<String>> = blockedApps.toMap()
    
    /**
     * Get count of blocked apps.
     */
    fun getBlockedCount(): Int = blockedApps.size
    
    // === BOX UPDATE (Called by Worker and Triggers) ===
    
    /**
     * Rebuild THE BOX completely.
     * Called every 3 minutes by worker, and immediately on any trigger.
     */
    suspend fun rebuildBox(context: Context) {
        mutex.withLock {
            try {
                blockedApps.clear()
                
                val prefs = SavedPreferencesLoader(context)
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)
                val now = System.currentTimeMillis()
                val calendar = java.util.Calendar.getInstance()
                val currentMins = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + 
                                  calendar.get(java.util.Calendar.MINUTE)
                val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                
                // === STEP 1: Check if any blocking mode is active ===
                val focusData = prefs.getFocusModeData()
                val bedtimeData = prefs.getBedtimeData()
                val schedules = prefs.loadAutoFocusHoursList()
                val calendarEvents = db.calendarEventDao().getCurrentEvents(now)
                
                val isFocusActive = focusData.isTurnedOn && focusData.endTime > now
                val isBedtimeActive = bedtimeData.isEnabled && isBedtimeNow(bedtimeData, currentMins)
                val isScheduleActive = isAnyScheduleActive(schedules, currentMins, currentDay)
                val isCalendarEventActive = calendarEvents.isNotEmpty()
                
                isAnyBlockingModeActive = isFocusActive || isBedtimeActive || 
                                          isScheduleActive || isCalendarEventActive
                
                // === STEP 2: Add apps from blocklist if ANY mode is active ===
                if (isAnyBlockingModeActive) {
                    val blocklist = focusData.selectedApps
                    val modeType = focusData.modeType
                    
                    // Determine reason
                    val reason = when {
                        isFocusActive -> "Focus Mode"
                        isBedtimeActive -> "Bedtime Mode"
                        isScheduleActive -> "Scheduled Block"
                        isCalendarEventActive -> "Calendar Event"
                        else -> "Blocked"
                    }
                    
                    if (modeType == com.neubofy.reality.Constants.FOCUS_MODE_BLOCK_SELECTED) {
                        // Block selected apps
                        blocklist.forEach { pkg ->
                            if (pkg.isNotEmpty()) {
                                addToBox(pkg, reason)
                            }
                        }
                    } else {
                        // Block ALL except selected - handled differently
                        // For this mode, we'd need to get all installed apps
                        // For now, we'll handle this in the check logic
                    }
                }
                
                // === STEP 3: Add apps that exceeded usage limits ===
                val usageMap = UsageUtils.getUsageSinceMidnight(context)
                val appLimits = db.appLimitDao().getAllLimits()
                
                for (limit in appLimits) {
                    val usedMs = usageMap[limit.packageName] ?: 0L
                    val limitMs = limit.limitInMinutes * 60 * 1000L
                    
                    // Check if limit exceeded
                    if (limitMs > 0 && usedMs >= limitMs) {
                        addToBox(limit.packageName, "Daily Limit Reached (${limit.limitInMinutes}m)")
                    }
                    
                    // Check if outside active hours
                    if (limit.activePeriodsJson.isNotEmpty() && limit.activePeriodsJson.length > 5) {
                        if (!isWithinActivePeriod(limit.activePeriodsJson, currentMins)) {
                            addToBox(limit.packageName, "Outside Active Hours")
                        }
                    }
                }
                
                // === STEP 4: Add apps from groups that exceeded limits ===
                val appGroups = db.appGroupDao().getAllGroups()
                
                for (group in appGroups) {
                    val packages = parsePackages(group.packageNamesJson)
                    val limitMs = group.limitInMinutes * 60 * 1000L
                    
                    // Calculate total group usage
                    var totalGroupUsage = 0L
                    for (pkg in packages) {
                        totalGroupUsage += usageMap[pkg] ?: 0L
                    }
                    
                    // Check if group limit exceeded
                    if (limitMs > 0 && totalGroupUsage >= limitMs) {
                        val reason = "Group Limit Reached (${group.name})"
                        packages.forEach { pkg -> addToBox(pkg, reason) }
                    }
                    
                    // Check if outside group active hours
                    if (group.activePeriodsJson.isNotEmpty() && group.activePeriodsJson.length > 5) {
                        if (!isWithinActivePeriod(group.activePeriodsJson, currentMins)) {
                            val reason = "Outside Group Hours (${group.name})"
                            packages.forEach { pkg -> addToBox(pkg, reason) }
                        }
                    }
                }
                
                lastUpdateTime = System.currentTimeMillis()
                TerminalLogger.log("BOX REBUILT: ${blockedApps.size} apps blocked")
                
                // === PERSIST TO DISK (Survives RAM cleanup) ===
                saveToDisk(context)
                
            } catch (e: Exception) {
                TerminalLogger.log("BOX ERROR: ${e.message}")
            }
        }
    }
    
    /**
     * Save the box to disk for persistence across RAM cleanups.
     */
    private fun saveToDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences("block_cache", Context.MODE_PRIVATE)
            val json = org.json.JSONObject()
            
            for ((pkg, reasons) in blockedApps) {
                json.put(pkg, org.json.JSONArray(reasons.toList()))
            }
            
            prefs.edit()
                .putString("blocked_apps", json.toString())
                .putLong("last_update", lastUpdateTime)
                .putBoolean("is_active", isAnyBlockingModeActive)
                .apply()
        } catch (e: Exception) {
            TerminalLogger.log("DISK SAVE ERROR: ${e.message}")
        }
    }
    
    /**
     * Load the box from disk on startup or after RAM cleanup.
     */
    fun loadFromDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences("block_cache", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("blocked_apps", null) ?: return
            val savedTime = prefs.getLong("last_update", 0L)
            
            // Only use disk data if it's less than 5 minutes old
            if (System.currentTimeMillis() - savedTime > 5 * 60 * 1000) {
                return // Data is stale, will be rebuilt by worker
            }
            
            val json = org.json.JSONObject(jsonStr)
            blockedApps.clear()
            
            for (key in json.keys()) {
                val reasonsArr = json.getJSONArray(key)
                val reasonsSet = mutableSetOf<String>()
                for (i in 0 until reasonsArr.length()) {
                    reasonsSet.add(reasonsArr.getString(i))
                }
                blockedApps[key] = reasonsSet
            }
            
            lastUpdateTime = savedTime
            isAnyBlockingModeActive = prefs.getBoolean("is_active", false)
            TerminalLogger.log("BOX LOADED FROM DISK: ${blockedApps.size} apps")
        } catch (e: Exception) {
            TerminalLogger.log("DISK LOAD ERROR: ${e.message}")
        }
    }
    
    /**
     * Add an app to the box with a reason.
     * If app already exists, adds the new reason.
     */
    private fun addToBox(packageName: String, reason: String) {
        blockedApps.getOrPut(packageName) { mutableSetOf() }.add(reason)
    }
    
    /**
     * Force invalidate - next check will trigger rebuild.
     */
    fun invalidate() {
        lastUpdateTime = 0L
    }
    
    // === HELPER FUNCTIONS ===
    
    private fun isBedtimeNow(bedtime: com.neubofy.reality.Constants.BedtimeData, currentMins: Int): Boolean {
        val start = bedtime.startTimeInMins
        val end = bedtime.endTimeInMins
        
        return if (start < end) {
            currentMins in start until end
        } else {
            // Spans midnight
            currentMins >= start || currentMins < end
        }
    }
    
    private fun isAnyScheduleActive(
        schedules: List<com.neubofy.reality.Constants.AutoTimedActionItem>,
        currentMins: Int,
        currentDay: Int
    ): Boolean {
        for (item in schedules) {
            val runsToday = item.repeatDays.isEmpty() || item.repeatDays.contains(currentDay)
            if (!runsToday) continue
            
            val start = item.startTimeInMins
            val end = item.endTimeInMins
            
            val isActive = if (start <= end) {
                currentMins in start until end
            } else {
                currentMins >= start || currentMins < end
            }
            
            if (isActive) return true
        }
        return false
    }
    
    private fun isWithinActivePeriod(json: String, currentMins: Int): Boolean {
        if (json.length < 5) return true
        try {
            val arr = org.json.JSONArray(json)
            if (arr.length() == 0) return true
            
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val startStr = obj.getString("start")
                val endStr = obj.getString("end")
                val startMins = parseTime(startStr)
                val endMins = parseTime(endStr)
                
                val isInPeriod = if (startMins <= endMins) {
                    currentMins in startMins..endMins
                } else {
                    currentMins >= startMins || currentMins <= endMins
                }
                
                if (isInPeriod) return true
            }
            return false
        } catch (e: Exception) {
            return true
        }
    }
    
    private fun parseTime(time: String): Int {
        val parts = time.split(":")
        return if (parts.size == 2) {
            parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
        } else 0
    }
    
    private fun parsePackages(json: String): List<String> {
        if (json.isEmpty()) return emptyList()
        try {
            if (json.trim().startsWith("[")) {
                val list = mutableListOf<String>()
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                return list
            }
        } catch (e: Exception) {}
        return json.split(",")
    }
}
