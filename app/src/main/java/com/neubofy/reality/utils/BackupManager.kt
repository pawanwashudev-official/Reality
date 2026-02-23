package com.neubofy.reality.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.neubofy.reality.data.db.*
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.google.GoogleDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * BACKUP MANAGER - Google Drive Backup & Restore Engine
 * 
 * Exports/Imports all app data as a single JSON file to Google Drive.
 * Data is organized by category so users can selectively restore.
 * 
 * Backup file: reality_backup.json
 * Drive folder: .reality_backup (hidden by convention)
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_FOLDER_NAME = ".reality_backup"
    private const val BACKUP_FILE_NAME = "reality_backup.json"
    private const val BACKUP_VERSION = 1

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // === BACKUP CATEGORIES ===
    enum class BackupCategory(val displayName: String, val icon: String) {
        BLOCKLIST_FOCUS("Blocklist & Focus Mode", "🚫"),
        SCHEDULES("Schedules", "📅"),
        BEDTIME("Bedtime Mode", "🌙"),
        EMERGENCY("Emergency Mode", "🚨"),
        USAGE_LIMITS("Usage Limits", "⏱️"),
        STRICT_MODE("Strict Mode", "🔒"),
        GAMIFICATION("Gamification (XP/Streak)", "🏆"),
        NIGHTLY("Nightly Protocol", "🌃"),
        AI_SETTINGS("AI Settings", "🤖"),
        THEME("Theme & Appearance", "🎨"),
        BLOCK_MESSAGES("Block Messages", "💬"),
        REMINDERS("Reminders", "⏰"),
        APP_GROUPS("App Groups", "📦"),
        APP_LIMITS("Per-App Limits", "📊"),
        TAPASYA("Tapasya Sessions", "🧘"),
        DAILY_STATS("Daily Stats", "📈"),
        BLOCKED_APP_CONFIGS("App Blocking Modes", "⚙️")
    }

    // === DATA STRUCTURES ===
    data class BackupMetadata(
        val version: Int = BACKUP_VERSION,
        val appVersion: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val deviceModel: String = android.os.Build.MODEL,
        val categories: List<String> = emptyList()
    )

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val timestamp: Long = 0
    )

    data class BackupInfo(
        val exists: Boolean,
        val timestamp: Long = 0,
        val appVersion: String = "",
        val categories: List<String> = emptyList(),
        val sizeBytes: Long = 0
    )

    // Mapping of categories to SharedPreferences file names
    private val CATEGORY_PREFS_MAP = mapOf(
        BackupCategory.BLOCKLIST_FOCUS to listOf("focus_mode", "app_preferences"),
        BackupCategory.SCHEDULES to listOf("auto_focus_hours"),
        BackupCategory.BEDTIME to listOf("bedtime_mode"),
        BackupCategory.EMERGENCY to listOf("emergency_mode", "warning_data"),
        BackupCategory.USAGE_LIMITS to listOf("usage_limit"),
        BackupCategory.STRICT_MODE to listOf("strict_mode"),
        BackupCategory.GAMIFICATION to listOf("xp_prefs"),
        BackupCategory.NIGHTLY to listOf("nightly_prefs"),
        BackupCategory.AI_SETTINGS to listOf("ai_prefs"),
        BackupCategory.THEME to listOf("reality_theme_prefs"),
        BackupCategory.BLOCK_MESSAGES to listOf("block_messages"),
        BackupCategory.REMINDERS to listOf("custom_reminders"),
        BackupCategory.BLOCKED_APP_CONFIGS to listOf("blocked_app_configs")
    )

    // ===================================
    // BACKUP (EXPORT)
    // ===================================

    /**
     * Create a full backup and upload to Google Drive.
     * @param categories Which categories to include (all by default)
     * @param onProgress Callback for progress updates (0.0 to 1.0)
     */
    suspend fun createBackup(
        context: Context,
        categories: Set<BackupCategory> = BackupCategory.entries.toSet(),
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                // Check sign-in
                if (!GoogleAuthManager.isSignedIn(context)) {
                    return@withContext BackupResult(false, "Please sign in with Google first")
                }

                onProgress(0.05f, "Preparing backup...")

                val rootJson = JSONObject()
                val metadataJson = JSONObject()
                val categoriesJson = JSONObject()

                // Metadata
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                } catch (e: Exception) { "unknown" }

                metadataJson.put("version", BACKUP_VERSION)
                metadataJson.put("appVersion", appVersion)
                metadataJson.put("timestamp", System.currentTimeMillis())
                metadataJson.put("deviceModel", android.os.Build.MODEL)
                metadataJson.put("categoriesIncluded", gson.toJson(categories.map { it.name }))
                rootJson.put("metadata", metadataJson)

                onProgress(0.1f, "Exporting settings...")

                // Export SharedPreferences for selected categories
                var exportedCount = 0
                val totalCategories = categories.size
                for (category in categories) {
                    val categoryJson = JSONObject()

                    // Export SharedPreferences
                    val prefNames = CATEGORY_PREFS_MAP[category]
                    if (prefNames != null) {
                        for (prefName in prefNames) {
                            val prefData = exportSharedPreferences(context, prefName)
                            if (prefData.length() > 0) {
                                categoryJson.put("prefs_$prefName", prefData)
                            }
                        }
                    }

                    // Export Room DB data for relevant categories
                    when (category) {
                        BackupCategory.APP_GROUPS -> {
                            val db = AppDatabase.getDatabase(context)
                            val groups = db.appGroupDao().getAllGroups()
                            categoryJson.put("db_app_groups", gson.toJson(groups))
                        }
                        BackupCategory.APP_LIMITS -> {
                            val db = AppDatabase.getDatabase(context)
                            val limits = db.appLimitDao().getAllLimits()
                            categoryJson.put("db_app_limits", gson.toJson(limits))
                        }
                        BackupCategory.NIGHTLY -> {
                            val db = AppDatabase.getDatabase(context)
                            val sessions = db.nightlyDao().getAllSessions()
                            categoryJson.put("db_nightly_sessions", gson.toJson(sessions))
                            // Get steps for all sessions
                            val allSteps = mutableListOf<NightlyStep>()
                            for (session in sessions) {
                                allSteps.addAll(db.nightlyDao().getSteps(session.date))
                            }
                            categoryJson.put("db_nightly_steps", gson.toJson(allSteps))
                        }
                        BackupCategory.TAPASYA -> {
                            val db = AppDatabase.getDatabase(context)
                            val sessions = db.tapasyaSessionDao().getRecentSessions(10000)
                            categoryJson.put("db_tapasya_sessions", gson.toJson(sessions))
                        }
                        BackupCategory.DAILY_STATS -> {
                            val db = AppDatabase.getDatabase(context)
                            val stats = db.dailyStatsDao().getAllStats()
                            categoryJson.put("db_daily_stats", gson.toJson(stats))
                        }
                        else -> { /* No DB data for other categories */ }
                    }

                    if (categoryJson.length() > 0) {
                        categoriesJson.put(category.name, categoryJson)
                    }

                    exportedCount++
                    val progress = 0.1f + (0.6f * exportedCount / totalCategories)
                    onProgress(progress, "Exported ${category.displayName}")
                }

                rootJson.put("categories", categoriesJson)

                onProgress(0.75f, "Uploading to Google Drive...")

                // Upload to Drive
                val jsonBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
                val inputStream = ByteArrayInputStream(jsonBytes)

                // Get or create backup folder
                val (folderId, _) = GoogleDriveManager.getOrCreateFolder(context, BACKUP_FOLDER_NAME)

                // Check if backup file already exists → update it
                val existingFileId = GoogleDriveManager.searchFile(context, BACKUP_FILE_NAME, folderId)
                if (existingFileId != null) {
                    // Delete old backup
                    GoogleDriveManager.deleteFile(context, existingFileId)
                }

                // Upload new backup
                val fileId = GoogleDriveManager.uploadFile(
                    context,
                    BACKUP_FILE_NAME,
                    "application/json",
                    inputStream,
                    folderId
                )

                if (fileId != null) {
                    onProgress(1.0f, "Backup complete!")
                    TerminalLogger.log("$TAG: Backup uploaded successfully. Size: ${jsonBytes.size} bytes")
                    BackupResult(true, "Backup created successfully", System.currentTimeMillis())
                } else {
                    BackupResult(false, "Failed to upload backup to Drive")
                }

            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                TerminalLogger.log("$TAG: Need Drive permission - ${e.message}")
                BackupResult(false, "NEED_PERMISSION:${e.intent}")
            } catch (e: Exception) {
                TerminalLogger.log("$TAG: Backup failed - ${e.message}")
                BackupResult(false, "Backup failed: ${e.message}")
            }
        }
    }

    // ===================================
    // RESTORE (IMPORT)
    // ===================================

    /**
     * Restore data from Google Drive backup.
     * @param categories Which categories to restore (all available by default)
     * @param onProgress Callback for progress updates
     */
    suspend fun restoreBackup(
        context: Context,
        categories: Set<BackupCategory>? = null, // null = restore all available
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!GoogleAuthManager.isSignedIn(context)) {
                    return@withContext BackupResult(false, "Please sign in with Google first")
                }

                onProgress(0.05f, "Searching for backup...")

                // Find backup folder
                val folderId = try {
                    GoogleDriveManager.getOrCreateFolder(context, BACKUP_FOLDER_NAME).first
                } catch (e: Exception) {
                    return@withContext BackupResult(false, "No backup folder found on Drive")
                }

                // Find backup file
                val fileId = GoogleDriveManager.searchFile(context, BACKUP_FILE_NAME, folderId)
                    ?: return@withContext BackupResult(false, "No backup found on Google Drive")

                onProgress(0.15f, "Downloading backup...")

                // Download backup
                val data = GoogleDriveManager.downloadFile(context, fileId)
                    ?: return@withContext BackupResult(false, "Failed to download backup")

                val jsonString = String(data, Charsets.UTF_8)
                val rootJson = JSONObject(jsonString)

                onProgress(0.3f, "Parsing backup data...")

                // Validate version
                val metadata = rootJson.getJSONObject("metadata")
                val version = metadata.optInt("version", 0)
                if (version > BACKUP_VERSION) {
                    return@withContext BackupResult(false, "Backup is from a newer app version. Please update the app.")
                }

                val categoriesJson = rootJson.getJSONObject("categories")

                // Determine which categories to restore
                val availableCategories = mutableSetOf<BackupCategory>()
                val iter = categoriesJson.keys()
                while (iter.hasNext()) {
                    try {
                        availableCategories.add(BackupCategory.valueOf(iter.next()))
                    } catch (_: Exception) {}
                }

                val toRestore = categories?.intersect(availableCategories) ?: availableCategories

                onProgress(0.35f, "Restoring settings...")

                var restoredCount = 0
                val total = toRestore.size
                for (category in toRestore) {
                    val categoryJson = categoriesJson.optJSONObject(category.name) ?: continue

                    // Restore SharedPreferences
                    val prefNames = CATEGORY_PREFS_MAP[category]
                    if (prefNames != null) {
                        for (prefName in prefNames) {
                            val prefData = categoryJson.optJSONObject("prefs_$prefName")
                            if (prefData != null) {
                                importSharedPreferences(context, prefName, prefData)
                            }
                        }
                    }

                    // Restore Room DB data
                    when (category) {
                        BackupCategory.APP_GROUPS -> {
                            val json = categoryJson.optString("db_app_groups", "")
                            if (json.isNotEmpty()) {
                                val type = object : TypeToken<List<AppGroupEntity>>() {}.type
                                val groups: List<AppGroupEntity> = gson.fromJson(json, type)
                                val db = AppDatabase.getDatabase(context)
                                for (group in groups) {
                                    db.appGroupDao().insert(group)
                                }
                            }
                        }
                        BackupCategory.APP_LIMITS -> {
                            val json = categoryJson.optString("db_app_limits", "")
                            if (json.isNotEmpty()) {
                                val type = object : TypeToken<List<AppLimitEntity>>() {}.type
                                val limits: List<AppLimitEntity> = gson.fromJson(json, type)
                                val db = AppDatabase.getDatabase(context)
                                for (limit in limits) {
                                    db.appLimitDao().insert(limit)
                                }
                            }
                        }
                        BackupCategory.NIGHTLY -> {
                            val sessionsJson = categoryJson.optString("db_nightly_sessions", "")
                            if (sessionsJson.isNotEmpty()) {
                                val type = object : TypeToken<List<NightlySession>>() {}.type
                                val sessions: List<NightlySession> = gson.fromJson(sessionsJson, type)
                                val db = AppDatabase.getDatabase(context)
                                for (session in sessions) {
                                    db.nightlyDao().insertOrUpdateSession(session)
                                }
                            }
                            val stepsJson = categoryJson.optString("db_nightly_steps", "")
                            if (stepsJson.isNotEmpty()) {
                                val type = object : TypeToken<List<NightlyStep>>() {}.type
                                val steps: List<NightlyStep> = gson.fromJson(stepsJson, type)
                                val db = AppDatabase.getDatabase(context)
                                for (step in steps) {
                                    db.nightlyDao().insertOrUpdateStep(step)
                                }
                            }
                        }
                        BackupCategory.TAPASYA -> {
                            val json = categoryJson.optString("db_tapasya_sessions", "")
                            if (json.isNotEmpty()) {
                                val type = object : TypeToken<List<TapasyaSession>>() {}.type
                                val sessions: List<TapasyaSession> = gson.fromJson(json, type)
                                val db = AppDatabase.getDatabase(context)
                                for (session in sessions) {
                                    db.tapasyaSessionDao().insert(session)
                                }
                            }
                        }
                        BackupCategory.DAILY_STATS -> {
                            val json = categoryJson.optString("db_daily_stats", "")
                            if (json.isNotEmpty()) {
                                val type = object : TypeToken<List<DailyStats>>() {}.type
                                val stats: List<DailyStats> = gson.fromJson(json, type)
                                val db = AppDatabase.getDatabase(context)
                                for (stat in stats) {
                                    db.dailyStatsDao().insertStats(stat)
                                }
                            }
                        }
                        else -> { /* No DB data */ }
                    }

                    restoredCount++
                    val progress = 0.35f + (0.6f * restoredCount / total)
                    onProgress(progress, "Restored ${category.displayName}")
                }

                onProgress(1.0f, "Restore complete!")
                TerminalLogger.log("$TAG: Restore completed. $restoredCount categories restored.")
                BackupResult(true, "Restored $restoredCount categories successfully", System.currentTimeMillis())

            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                BackupResult(false, "NEED_PERMISSION:${e.intent}")
            } catch (e: Exception) {
                TerminalLogger.log("$TAG: Restore failed - ${e.message}")
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }
    }

    // ===================================
    // GET BACKUP INFO
    // ===================================

    /**
     * Check if a backup exists on Drive and get its info.
     */
    suspend fun getBackupInfo(context: Context): BackupInfo {
        return withContext(Dispatchers.IO) {
            try {
                if (!GoogleAuthManager.isSignedIn(context)) {
                    return@withContext BackupInfo(false)
                }

                val folderId = try {
                    GoogleDriveManager.getOrCreateFolder(context, BACKUP_FOLDER_NAME).first
                } catch (e: Exception) {
                    return@withContext BackupInfo(false)
                }

                val files = GoogleDriveManager.listFilesInFolder(context, folderId)
                val backupFile = files.find { it.name == BACKUP_FILE_NAME }
                    ?: return@withContext BackupInfo(false)

                // Get metadata from file
                val data = GoogleDriveManager.downloadFile(context, backupFile.id)
                if (data != null) {
                    val jsonString = String(data, Charsets.UTF_8)
                    val rootJson = JSONObject(jsonString)
                    val metadata = rootJson.getJSONObject("metadata")
                    val categoriesList = try {
                        val type = object : TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>>(metadata.optString("categoriesIncluded", "[]"), type)
                    } catch (_: Exception) { emptyList() }

                    BackupInfo(
                        exists = true,
                        timestamp = metadata.optLong("timestamp", 0),
                        appVersion = metadata.optString("appVersion", ""),
                        categories = categoriesList,
                        sizeBytes = data.size.toLong()
                    )
                } else {
                    BackupInfo(exists = true, timestamp = backupFile.modifiedTime?.value ?: 0)
                }

            } catch (e: Exception) {
                TerminalLogger.log("$TAG: Error getting backup info - ${e.message}")
                BackupInfo(false)
            }
        }
    }

    // ===================================
    // SHARED PREFERENCES HELPERS
    // ===================================

    /**
     * Export all key-value pairs from a SharedPreferences file to JSONObject.
     */
    private fun exportSharedPreferences(context: Context, prefName: String): JSONObject {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val json = JSONObject()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            when (value) {
                is String -> json.put(key, JSONObject().apply {
                    put("type", "string")
                    put("value", value)
                })
                is Int -> json.put(key, JSONObject().apply {
                    put("type", "int")
                    put("value", value)
                })
                is Long -> json.put(key, JSONObject().apply {
                    put("type", "long")
                    put("value", value)
                })
                is Float -> json.put(key, JSONObject().apply {
                    put("type", "float")
                    put("value", value.toDouble())
                })
                is Boolean -> json.put(key, JSONObject().apply {
                    put("type", "boolean")
                    put("value", value)
                })
                is Set<*> -> json.put(key, JSONObject().apply {
                    put("type", "stringset")
                    put("value", gson.toJson(value))
                })
            }
        }
        return json
    }

    /**
     * Import key-value pairs from JSONObject into a SharedPreferences file.
     */
    private fun importSharedPreferences(context: Context, prefName: String, json: JSONObject) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val entry = json.getJSONObject(key)
                val type = entry.getString("type")
                when (type) {
                    "string" -> editor.putString(key, entry.getString("value"))
                    "int" -> editor.putInt(key, entry.getInt("value"))
                    "long" -> editor.putLong(key, entry.getLong("value"))
                    "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                    "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                    "stringset" -> {
                        val setType = object : TypeToken<Set<String>>() {}.type
                        val set: Set<String> = gson.fromJson(entry.getString("value"), setType)
                        editor.putStringSet(key, set)
                    }
                }
            } catch (e: Exception) {
                TerminalLogger.log("$TAG: Error importing pref key '$key' - ${e.message}")
            }
        }

        editor.apply()
    }
}
