package com.neubofy.reality.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE endTime >= :currentTime ORDER BY startTime ASC")
    suspend fun getUpcomingEvents(currentTime: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :start AND startTime < :end ORDER BY startTime ASC")
    suspend fun getEventsInRange(start: Long, end: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAllEvents(): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE startTime <= :currentTime AND endTime >= :currentTime AND isEnabled = 1")
    suspend fun getCurrentEvents(currentTime: Long): List<CalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEvent>)
    
    @androidx.room.Update
    suspend fun updateEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events WHERE endTime < :currentTime")
    suspend fun deleteOldEvents(currentTime: Long)

    @Query("DELETE FROM calendar_events WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM calendar_events")
    suspend fun clearAll()
}
