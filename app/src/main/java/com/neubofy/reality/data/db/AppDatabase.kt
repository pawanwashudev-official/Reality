package com.neubofy.reality.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CalendarEvent::class, AppGroupEntity::class, AppLimitEntity::class, ChatSession::class, ChatMessageEntity::class, TapasyaSession::class, DailyStats::class, NightlySession::class, NightlyStep::class, TaskListConfig::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun chatDao(): ChatDao
    abstract fun tapasyaSessionDao(): TapasyaSessionDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun nightlyDao(): NightlyDao
    abstract fun taskListConfigDao(): TaskListConfigDao

    companion object {
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nightly_sessions ADD COLUMN reportContent TEXT")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE daily_stats ADD COLUMN totalPlannedMinutes INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE daily_stats ADD COLUMN totalEffectiveMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_list_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `googleListId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `description` TEXT NOT NULL)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // nightly_sessions columns
                if (!hasColumn(database, "nightly_sessions", "isPlanVerified")) {
                    database.execSQL("ALTER TABLE nightly_sessions ADD COLUMN isPlanVerified INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(database, "nightly_sessions", "reflectionXp")) {
                    database.execSQL("ALTER TABLE nightly_sessions ADD COLUMN reflectionXp INTEGER NOT NULL DEFAULT 0")
                }
                
                // nightly_steps columns
                if (!hasColumn(database, "nightly_steps", "resultJson")) {
                    database.execSQL("ALTER TABLE nightly_steps ADD COLUMN resultJson TEXT")
                }
                if (!hasColumn(database, "nightly_steps", "linkUrl")) {
                    database.execSQL("ALTER TABLE nightly_steps ADD COLUMN linkUrl TEXT")
                }
            }
            
            private fun hasColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
                val cursor = db.query("PRAGMA table_info($tableName)")
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex == -1) return false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == columnName) {
                            return true
                        }
                    }
                } finally {
                    cursor.close()
                }
                return false
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reality_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
