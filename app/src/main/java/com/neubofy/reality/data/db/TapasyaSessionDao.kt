package com.neubofy.reality.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TapasyaSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TapasyaSession)
    
    @Query("SELECT * FROM tapasya_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<TapasyaSession>>
    
    @Query("SELECT * FROM tapasya_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<TapasyaSession>
    
    @Query("SELECT * FROM tapasya_sessions WHERE sessionId = :id")
    suspend fun getSessionById(id: String): TapasyaSession?
    
    @Query("SELECT SUM(effectiveTimeMs) FROM tapasya_sessions")
    suspend fun getTotalEffectiveTime(): Long?
    
    @Query("SELECT COUNT(*) FROM tapasya_sessions")
    suspend fun getSessionCount(): Int
    
    @Query("SELECT * FROM tapasya_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: Long): List<TapasyaSession>
    
    @Delete
    suspend fun delete(session: TapasyaSession)
    
    @Query("DELETE FROM tapasya_sessions")
    suspend fun deleteAll()
    
    @Query("DELETE FROM tapasya_sessions WHERE startTime < :cutoffTime")
    suspend fun deleteOldSessions(cutoffTime: Long)
    
    @Query("SELECT * FROM tapasya_sessions WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime DESC")
    suspend fun getSessionsForDay(dayStart: Long, dayEnd: Long): List<TapasyaSession>
}
