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
        // 1. Universal Whitelist (Launcher, Keyboard, Self, Settings)
        if (whitelistedPackages.contains(packageName)) {
            return BlockerResult(isBlocked = false, isEmergency = true)
        }
    
        // 2. Check Maintenance Window (00:00 - 00:10) - UNIVERSAL UNBLOCK
        if (com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()) {
            return BlockerResult(isBlocked = false, isEmergency = true)
        }

        // 3. Check Emergency Mode (Temporary Unblock)
        // Global Bypass: If Emergency Mode is active, NO app is blocked.
        if (emergencyData.currentSessionEndTime > System.currentTimeMillis()) {
            return BlockerResult(isBlocked = false, endTime = emergencyData.currentSessionEndTime, isEmergency = true)
        }
        
        // NOTE: Cooldown (Proceed Anyway) logic REMOVED - apps will always be blocked when in blocking mode


        // === BLOCKING MODES (Only block if one of these is active) ===

        // 5. Check Manual Focus Mode
        if (focusModeData.isTurnedOn) {
            if (focusModeData.endTime < System.currentTimeMillis()) {
                focusModeData.isTurnedOn = false
                return BlockerResult(isBlocked = false, isRequestingUpdate = true)
            }
            if (shouldBlockPackage(packageName)) {
                return BlockerResult(isBlocked = true, endTime = focusModeData.endTime, reason = "Focus Mode")
            }
        }

        // 6. Check Bedtime Mode (Uses UNIVERSAL blocklist - same as Focus Mode)
        if (bedtimeData.isEnabled && isBedtime()) {
            if (shouldBlockPackage(packageName)) {
                val bedtimeEnd = getBedtimeEndTime()
                return BlockerResult(isBlocked = true, endTime = bedtimeEnd, reason = "Bedtime Mode")
            }
        }

        // 7. Check Calendar Events (Auto-Focus from Calendar)
        val currentTime = System.currentTimeMillis()
        calendarEvents.forEach { (start, end) ->
            if (currentTime in start..end) {
                if (shouldBlockPackage(packageName)) {
                    return BlockerResult(isBlocked = true, endTime = end, reason = "Scheduled Event")
                }
            }
        }

        // 8. Check Manual Scheduled Focus (Auto-Focus)
        val scheduleEndTime = getScheduleEndTime(packageName)
        if (scheduleEndTime != null) {
            return BlockerResult(isBlocked = true, endTime = scheduleEndTime, reason = "Scheduled Block")
        }
        
        // 9. Check App Limits (O(1) Lookup)
        val appLimit = appLimitsMap[packageName]
        if (appLimit != null && appLimit.limitInMinutes > 0) {
            
            // Rule A: Strict Active Session (Standalone Rule)
            if (!isActivePeriod(appLimit.activePeriodsJson)) {
                return BlockerResult(isBlocked = true, reason = "Outside Active Session")
            }

            // Rule B: Usage Limit (Standalone Rule)
            val limitMs = appLimit.limitInMinutes * 60 * 1000L
            val used = usageLimitData.appUsages.getOrDefault(packageName, 0L)
            if (used >= limitMs) {
                return getMidnightBlockResult("Daily limit reached")
            }
        }
        
        // 10. Check App Group Limits (O(1) Linked Lookup)
        val relevantGroups = packageToGroupsMap[packageName]
        if (relevantGroups != null) {
            for (group in relevantGroups) {
                if (group.limitInMinutes <= 0) continue
                
                // Rule A: Group Active Session (Standalone Rule)
                if (!isActivePeriod(group.activePeriodsJson)) {
                    return BlockerResult(isBlocked = true, reason = "Outside Group Session")
                }

                // Rule B: Group Usage Limit (Standalone Rule)
                // We still need to sum usage here, but only for groups strictly containing this app
                val packages = parsePackages(group.packageNamesJson) // Cached string split could be optimized further but list size is usually small
                var totalGroupUsage = 0L
                for (pkg in packages) {
                    totalGroupUsage += usageLimitData.appUsages.getOrDefault(pkg, 0L)
                }
                
                val limitMs = group.limitInMinutes * 60 * 1000L
                if (totalGroupUsage >= limitMs) {
                    return getMidnightBlockResult("Group limit reached")
                }
            }
        }

        // === DEFAULT: NO BLOCKING (Blocker is "Dead" in normal time) ===
        return BlockerResult(isBlocked = false)
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
                // If packages list is empty, use default blocklist
                val shouldBlock = if (item.packages.isEmpty()) {
                    shouldBlockPackage(packageName)
                } else {
                    item.packages.contains(packageName)
                }
                
                if (shouldBlock) {
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

    data class BlockerResult(
        val isBlocked: Boolean,
        val endTime: Long = -1L,
        val isRequestingUpdate: Boolean = false,
        val isEmergency: Boolean = false,
        val reason: String = ""
    )
}
