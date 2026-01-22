package com.neubofy.reality.data.nightly

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.data.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class NightlyDataCollector(private val context: Context) {

    suspend fun fetchTasks(date: LocalDate): GoogleTasksManager.TaskStats = withContext(Dispatchers.IO) {
        val dateString = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        try {
            GoogleTasksManager.getTasksForDate(context, dateString)
        } catch (e: Exception) {
            GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
        }
    }

    suspend fun collectDayData(date: LocalDate, taskStats: GoogleTasksManager.TaskStats): DaySummary = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        val calendarRepo = CalendarRepository(context)
        val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
        
        val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
        val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)
        
        val totalPlannedMinutes = calendarEvents.sumOf { (it.endTime - it.startTime) / 60000 }
        val totalEffectiveMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }
        
        DaySummary(
            date = date,
            calendarEvents = calendarEvents,
            completedSessions = sessions,
            tasksDue = taskStats.dueTasks,
            tasksCompleted = taskStats.completedTasks,
            plannedEvents = plannedEvents,
            totalPlannedMinutes = totalPlannedMinutes,
            totalEffectiveMinutes = totalEffectiveMinutes
        )
    }
}
