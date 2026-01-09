package com.neubofy.reality

class Constants {
    companion object {
        // available modes for setting up anti-uninstall
        const val ANTI_UNINSTALL_PASSWORD_MODE = 1
        const val ANTI_UNINSTALL_TIMED_MODE = 2

        // available types of warning screen
        const val WARNING_SCREEN_MODE_APP_BLOCKER = 2

        // available types for focus mode
        const val FOCUS_MODE_BLOCK_ALL_EX_SELECTED = 1
        const val FOCUS_MODE_BLOCK_SELECTED = 2
        
        // Emergency mode constants
        const val EMERGENCY_MAX_USES = 3
        const val EMERGENCY_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    data class WarningData(
        var message: String = "This app is currently blocked by Reality",
        var timeInterval: Int = 60000 * 5,
        var isDynamicIntervalSettingAllowed: Boolean = false,
        var isProceedDisabled: Boolean = false,
        var isWarningDialogHidden: Boolean = false,
        var proceedDelayInSecs: Int = 3
    )

    data class AutoTimedActionItem(
        val title: String,
        val startTimeInMins: Int,
        val endTimeInMins: Int,
        val packages: ArrayList<String>,
        val isProceedHidden: Boolean = false,
        var repeatDays: List<Int> = listOf(1,2,3,4,5,6,7),
        var isReminderEnabled: Boolean = true
    )
    
    data class BedtimeData(
        var isEnabled: Boolean = false,
        var startTimeInMins: Int = 1320, // 22:00 (10 PM)
        var endTimeInMins: Int = 420,    // 07:00 (7 AM)
        var blockedApps: HashSet<String> = hashSetOf()
    )
    
    data class EmergencyModeData(
        var usesRemaining: Int = EMERGENCY_MAX_USES,
        var currentSessionEndTime: Long = -1,
        var lastResetDate: Long = System.currentTimeMillis()
    )

    data class UsageLimitData(
        var isEnabled: Boolean = false, // Global/Group Limit Switch
        var limitInMinutes: Int = 180,   // Global/Group Limit (3 hours default)
        var usedTimeInMillis: Long = 0, // Global/Group Usage
        
        // Per-App Limits
        var appLimits: HashMap<String, Int> = hashMapOf(), // Package -> Limit (Mins)
        var appUsages: HashMap<String, Long> = hashMapOf(), // Package -> Usage (Ms)
        
        var lastResetDate: Long = System.currentTimeMillis()
    )

    data class StrictModeData(
        var isEnabled: Boolean = false,
        var modeType: String = MODE_NONE, 
        var timerEndTime: Long = 0,
        var passwordHash: String = "",
        var lastVerifyDate: Long = 0,
        
        // Forgot Password: 24hr waiting period
        var forgotPasswordTimerEndTime: Long = 0,
        
        // Granular Control
        var isBlocklistLocked: Boolean = true,
        var isBedtimeLocked: Boolean = true,
        var isAppLimitLocked: Boolean = true,
        var isGroupLimitLocked: Boolean = true,
        var isScheduleLocked: Boolean = true,
        
        // Anti-Uninstall Protection (Device Admin page)
        var isAntiUninstallEnabled: Boolean = false,
        
        // App Info Protection (Force Stop / Uninstall page)
        var isAppInfoProtectionEnabled: Boolean = false,
        
        // Anti-Time Cheat
        var isTimeCheatProtectionEnabled: Boolean = true,
        
        // Lock Emergency Access
        var isEmergencyLocked: Boolean = true,
        
        // Lock Auto DND
        var isAutoDndLocked: Boolean = true,
        
        // Accessibility Protection - Prevents disabling accessibility service
        var isAccessibilityProtectionEnabled: Boolean = false,
        
        // Lock Calendar Sync - Prevents disconnecting calendar when strict mode is active
        var isCalendarLocked: Boolean = true
    ) {
        companion object {
            const val MODE_NONE = "NONE"
            const val MODE_TIMER = "TIMER"
            const val MODE_PASSWORD = "PASSWORD"
        }
    }
    data class BlockMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val message: String,
        val tags: List<String> = listOf("ALL") // "FOCUS", "BEDTIME", "LIMIT", "STRICT"
    )
    
    // Settings Page Learning - Device-specific class names
    enum class PageType {
        ACCESSIBILITY,
        DEVICE_ADMIN,
        APP_INFO,
        TIME_SETTINGS,
        DEVELOPER_OPTIONS
    }
    
    data class LearnedSettingsPages(
        var accessibilityPageClass: String = "",
        var deviceAdminPageClass: String = "",
        var appInfoPageClass: String = "",
        var timeSettingsPageClass: String = "",
        var developerOptionsPageClass: String = "",
        
        // User can add custom pages to block
        var customBlockedPages: MutableSet<String> = mutableSetOf(),
        
        // Penalty tracking
        var lastPenaltyTime: Long = 0,
        var consecutiveAttempts: Int = 0
    )
}