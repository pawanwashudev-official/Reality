package com.neubofy.reality.data

/**
 * Custom Reminder with SNAPSHOT settings.
 * All settings are captured at creation time so later global changes don't affect this reminder.
 */
data class CustomReminder(
    val id: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val repeatDays: List<Int> = emptyList(), // Calendar.SUNDAY = 1
    val retryIntervalMins: Int = 0, // 0 = disabled
    val lastDismissedDate: Long = 0L,
    
    // SNAPSHOT: Offset in minutes (captured from global at creation if not custom)
    val offsetMins: Int = 1,
    
    // SNAPSHOT: URL to redirect (captured from global at creation, or custom)
    val redirectUrl: String? = null,
    
    // SNAPSHOT: Snooze settings (captured from global at creation)
    val snoozeEnabled: Boolean = true,
    val snoozeIntervalMins: Int = 5,
    val autoSnoozeEnabled: Boolean = true,
    val autoSnoozeTimeoutSecs: Int = 30,
    
    // Legacy fields for migration compatibility
    @Deprecated("Use offsetMins") val customOffsetMins: Int? = null,
    @Deprecated("Use redirectUrl") val url: String? = null,
    @Deprecated("Use redirectUrl") val urlSource: Int = 1
)
