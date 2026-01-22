package com.neubofy.reality.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NightlyDao {

    // --- Session ---
    @Query("SELECT * FROM nightly_sessions WHERE date = :date")
    suspend fun getSession(date: String): NightlySession?

    @Query("SELECT * FROM nightly_sessions ORDER BY date DESC")
    suspend fun getAllSessions(): List<NightlySession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: NightlySession)

    @Query("DELETE FROM nightly_sessions WHERE date = :date")
    suspend fun deleteSession(date: String)

    // --- Steps ---
    @Query("SELECT * FROM nightly_steps WHERE sessionDate = :date AND stepId = :stepId")
    suspend fun getStep(date: String, stepId: Int): NightlyStep?

    @Query("SELECT * FROM nightly_steps WHERE sessionDate = :date ORDER BY stepId ASC")
    suspend fun getSteps(date: String): List<NightlyStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStep(step: NightlyStep)

    @Query("DELETE FROM nightly_steps WHERE sessionDate = :date")
    suspend fun deleteStepsForSession(date: String)
    
    @Query("DELETE FROM nightly_sessions WHERE date < :cutoffDate")
    suspend fun deleteOldSessions(cutoffDate: String)
    
    @Query("DELETE FROM nightly_steps WHERE sessionDate < :cutoffDate")
    suspend fun deleteOldSteps(cutoffDate: String)
    
    // --- Transactional Helper ---
    @Transaction
    suspend fun initializeSession(session: NightlySession) {
        if (getSession(session.date) == null) {
            insertOrUpdateSession(session)
        }
    }
}
