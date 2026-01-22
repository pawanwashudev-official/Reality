package com.neubofy.reality.data.model

import java.time.LocalDate
import com.neubofy.reality.data.db.CalendarEvent as DbCalendarEvent
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.data.repository.CalendarRepository

data class DaySummary(
    val date: LocalDate,
    val completedSessions: List<TapasyaSession>,
    val calendarEvents: List<CalendarRepository.CalendarEvent>,
    val tasksDue: List<String>,
    val tasksCompleted: List<String>,
    val plannedEvents: List<DbCalendarEvent> = emptyList(),
    val totalPlannedMinutes: Long = 0,
    val totalEffectiveMinutes: Long = 0
)
