package com.neubofy.reality.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_list_configs")
data class TaskListConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val googleListId: String,
    val displayName: String,
    val description: String
)
