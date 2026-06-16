package com.neubofy.reality.data.model

data class WakeupAlarm(
    val id: String,
    val title: String,
    val description: String = "",
    val hour: Int,
    val minute: Int,
    var isEnabled: Boolean = true,
    var repeatDays: List<Int> = emptyList(), // Calendar.SUNDAY = 1
    var ringtoneUri: String? = null,
    var vibrationEnabled: Boolean = true,
    var snoozeIntervalMins: Int = 3,
    var maxAttempts: Int = 5,
    var isDeleted: Boolean = false // For recycle bin functionality
)
