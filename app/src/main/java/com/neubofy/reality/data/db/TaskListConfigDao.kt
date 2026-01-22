package com.neubofy.reality.data.db

import androidx.room.*

@Dao
interface TaskListConfigDao {
    @Query("SELECT * FROM task_list_configs")
    suspend fun getAll(): List<TaskListConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: TaskListConfig)

    @Delete
    suspend fun delete(config: TaskListConfig)

    @Update
    suspend fun update(config: TaskListConfig)

    @Query("DELETE FROM task_list_configs")
    suspend fun deleteAll()
}
