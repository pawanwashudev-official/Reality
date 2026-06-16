package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val limitInMinutes: Int,
    val isStrict: Boolean,
    val activePeriodsJson: String = "[]" // JSON list of TimeRange objects
)
