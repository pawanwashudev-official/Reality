package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    val date: String, // Format: yyyy-MM-dd
    val totalXP: Int,
    val totalStudyTimeMinutes: Long,
    val totalPlannedMinutes: Long = 0,
    val totalEffectiveMinutes: Long = 0,
    val streak: Int,
    val level: Int,
    val breakdownJson: String // JSON storing taskXP, sessionXP, etc.
)
