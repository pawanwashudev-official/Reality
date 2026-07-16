package com.neubofy.reality.data.repository

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class CalendarRepository(private val context: Context) {

    enum class EventStatus {
        PENDING, UPCOMING, RUNNING, COMPLETED
    }

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val description: String?,
        val startTime: Long,
        val endTime: Long,
        val color: Int,
        val location: String?,
        var status: EventStatus = EventStatus.PENDING,
        var progress: Float = 0f, // 0.0 to 1.0
        var isInternal: Boolean = false
    )

    fun getUpcomingEvents(hoursAhead: Int = 24): List<CalendarEvent> {
        return getEventsInRange(
            System.currentTimeMillis(),
            System.currentTimeMillis() + (hoursAhead * 60 * 60 * 1000L)
        )
    }

    fun getEventsForToday(): List<CalendarEvent> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfDay = cal.timeInMillis

        return getEventsInRange(startOfDay, endOfDay)
    }

    /**
     * Get events in a time range from synced local database.
     */
    fun getEventsInRange(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        try {
            val db = AppDatabase.getDatabase(context)
            val dbEvents = runBlocking {
                db.calendarEventDao().getEventsInRange(startMillis, endMillis)
            }
            
            for (dbEvent in dbEvents) {
                // Filter: Exclude "Family" events
                if (dbEvent.title.contains("family", ignoreCase = true)) continue
                
                events.add(
                    CalendarEvent(
                        id = (dbEvent.eventId.hashCode() and 0x7FFFFFFF).toLong(), // Map string UUID/ID hash to Long
                        title = dbEvent.title,
                        description = null,
                        startTime = dbEvent.startTime,
                        endTime = dbEvent.endTime,
                        color = 0xFF4CAF50.toInt(), // Default green color for calendar events
                        location = null
                    )
                )
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("ERROR loading synced events: ${e.message}")
        }
        return events
    }
    
    /**
     * Create a calendar event (Stub: device calendar writing is stripped).
     * @return -1
     */
    fun createEvent(title: String, startTime: Long, endTime: Long, description: String? = null): Long {
        return -1
    }
}
