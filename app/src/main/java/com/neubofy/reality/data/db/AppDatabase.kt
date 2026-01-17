package com.neubofy.reality.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CalendarEvent::class, AppGroupEntity::class, AppLimitEntity::class, ChatSession::class, ChatMessageEntity::class, TapasyaSession::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun chatDao(): ChatDao
    abstract fun tapasyaSessionDao(): TapasyaSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reality_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
