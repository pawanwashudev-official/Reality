package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey val eventId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val calendarId: String,
    val isEnabled: Boolean = true
)
