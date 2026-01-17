package com.neubofy.reality.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.CalendarEvent
import com.neubofy.reality.utils.TerminalLogger
import java.util.Calendar

/**
 * Smart Calendar Sync Worker
 * 
 * Sync Logic:
 * 1. If day changed → delete ALL, fresh sync
 * 2. Same day → intelligent diff:
 *    - Same eventId + title + time → no change
 *    - Same eventId, different title/time → update
 *    - New eventId → insert
 *    - Missing eventId → delete
 * 3. Single timestamp storage (overwrite, not accumulate)
 */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val PREF_NAME = "calendar_sync"
        private const val KEY_LAST_SYNC_DATE = "last_sync_date_millis"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                TerminalLogger.log("CALENDAR SYNC: No permission")
                return@withContext Result.failure()
            }

            val prefs = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val selectedCalendarIds = prefs.getStringSet("selected_calendar_ids", emptySet()) ?: emptySet()
            
            if (selectedCalendarIds.isEmpty()) {
                TerminalLogger.log("CALENDAR SYNC: No calendars selected")
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.calendarEventDao()

            // === SMART SYNC: Day Change Detection ===
            val todayStart = getTodayStartMillis()
            val lastSyncDate = prefs.getLong(KEY_LAST_SYNC_DATE, 0L)
            val isDayChanged = lastSyncDate < todayStart
            
            if (isDayChanged) {
                // New day → Fresh start: delete ALL events
                dao.clearAll()
                TerminalLogger.log("CALENDAR SYNC: Day changed - cleared all events")
            }
            
            // Update last sync timestamp (OVERWRITE, not accumulate)
            prefs.edit().putLong(KEY_LAST_SYNC_DATE, System.currentTimeMillis()).apply()

            // === Fetch Today's Events from System Calendar ===
            val endTime = todayStart + (24 * 60 * 60 * 1000)
            val fetchedEvents = fetchCalendarEvents(selectedCalendarIds, todayStart, endTime)
            
            if (isDayChanged) {
                // Fresh sync - just insert all
                dao.insertAll(fetchedEvents)
                TerminalLogger.log("CALENDAR SYNC: Fresh sync - inserted ${fetchedEvents.size} events")
            } else {
                // Smart diff sync
                val existingEvents = dao.getAllEvents()
                val existingMap = existingEvents.associateBy { it.eventId }
                
                var inserted = 0
                var updated = 0
                var deleted = 0
                
                // 1. Find events to INSERT or UPDATE
                for (fetched in fetchedEvents) {
                    val existing = existingMap[fetched.eventId]
                    
                    if (existing == null) {
                        // New event → INSERT
                        dao.insertAll(listOf(fetched))
                        inserted++
                    } else if (existing.title != fetched.title || 
                               existing.startTime != fetched.startTime || 
                               existing.endTime != fetched.endTime) {
                        // Same ID but contents changed → UPDATE (preserve isEnabled)
                        val updatedEvent = fetched.copy(isEnabled = existing.isEnabled)
                        dao.updateEvent(updatedEvent)
                        updated++
                        
                        // Clear from fired cache - time might have changed
                        com.neubofy.reality.utils.FiredEventsCache.clearFired(applicationContext, fetched.eventId)
                    }
                    // If title + time match, do nothing (no edit needed)
                }
                
                // 2. Find and DELETE events that are no longer in calendar
                val fetchedIds = fetchedEvents.map { it.eventId }.toSet()
                for (existing in existingEvents) {
                    if (existing.eventId !in fetchedIds) {
                        // Event removed from calendar → DELETE
                        dao.deleteByEventId(existing.eventId)
                        deleted++
                    }
                }
                
                TerminalLogger.log("CALENDAR SYNC: Smart sync - I:$inserted U:$updated D:$deleted")
            }
            
            // Trigger reminders refresh
            com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(applicationContext)
            
            // Notify AppBlockerService to refresh
            val intent = android.content.Intent("com.neubofy.reality.refresh.focus_mode")
            intent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(intent)
            
            Result.success()
        } catch (e: Exception) {
            TerminalLogger.log("CALENDAR SYNC ERROR: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }
    
    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    private fun fetchCalendarEvents(
        calendarIds: Set<String>, 
        startTime: Long, 
        endTime: Long
    ): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID
        )

        val selection = "${CalendarContract.Events.CALENDAR_ID} IN (${calendarIds.joinToString(",")}) AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

        applicationContext.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val eventId = cursor.getString(0)
                val title = cursor.getString(1) ?: "Untitled"
                val start = cursor.getLong(2)
                val end = cursor.getLong(3)
                val calId = cursor.getString(4)
                
                events.add(CalendarEvent(eventId, title, start, end, calId, isEnabled = true))
            }
        }
        
        return events
    }
}

