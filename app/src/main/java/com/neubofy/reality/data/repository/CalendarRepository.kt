package com.neubofy.reality.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
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

    private fun getEventsInRange(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION
        )

        // Construct the query with the Instances URI
        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, startMillis)
        android.content.ContentUris.appendId(builder, endMillis)

        val cursor: Cursor? = context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val descIndex = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val beginIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
            val colorIndex = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
            val locationIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)

            while (it.moveToNext()) {
                val title = it.getString(titleIndex) ?: "No Title"
                val begin = it.getLong(beginIndex)
                val end = it.getLong(endIndex)
                
                events.add(
                    CalendarEvent(
                        id = it.getLong(idIndex),
                        title = title,
                        description = it.getString(descIndex),
                        startTime = begin,
                        endTime = end,
                        color = it.getInt(colorIndex),
                        location = it.getString(locationIndex)
                    )
                )
            }
        }
        return events
    }
}
