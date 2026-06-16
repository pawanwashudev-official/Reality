package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_groups")
data class AppGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitInMinutes: Int,
    val packageNamesJson: String,
    val isStrict: Boolean,
    val createdTime: Long = System.currentTimeMillis(),
    val activePeriodsJson: String = "[]"
)
