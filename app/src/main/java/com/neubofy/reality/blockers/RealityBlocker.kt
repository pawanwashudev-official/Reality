package com.neubofy.reality.blockers

import android.os.SystemClock
import android.util.Log
import com.neubofy.reality.Constants
import com.neubofy.reality.utils.TimeTools
import java.util.Calendar

class RealityBlocker {

    data class FocusModeData(
        var isTurnedOn: Boolean = false,
        var endTime: Long = -1,
        var selectedApps: HashSet<String> = hashSetOf(""),
        var modeType: Int = Constants.FOCUS_MODE_BLOCK_SELECTED,
        var blockedWebsites: HashSet<String> = hashSetOf()
    )

    var focusModeData = FocusModeData()
    var bedtimeData = Constants.BedtimeData()
    var emergencyData = Constants.EmergencyModeData()
    var usageLimitData = Constants.UsageLimitData()
    private var schedules: MutableList<Constants.AutoTimedActionItem> = mutableListOf()
    // REMOVED: Cooldown bypass logic removed as per user request
    private var calendarEvents: List<Pair<Long, Long>> = emptyList()
    var appGroups: List<com.neubofy.reality.data.db.AppGroupEntity> = emptyList()
    var appLimits: List<com.neubofy.reality.data.db.AppLimitEntity> = emptyList()
    var whitelistedPackages = hashSetOf<String>()
    var strictModeData = Constants.StrictModeData()

    // Optimized Data Structures (O(1) Access)
    private var appLimitsMap: Map<String, com.neubofy.reality.data.db.AppLimitEntity> = emptyMap()
    private var packageToGroupsMap: Map<String, List<com.neubofy.reality.data.db.AppGroupEntity>> = emptyMap()

    fun refreshAppGroups(groups: List<com.neubofy.reality.data.db.AppGroupEntity>) {
        appGroups = groups
        // Pre-process groups into a Reverse Index Map (Package -> List of Groups it belongs to)
        // This eliminates string splitting and iteration during the critical blocking check.
        val newMap = mutableMapOf<String, MutableList<com.neubofy.reality.data.db.AppGroupEntity>>()
        for (group in groups) {
            val packages = parsePackages(group.packageNamesJson)
            for (pkg in packages) {
                newMap.getOrPut(pkg) { mutableListOf() }.add(group)
            }
        }
        packageToGroupsMap = newMap
    }
    
    fun refreshAppLimits(limits: List<com.neubofy.reality.data.db.AppLimitEntity>) {
        appLimits = limits
        // Pre-index limits by package name for O(1) lookup
        appLimitsMap = limits.associateBy { it.packageName }
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

    fun doesAppNeedToBeBlocked(packageName: String): BlockerResult {
        // === THE BOX APPROACH: Single Source of Truth ===
        // BlockCache handles: Emergency Mode, Maintenance Window, and all blocking decisions.
        // This function only does quick whitelist checks.
        
        // 1. Universal Whitelist (Launcher, Keyboard, Self, Settings)
        if (whitelistedPackages.contains(packageName)) {
            return BlockerResult(isBlocked = false, isEmergency = false)
        }
        
        // 2. Expanded System Whitelist (Safety Net)
        val expandedWhitelist = setOf(
            "com.android.calculator2", "com.google.android.calculator",
            "com.android.dialer", "com.google.android.dialer",
            "com.android.contacts", "com.google.android.contacts",
            "com.android.deskclock", "com.google.android.deskclock",
            "com.android.systemui", 
            "com.google.android.packageinstaller", "com.android.packageinstaller"
        )
        if (expandedWhitelist.contains(packageName)) {
            return BlockerResult(isBlocked = false, isEmergency = false)
        }
        
        // 3. THE BOX - Single Source of Truth
        // BlockCache already handles: Emergency Mode, Maintenance Window
        // If it's in the box = blocked. If not = not blocked. Period.
        val (isCachedBlocked, cachedReasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(packageName)
        
        if (isCachedBlocked) {
            val reason = cachedReasons.firstOrNull() ?: "Blocked"
            // Get end time based on reason type
            val endTime = getEndTimeForReason(reason)
            return BlockerResult(isBlocked = true, endTime = endTime, reason = reason)
        }
        
        // === NOT BLOCKED ===
        return BlockerResult(isBlocked = false)
    }
    
    // Helper: Get end time based on blocking reason
    private fun getEndTimeForReason(reason: String): Long {
        return when {
            reason.contains("Focus Mode") -> focusModeData.endTime
            reason.contains("Bedtime") -> getBedtimeEndTime()
            reason.contains("Schedule") || reason.contains("Event") -> {
                // Find next calendar/schedule end time
                val now = System.currentTimeMillis()
                calendarEvents.filter { now in it.first..it.second }
                    .minOfOrNull { it.second } ?: getMidnightTimestamp()
            }
            reason.contains("limit") -> getMidnightTimestamp()
            else -> getMidnightTimestamp()
        }
    }
    
    private fun getMidnightTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun isBedtime(): Boolean {
        val cal = Calendar.getInstance()
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = bedtimeData.startTimeInMins
        val end = bedtimeData.endTimeInMins
        
        return if (start < end) {
            currentMins in start until end
        } else {
            // Bedtime spans midnight
            currentMins >= start || currentMins < end
        }
    }
    
    private fun getBedtimeEndTime(): Long {
        val cal = Calendar.getInstance()
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val end = bedtimeData.endTimeInMins
        
        val hoursUntilEnd = end / 60
        val minsUntilEnd = end % 60
        
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hoursUntilEnd)
            set(Calendar.MINUTE, minsUntilEnd)
            set(Calendar.SECOND, 0)
            
            // If end time is earlier in the day, it's tomorrow
            if (end < currentMins && bedtimeData.startTimeInMins > bedtimeData.endTimeInMins) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        return endCal.timeInMillis
    }

    private fun shouldBlockPackage(packageName: String): Boolean {
        return if (focusModeData.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
            !focusModeData.selectedApps.contains(packageName)
        } else {
            focusModeData.selectedApps.contains(packageName)
        }
    }

    private fun getScheduleEndTime(packageName: String): Long? {
        val currentTime = Calendar.getInstance()
        val currentMinutes = TimeTools.convertToMinutesFromMidnight(
            currentTime.get(Calendar.HOUR_OF_DAY),
            currentTime.get(Calendar.MINUTE)
        )
        val uptimeNow = SystemClock.uptimeMillis()

        schedules.forEach { item ->
            // Check if schedule is active
            val start = item.startTimeInMins
            val end = item.endTimeInMins
            
            val isActive = (start <= end && currentMinutes in start until end) ||
                          (start > end && (currentMinutes >= start || currentMinutes < end))
            
            if (isActive) {
                // UNIVERSAL BLOCKLIST ENFORCED: Ignore item.packages, use Universal List
                if (shouldBlockPackage(packageName)) {
                    var dayOffset = 0
                    if (start > end && currentMinutes > end) dayOffset = 1440
                    val diff = end + dayOffset - currentMinutes
                    return uptimeNow + (diff * 60 * 1000)
                }
            }
        }
        return null
    }

    // REMOVED: putCooldown function removed - no more Proceed Anyway bypass

    fun refreshSchedules(newList: List<Constants.AutoTimedActionItem>) {
        schedules.clear()
        schedules.addAll(newList)
    }

    fun refreshCalendarEvents(events: List<Pair<Long, Long>>) {
        calendarEvents = events
    }
    
    fun hasActiveSchedules(): Boolean = schedules.isNotEmpty()
    fun hasActiveCalendarEvents(): Boolean = calendarEvents.isNotEmpty()
    
    fun isAnyScheduleActive(): Boolean {
        val currentTime = Calendar.getInstance()
        val currentMinutes = TimeTools.convertToMinutesFromMidnight(
            currentTime.get(Calendar.HOUR_OF_DAY),
            currentTime.get(Calendar.MINUTE)
        )
        schedules.forEach { item ->
            val start = item.startTimeInMins
            val end = item.endTimeInMins
            val isActive = (start <= end && currentMinutes in start until end) ||
                          (start > end && (currentMinutes >= start || currentMinutes < end))
            if (isActive) return true
        }
        
        // Also check Calendar Events
        val nowMs = System.currentTimeMillis()
        calendarEvents.forEach { (start, end) ->
            if (nowMs in start..end) return true
        }
        
        return false
    }

    fun hasConfiguredLimits(): Boolean = appLimits.isNotEmpty() || appGroups.isNotEmpty()
    
    private fun getMidnightBlockResult(reason: String): BlockerResult {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        return BlockerResult(isBlocked = true, endTime = cal.timeInMillis, reason = reason)
    }

    private fun isActivePeriod(json: String): Boolean {
        if (json.length < 5) return true 
        try {
            val arr = org.json.JSONArray(json)
            if (arr.length() == 0) return true
            
            val now = Calendar.getInstance()
            val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val startStr = obj.getString("start")
                val endStr = obj.getString("end")
                val startMins = parseTime(startStr)
                val endMins = parseTime(endStr)
                
                val active = if (startMins <= endMins) {
                    currentMins in startMins..endMins
                } else {
                    currentMins >= startMins || currentMins <= endMins
                }
                if (active) return true
            }
            return false
        } catch (e: Exception) {
            return true
        }
    }
    
    private fun parseTime(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) { 0 }
    }
    
    fun isAnyStrictLimitActive(): Boolean {
         // Check Global Strict Mode
         if (strictModeData.isEnabled) return true

         // Check Groups - only if they have specific periods AND are currently active
         appGroups.forEach {
             if (it.isStrict) {
                 // Only count as strict if it has specific periods AND is currently active
                 val hasSpecificPeriods = try {
                     val arr = org.json.JSONArray(it.activePeriodsJson)
                     arr.length() > 0
                 } catch (e: Exception) { false }
                 
                 if (hasSpecificPeriods && isActivePeriod(it.activePeriodsJson)) return true
             }
         }
         
         // Check Single Limits - only if they have specific periods AND are currently active
         appLimits.forEach {
             if (it.isStrict) {
                 val hasSpecificPeriods = try {
                     val arr = org.json.JSONArray(it.activePeriodsJson)
                     arr.length() > 0
                 } catch (e: Exception) { false }
                 
                 if (hasSpecificPeriods && isActivePeriod(it.activePeriodsJson)) return true
             }
         }
         
         return false
    }

    fun getBlockReason(packageName: String): String? {
        // 1. Universal Whitelist Check
        if (whitelistedPackages.contains(packageName)) return null
        val expandedWhitelist = setOf(
            "com.android.calculator2", "com.google.android.calculator",
            "com.android.dialer", "com.google.android.dialer",
            "com.android.contacts", "com.google.android.contacts",
            "com.android.deskclock", "com.google.android.deskclock",
            "com.android.systemui", 
            "com.google.android.packageinstaller", "com.android.packageinstaller"
        )
        if (expandedWhitelist.contains(packageName)) return null
        if (com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()) return null
        if (emergencyData.currentSessionEndTime > System.currentTimeMillis()) return null
        
        // 2. Blocking Checks
        
        // Focus Mode
        if (focusModeData.isTurnedOn) {
            if (shouldBlockPackage(packageName)) return "Focus Session"
        }
        
        // Bedtime Mode
        if (bedtimeData.isEnabled && isBedtime()) {
            if (shouldBlockPackage(packageName)) return "Bedtime Mode"
        }
        
        // Calendar Events
        val currentTime = System.currentTimeMillis()
        calendarEvents.forEach { (start, end) ->
            if (currentTime in start..end) {
                if (shouldBlockPackage(packageName)) return "Scheduled Event"
            }
        }
        
        // Scheduled Blocks
        val scheduleEndTime = getScheduleEndTime(packageName)
        if (scheduleEndTime != null) return "Scheduled Block"
        
        // App Limits / Group Limits ( Simplified check for UI)
        val appLimit = appLimitsMap[packageName]
        if (appLimit != null && appLimit.limitInMinutes > 0) {
             if (!isActivePeriod(appLimit.activePeriodsJson)) return "Outside Active Session"
             // Usage limit check requires real-time usage data which might vary slightly from service, 
             // but we can check if it's generally exhausted.
             val limitMs = appLimit.limitInMinutes * 60 * 1000L
             val used = usageLimitData.appUsages.getOrDefault(packageName, 0L)
             if (used >= limitMs) return "Daily Limit Reached"
        }
        
        // Group Limits
        val relevantGroups = packageToGroupsMap[packageName]
        if (relevantGroups != null) {
            for (group in relevantGroups) {
                if (group.limitInMinutes > 0) {
                    if (!isActivePeriod(group.activePeriodsJson)) return "Outside Group Session"
                    // Usage check omitted for UI speed, assumed 'Active Session' covers most cases
                }
            }
        }

        return null
    }

    data class BlockerResult(
        val isBlocked: Boolean,
        val endTime: Long = -1L,
        val isRequestingUpdate: Boolean = false,
        val isEmergency: Boolean = false,
        val reason: String = ""
    )
}
