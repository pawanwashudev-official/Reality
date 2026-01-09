package com.neubofy.reality.utils

import android.content.Context
import java.util.Calendar
import com.neubofy.reality.Constants

object StrictLockUtils {

    /**
     * Checks if we are currently in the Maintenance Window (00:00 - 00:10).
     * During this time, EVERYTHING is unlocked (Usage and Settings).
     */
    fun isMaintenanceWindow(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour == 0 && minute < 10
    }

    /**
     * Checks if "Strict Mode" is considered active.
     * Strict Mode is active if either "Timed Lock" or "Password Lock" is enabled in Anti-Uninstall settings.
     */
    fun isStrictModeActive(context: Context): Boolean {
        // Check new Strict Mode Data
        val loader = SavedPreferencesLoader(context)
        val data = loader.getStrictModeData()
        
        if (!data.isEnabled) return false
        
        // MODE_NONE means instant deactivation is allowed, but protection is STILL ACTIVE
        if (data.modeType == Constants.StrictModeData.MODE_NONE) {
            return true // <-- FIX: No-lock mode is still active!
        }
        
        if (data.modeType == Constants.StrictModeData.MODE_TIMER) {
             // Active if time hasn't passed
             return System.currentTimeMillis() < data.timerEndTime
        }
        
        if (data.modeType == Constants.StrictModeData.MODE_PASSWORD) {
             // Active always if enabled
             return true
        }
        
        return false
    }

    /**
     * Determines if the user is allowed to modify settings (change limits, uncheck apps, etc.).
     * Allowed ONLY if:
     * 1. Strict Mode is OFF
     * OR
     * 2. It is strictly the Maintenance Window (00:00 - 00:10)
     * 
     * Note: Emergency Mode does NOT allow modification.
     */
    fun isModificationAllowed(context: Context): Boolean {
        if (isMaintenanceWindow()) return true
        return !isStrictModeActive(context)
    }
    
    enum class FeatureType {
        BLOCKLIST, BEDTIME, APP_LIMIT, GROUP_LIMIT, SCHEDULE
    }
    
    /**
     * Granular modification check.
     * Checks if the user can modify a specific feature based on:
     * 1. Maintenance window (always allows)
     * 2. Global Strict Mode enabled AND the feature's specific lock flag
     */
    fun isModificationAllowedFor(context: Context, feature: FeatureType): Boolean {
        if (isMaintenanceWindow()) return true
        
        val loader = SavedPreferencesLoader(context)
        val strictData = loader.getStrictModeData()
        
        if (!strictData.isEnabled) return true // Strict mode is off
        
        // Check if strict mode is active (timer/password)
        if (!isStrictModeActive(context)) return true
        
        // Check granular lock
        return when (feature) {
            FeatureType.BLOCKLIST -> !strictData.isBlocklistLocked
            FeatureType.BEDTIME -> !strictData.isBedtimeLocked
            FeatureType.APP_LIMIT -> !strictData.isAppLimitLocked
            FeatureType.GROUP_LIMIT -> !strictData.isGroupLimitLocked
            FeatureType.SCHEDULE -> !strictData.isScheduleLocked
        }
    }
}
