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
        val credential = GoogleAuthManager.getGoogleCredential(context) ?: return null
        
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
    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 8000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        for (i in 0 until times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                val isQuotaError = e.message?.contains("429") == true || 
                                   e.message?.contains("503") == true || 
                                   e.message?.contains("quota") == true || 
                                   e.message?.contains("rateLimit") == true
                
                if (!isQuotaError && e !is java.io.IOException) {
                    throw e
                }
                
                TerminalLogger.log("API: Retry ${i+1}/$times due to ${e.message} in ${currentDelay}ms")
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block()
    }

    /**
     * Get events for a specific day from Google Calendar API.
     * Uses exponential backoff for rate limits.
     */
    suspend fun getEvents(context: Context, startTimeMs: Long, endTimeMs: Long, calendarId: String = "primary"): List<Event> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context) ?: throw Exception("Not signed in to Google Workspace")
                
                val events = retryWithBackoff {
                    service.events().list(calendarId)
                        .setTimeMin(DateTime(Date(startTimeMs)))
                        .setTimeMax(DateTime(Date(endTimeMs)))
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .execute()
                }
                
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
                throw e // CRITICAL: Throw the error so the Worker knows it failed and doesn't wipe the local database!
            }
        }
    }
}
