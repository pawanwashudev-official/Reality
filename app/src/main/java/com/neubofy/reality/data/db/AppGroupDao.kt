package com.neubofy.reality.data.db

import androidx.room.*

@Dao
interface AppGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AppGroupEntity): Long

    @Update
    suspend fun update(group: AppGroupEntity)

    @Delete
    suspend fun delete(group: AppGroupEntity)

    @Query("SELECT * FROM app_groups")
    suspend fun getAllGroups(): List<AppGroupEntity>

    @Query("SELECT * FROM app_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): AppGroupEntity?
}
