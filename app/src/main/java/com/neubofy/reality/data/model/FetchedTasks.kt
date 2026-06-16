package com.neubofy.reality.data.model

data class FetchedTasks(
    val completedTasks: List<String>,
    val dueTasks: List<String>
)
