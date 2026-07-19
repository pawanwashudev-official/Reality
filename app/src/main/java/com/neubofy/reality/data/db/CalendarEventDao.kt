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

    @Query("SELECT * FROM calendar_events WHERE source = 'GOOGLE'")
    suspend fun getGoogleEvents(): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE startTime <= :currentTime AND endTime >= :currentTime AND isEnabled = 1")
    suspend fun getCurrentEvents(currentTime: Long): List<CalendarEvent>

    @Query("SELECT MIN(time) FROM (SELECT startTime AS time FROM calendar_events WHERE startTime > :currentTime AND isEnabled = 1 UNION SELECT endTime AS time FROM calendar_events WHERE endTime > :currentTime AND isEnabled = 1)")
    suspend fun getNextEventTime(currentTime: Long): Long?

    @Query("SELECT * FROM calendar_events WHERE source = 'IN_APP' AND endTime <= :currentTime AND repeatRule IS NOT NULL")
    suspend fun getExpiredRepeatingInAppEvents(currentTime: Long): List<CalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEvent>)
    
    @androidx.room.Update
    suspend fun updateEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events WHERE endTime < :currentTime AND source = 'GOOGLE'")
    suspend fun deleteOldGoogleEvents(currentTime: Long)

    @Query("DELETE FROM calendar_events WHERE endTime < :currentTime AND source = 'IN_APP'")
    suspend fun deleteOldInAppEvents(currentTime: Long)

    @Query("DELETE FROM calendar_events WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM calendar_events WHERE source = 'GOOGLE'")
    suspend fun clearGoogleEvents()
}
