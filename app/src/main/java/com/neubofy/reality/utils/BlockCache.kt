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
 * - Every 15 minutes (by HeartbeatWorker - unified with SettingsBox)
 * - On screen unlock (event-driven)
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
     * 
     * ATOMIC SWAP IMPLEMENTATION:
     * This variable is replaced atomically. Readers will either see the OLD box or the NEW box.
     * They will NEVER see an empty box during a rebuild.
     */
    @Volatile private var blockedApps: Map<String, Set<String>> = emptyMap()
    
    /** Last time the cache was updated */
    var lastUpdateTime = 0L
        private set
    
    /** Emergency Session End Time - Checked BEFORE the box */
    @Volatile var emergencySessionEndTime: Long = 0L
    
    /** Is any blocking mode currently active */
    @Volatile var isAnyBlockingModeActive = false
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
        val currentBox = blockedApps // Capture reference for consistency
        val reasons = currentBox[packageName]
        return if (reasons != null && reasons.isNotEmpty()) {
            Pair(true, reasons.toList())
        } else {
            Pair(false, emptyList())
        }
    }
    
    /**
     * Get all currently blocked apps with reasons.
     */
    fun getAllBlockedApps(): Map<String, Set<String>> = blockedApps
    
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
                val prefs = SavedPreferencesLoader(context)
                // Get DB reference OUTSIDE the mutex to avoid holding the lock during DB initialization
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)
                val now = System.currentTimeMillis()
                
                // CRITICAL FIX: Reload emergency status immediately
                val emergencyData = prefs.getEmergencyData()
                emergencySessionEndTime = if (emergencyData.currentSessionEndTime > now && emergencyData.usesRemaining > 0) {
                    emergencyData.currentSessionEndTime
                } else {
                    0L
                }
                
                mutex.withLock {
                    try {
                        // ATOMIC SWAP: Create a completely new box. 
                        // The old 'blockedApps' is still active and protecting the phone while we build this.
                        val newBox = mutableMapOf<String, MutableSet<String>>()
                        
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
                
                // AUTO-CLEANUP: If Focus Mode expired, reset the flag
                if (focusData.isTurnedOn && focusData.endTime <= now) {
                    focusData.isTurnedOn = false
                    prefs.saveFocusModeData(focusData)
                    TerminalLogger.log("CLEANUP: Expired Focus Mode auto-cleared")
                }
                
                val isBedtimeActive = bedtimeData.isEnabled && isBedtimeNow(bedtimeData, currentMins)
                val isScheduleActive = isAnyScheduleActive(schedules, currentMins, currentDay)
                val isCalendarEventActive = calendarEvents.isNotEmpty()
                
                isAnyBlockingModeActive = isFocusActive || isBedtimeActive || 
                                          isScheduleActive || isCalendarEventActive
                
                // === STEP 2: Add apps from blocklist if ANY mode is active ===
                // Now with per-app mode filtering
                if (isAnyBlockingModeActive) {
                    val blocklist = focusData.selectedApps
                    val modeType = focusData.modeType
                    
                    // Load per-app mode configurations
                    val appConfigs = prefs.loadBlockedAppConfigs()
                    val configMap = appConfigs.associateBy { it.packageName }
                    
                    // Determine active mode for filtering
                    val activeMode = when {
                        isFocusActive -> "FOCUS"
                        isBedtimeActive -> "BEDTIME"
                        isScheduleActive -> "AUTO_FOCUS"
                        isCalendarEventActive -> "CALENDAR"
                        else -> "NONE"
                    }
                    
                    // Determine reason
                    val reason = when (activeMode) {
                        "FOCUS" -> "Focus Mode"
                        "BEDTIME" -> "Bedtime Mode"
                        "AUTO_FOCUS" -> "Scheduled Block"
                        "CALENDAR" -> "Calendar Event"
                        else -> "Blocked"
                    }
                    
                    if (modeType == com.neubofy.reality.Constants.FOCUS_MODE_BLOCK_SELECTED) {
                        // Block selected apps (with per-app mode filtering)
                        blocklist.forEach { pkg ->
                            if (pkg.isNotEmpty()) {
                                // Check if this app should be blocked for the current mode
                                val config = configMap[pkg] ?: com.neubofy.reality.Constants.BlockedAppConfig(pkg)
                                val shouldBlock = when (activeMode) {
                                    "FOCUS" -> config.blockInFocus
                                    "BEDTIME" -> config.blockInBedtime
                                    "AUTO_FOCUS" -> config.blockInAutoFocus
                                    "CALENDAR" -> config.blockInCalendar
                                    else -> true
                                }
                                
                                if (shouldBlock) {
                                    addToBox(newBox, pkg, reason)
                                }
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
                        addToBox(newBox, limit.packageName, "Daily Limit Reached (${limit.limitInMinutes}m)")
                    }
                    
                    // Check if outside active hours
                    if (limit.activePeriodsJson.isNotEmpty() && limit.activePeriodsJson.length > 5) {
                        if (!isWithinActivePeriod(limit.activePeriodsJson, currentMins)) {
                            addToBox(newBox, limit.packageName, "Outside Active Hours")
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
                        packages.forEach { pkg -> addToBox(newBox, pkg, reason) }
                    }
                    
                    // Check if outside group active hours
                    if (group.activePeriodsJson.isNotEmpty() && group.activePeriodsJson.length > 5) {
                        if (!isWithinActivePeriod(group.activePeriodsJson, currentMins)) {
                            val reason = "Outside Group Hours (${group.name})"
                            packages.forEach { pkg -> addToBox(newBox, pkg, reason) }
                        }
                    }
                }
                
                lastUpdateTime = System.currentTimeMillis()
                
                // === ATOMIC SWAP ===
                // We replace the old box with the new one instantly.
                blockedApps = newBox
                
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
            
            // blockedApps is thread-safe to read
            for ((pkg, reasons) in blockedApps) {
                json.put(pkg, org.json.JSONArray(reasons.toList()))
            }
            
            prefs.edit()
                .putString("blocked_apps", json.toString())
                .putLong("last_update", lastUpdateTime)
                .putBoolean("is_active", isAnyBlockingModeActive)
                .putLong("emergency_end_time", emergencySessionEndTime)
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
            
            // Use temporary container for atomic swap compatibility
            val newBox = mutableMapOf<String, MutableSet<String>>()
            
            for (key in json.keys()) {
                val reasonsArr = json.getJSONArray(key)
                val reasonsSet = mutableSetOf<String>()
                for (i in 0 until reasonsArr.length()) {
                    reasonsSet.add(reasonsArr.getString(i))
                }
                newBox[key] = reasonsSet
            }
            
            // Atomic update
            blockedApps = newBox
            
            lastUpdateTime = savedTime
            isAnyBlockingModeActive = prefs.getBoolean("is_active", false)
            emergencySessionEndTime = prefs.getLong("emergency_end_time", 0L)
            TerminalLogger.log("BOX LOADED FROM DISK: ${blockedApps.size} apps, emergency=${emergencySessionEndTime > System.currentTimeMillis()}")
        } catch (e: Exception) {
            TerminalLogger.log("DISK LOAD ERROR: ${e.message}")
        }
    }
    
    /**
     * Add an app to the box with a reason.
     * Takes the target map as an argument for thread confinement.
     */
    private fun addToBox(box: MutableMap<String, MutableSet<String>>, packageName: String, reason: String) {
        box.getOrPut(packageName) { mutableSetOf() }.add(reason)
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
