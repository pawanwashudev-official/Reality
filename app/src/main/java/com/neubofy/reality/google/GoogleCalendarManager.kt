package com.neubofy.reality.google

import android.content.Context
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Google Calendar API wrapper.
 */
object GoogleCalendarManager {
    
    private const val APP_NAME = "Reality"
    
    private fun getCalendarService(context: Context): Calendar? {
        val credential = GoogleAuthManager.getGoogleAccountCredential(context) ?: return null
        
        return Calendar.Builder(
            GoogleAuthManager.getHttpTransport(),
            GoogleAuthManager.getJsonFactory(),
            credential
        )
            .setApplicationName("com.neubofy.reality")
            .build()
    }
    
    /**
     * Create a new event on Google Calendar.
     * @return The event ID if successful, null otherwise.
     */
    suspend fun createEvent(
        context: Context,
        title: String,
        startTimeMs: Long,
        endTimeMs: Long,
        description: String? = null,
        calendarId: String = "primary"
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context) ?: return@withContext null
                
                val start = EventDateTime()
                    .setDateTime(DateTime(Date(startTimeMs)))
                    .setTimeZone(java.util.TimeZone.getDefault().id)
                
                val end = EventDateTime()
                    .setDateTime(DateTime(Date(endTimeMs)))
                    .setTimeZone(java.util.TimeZone.getDefault().id)
                
                val event = Event().apply {
                    summary = title
                    this.description = description
                    this.start = start
                    this.end = end
                }
                
                val createdEvent = service.events().insert(calendarId, event).execute()
                TerminalLogger.log("CALENDAR API: Created event '${createdEvent.summary}' (ID: ${createdEvent.id})")
                createdEvent.id
            } catch (e: Exception) {
                TerminalLogger.log("CALENDAR API: Error creating event - ${e.message}")
                null
            }
        }
    }
    /**
     * Get events for a specific day from Google Calendar API.
     */
    suspend fun getEvents(context: Context, startTimeMs: Long, endTimeMs: Long, calendarId: String = "primary"): List<Event> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context) ?: return@withContext emptyList()
                
                val events = service.events().list(calendarId)
                    .setTimeMin(DateTime(Date(startTimeMs)))
                    .setTimeMax(DateTime(Date(endTimeMs)))
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute()
                
                TerminalLogger.log("CALENDAR API: Fetched ${events.items?.size ?: 0} events")
                
                // Filter: Exclude All-Day (dateTime is null) and Family events
                events.items?.filter { event ->
                    val isAllDay = event.start.dateTime == null
                    val isFamily = (event.summary ?: "").contains("family", ignoreCase = true) ||
                                  (event.description ?: "").contains("family", ignoreCase = true)
                    !isAllDay && !isFamily
                } ?: emptyList()
            } catch (e: Exception) {
                TerminalLogger.log("CALENDAR API: Error fetching events - ${e.message}")
                emptyList()
            }
        }
    }
}
