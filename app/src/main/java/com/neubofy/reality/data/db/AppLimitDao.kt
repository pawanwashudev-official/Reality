package com.neubofy.reality.data.db

import androidx.room.*

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits")
    fun getAllLimits(): List<AppLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(limit: AppLimitEntity)
    
    @Delete
    fun delete(limit: AppLimitEntity)
    
    @Query("DELETE FROM app_limits WHERE packageName = :pkg")
    fun deleteByPackage(pkg: String)
    
    @Query("SELECT * FROM app_limits WHERE packageName = :pkg")
    fun getLimit(pkg: String): AppLimitEntity?
}
