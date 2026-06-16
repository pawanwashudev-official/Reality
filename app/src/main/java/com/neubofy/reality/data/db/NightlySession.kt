package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nightly_sessions")
data class NightlySession(
    @PrimaryKey
    val date: String, // YYYY-MM-DD
    
    val startTime: Long,
    val endTime: Long? = null,
    
    val totalXP: Int = 0,
    val status: Int = 0, // 0=InProgress, 1=Complete
    
    // Document IDs (previously in Prefs)
    val diaryDocId: String? = null,
    val planDocId: String? = null,
    val reportPdfId: String? = null,
    
    // Additional State
    val isPlanVerified: Boolean = false,
    val reflectionXp: Int = 0,
    
    // New Report Content
    val reportContent: String? = null
)
