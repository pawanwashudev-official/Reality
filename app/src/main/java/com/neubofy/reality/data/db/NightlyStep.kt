package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "nightly_steps",
    primaryKeys = ["sessionDate", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = NightlySession::class,
            parentColumns = ["date"],
            childColumns = ["sessionDate"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["sessionDate"])]
)
data class NightlyStep(
    val sessionDate: String, // YYYY-MM-DD
    val stepId: Int, // 1-12
    
    val status: Int, // 0=Pending, 1=Running, 2=Completed, 3=Skipped, 4=Error
    val details: String? = null,
    val resultJson: String? = null, // For structured data (question lists, etc)
    val linkUrl: String? = null, // For "Open Doc" links
    
    val updatedAt: Long = System.currentTimeMillis()
)
