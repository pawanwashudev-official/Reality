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
import java.util.Calendar

class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                return@withContext Result.failure()
            }

            val prefs = applicationContext.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
            val selectedCalendarIds = prefs.getStringSet("selected_calendar_ids", emptySet()) ?: emptySet()
            
            if (selectedCalendarIds.isEmpty()) {
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.calendarEventDao()

            // 1. Snapshot existing states to preserve toggles
            val existingEvents = dao.getAllEvents()
            val stateMap = existingEvents.associate { it.eventId to it.isEnabled }

            // Clear old events
            dao.deleteOldEvents(System.currentTimeMillis())

            // Fetch events from START OF TODAY to END OF TODAY
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            val startTime = cal.timeInMillis
            val endTime = startTime + (24 * 60 * 60 * 1000) // End of today (24 hours)

            val events = mutableListOf<CalendarEvent>()
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.CALENDAR_ID
            )

            val selection = "${CalendarContract.Events.CALENDAR_ID} IN (${selectedCalendarIds.joinToString(",")}) AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
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
                    
                    // Preserve state or default Enable
                    val isEnabled = stateMap[eventId] ?: true
                    
                    events.add(CalendarEvent(eventId, title, start, end, calId, isEnabled))
                }
            }

            dao.insertAll(events)
            
            // Notify AppBlockerService to refresh
            val intent = android.content.Intent("com.neubofy.reality.refresh.focus_mode")
            applicationContext.sendBroadcast(intent)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
