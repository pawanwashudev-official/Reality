package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.DailyStats
import com.neubofy.reality.google.GoogleTasksManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object XPManager {

    private const val PREFS_NAME = "xp_prefs"
    private const val PREF_TOTAL_XP = "total_xp"
    private const val PREF_LEVEL = "current_level"
    private const val PREF_STREAK = "current_streak"
    private const val PREF_RETENTION_DAYS = "xp_retention_days"

    // Data Class for XP Breakdown
    data class XPBreakdown(
        val date: String,
        val tapasyaXP: Int = 0,
        val taskXP: Int = 0,
        val sessionXP: Int = 0,
        val screenTimeXP: Int = 0,
        val reflectionXP: Int = 0,
        val bonusXP: Int = 0,
        val penaltyXP: Int = 0,
        val totalDailyXP: Int = 0,
        val level: Int = 1,
        val streak: Int = 0,
        val plannedMinutes: Long = 0,
        val effectiveMinutes: Long = 0
    )

    // Data Class for Level Information
    data class GamificationLevel(
        val level: Int,
        val name: String,
        val requiredXP: Int,
        val requiredStreak: Int
    )

    // Static Levels List (User Provided)
    private val LEVELS = listOf(
        GamificationLevel(1, "Novice Explorer", 0, 0),
        GamificationLevel(2, "Rising Student", 500, 1),
        GamificationLevel(3, "Eager Learner", 1000, 2),
        GamificationLevel(4, "Dedicated Pupil", 2000, 3),
        GamificationLevel(5, "Focus Initiate", 3500, 5),
        GamificationLevel(6, "Mindful Seeker", 5000, 7),
        GamificationLevel(7, "Discipline Holder", 7000, 10),
        GamificationLevel(8, "Steadfast Scholar", 10000, 14),
        GamificationLevel(9, "Knowledge Hunter", 15000, 18),
        GamificationLevel(10, "Wisdom Aspirant", 20000, 21),
        GamificationLevel(11, "Resilient Mind", 26000, 25),
        GamificationLevel(12, "Focus Champion", 33000, 30),
        GamificationLevel(13, "Elite Learner", 41000, 35),
        GamificationLevel(14, "Master Student", 50000, 40),
        GamificationLevel(15, "Sage Apprentice", 60000, 45),
        GamificationLevel(16, "Thought Leader", 72000, 50),
        GamificationLevel(17, "Mind Architect", 85000, 56),
        GamificationLevel(18, "Clarity Seeker", 100000, 63),
        GamificationLevel(19, "Peak Performer", 118000, 70),
        GamificationLevel(20, "Zen Warrior", 138000, 77),
        GamificationLevel(21, "Productivity Pro", 160000, 84),
        GamificationLevel(22, "Excellence Embodied", 185000, 91),
        GamificationLevel(23, "Unstoppable Force", 212000, 100),
        GamificationLevel(24, "Diamond Focus", 242000, 110),
        GamificationLevel(25, "Platinum Scholar", 275000, 120),
        GamificationLevel(26, "Legendary Learner", 310000, 130),
        GamificationLevel(27, "Epic Achiever", 350000, 140),
        GamificationLevel(28, "Mythic Mind", 393000, 150),
        GamificationLevel(29, "Transcendent Thinker", 440000, 160),
        GamificationLevel(30, "Grandmaster Scholar", 490000, 175),
        GamificationLevel(31, "Supreme Intellect", 545000, 190),
        GamificationLevel(32, "Cosmic Learner", 603000, 200),
        GamificationLevel(33, "Universal Sage", 665000, 215),
        GamificationLevel(34, "Infinite Focus", 730000, 230),
        GamificationLevel(35, "Eternal Student", 800000, 245),
        GamificationLevel(36, "Celestial Mind", 875000, 260),
        GamificationLevel(37, "Divine Scholar", 955000, 275),
        GamificationLevel(38, "Ascended Master", 1040000, 290),
        GamificationLevel(39, "Enlightened Being", 1130000, 305),
        GamificationLevel(40, "Reality Architect", 1225000, 320),
        GamificationLevel(41, "Time Lord", 1325000, 335),
        GamificationLevel(42, "Knowledge Titan", 1430000, 350),
        GamificationLevel(43, "Wisdom Sovereign", 1540000, 365),
        GamificationLevel(44, "Legendary Sage", 1660000, 400),
        GamificationLevel(45, "Mythical Master", 1785000, 450),
        GamificationLevel(46, "Epic Grandmaster", 1920000, 500),
        GamificationLevel(47, "Supreme Champion", 2065000, 600),
        GamificationLevel(48, "Transcendent Legend", 2220000, 730),
        GamificationLevel(49, "Eternal Grandmaster", 2385000, 900),
        GamificationLevel(50, "Reality Enlightened", 2560000, 1000)
    )

    /**
     * Get all level definitions, applying any user overrides.
     */
    fun getAllLevels(context: Context): List<GamificationLevel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = LEVELS.toMutableList()
        
        // Apply saved overrides (if any)
        for (i in defaults.indices) {
            val lvl = defaults[i].level
            val savedName = prefs.getString("level_${lvl}_name", null)
            val savedXP = prefs.getInt("level_${lvl}_xp", -1)
            val savedStreak = prefs.getInt("level_${lvl}_streak", -1)
            
            if (savedName != null || savedXP >= 0 || savedStreak >= 0) {
                defaults[i] = defaults[i].copy(
                    name = savedName ?: defaults[i].name,
                    requiredXP = if (savedXP >= 0) savedXP else defaults[i].requiredXP,
                    requiredStreak = if (savedStreak >= 0) savedStreak else defaults[i].requiredStreak
                )
            }
        }
        
        return defaults
    }

    // Cache for UI to access synchronously
    private var currentBreakdownCache: XPBreakdown? = null

    /**
     * Get XP breakdown for display (synchronous).
     * Returns cached value if available, or legacy prefs fallback.
     */
    fun getXPBreakdown(context: Context): XPBreakdown {
        if (currentBreakdownCache != null) return currentBreakdownCache!!
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return XPBreakdown(
            date = LocalDate.now().toString(),
            totalDailyXP = prefs.getInt("today_xp", 0),
            level = prefs.getInt(PREF_LEVEL, 1),
            streak = prefs.getInt(PREF_STREAK, 0)
        )
    }

    suspend fun getDailyStats(context: Context, date: String): XPBreakdown? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val stats = db.dailyStatsDao().getStatsForDate(date)
        if (stats != null) {
            parseBreakdown(stats.breakdownJson, date).copy(level = stats.level, streak = stats.streak)
        } else {
            null
        }
    }

    suspend fun getAllStatsDates(context: Context): List<String> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        db.dailyStatsDao().getAllStats().map { it.date }.sortedDescending()
    }

    // --- Core Logic: Tapasya XP Calculation ---
    // XP per fragment (each fragment = 15 minutes)
    fun calculateTapasyaXP(effectiveMinutes: Int): Int {
        val roundedMinutes = (effectiveMinutes / 15) * 15
        val fragments = roundedMinutes / 15
        
        var totalXP = 0
        for (i in 1..fragments) {
            totalXP += i * 15
        }
        return totalXP
    }

    // --- Core Logic: Task XP Calculation ---

    /**
     * Calculates Task XP manually (User Button Trigger).
     * Logic:
     * 1. Fetch Google Tasks for today.
     * 2. Parse Title: "{due time}|{Task title}" (e.g., "14:00|Meeting").
     * 3. Ignore tasks without due time.
     * 4. Filter tasks where Due Time <= Current Time.
     * 5. +100 XP for Completed, -100 XP for Pending/Due.
     */
    suspend fun calculateTaskXP(context: Context): Int = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val taskStats = GoogleTasksManager.getTasksForDate(context, today)
        
        var xp = 0
        val now = LocalTime.now()
        
        // Helper to check time condition
        fun checkTask(title: String): Boolean {
            val parts = title.split("|")
            if (parts.size >= 2) {
                var timeStr = parts[0].trim()
                // Handle {HH:mm} format by removing braces
                if (timeStr.startsWith("{") && timeStr.endsWith("}")) {
                    timeStr = timeStr.substring(1, timeStr.length - 1)
                }
                
                return try {
                    val dueTime = LocalTime.parse(timeStr) // Default ISO format HH:mm
                    !dueTime.isAfter(now) // True if Due <= Now
                } catch (e: Exception) {
                    false // Ignore if parse fails
                }
            }
            return false // Ignore if format doesn't match
        }

        // 1. Process Completed Tasks (+100)
        taskStats.completedTasks.forEach { title ->
            if (checkTask(title)) {
                xp += 100
            }
        }
        
        // 2. Process Pending (Due) Tasks (-100)
        taskStats.dueTasks.forEach { title ->
             if (checkTask(title)) {
                xp -= 100
            }
        }
        
        // Save to DB
        updateDailyStats(context, today) { current ->
            current.copy(taskXP = xp)
        }
        
        return@withContext xp
    }

    /**
     * Finalizes Task XP during Nightly Protocol (Step 7).
     * Rule: Counts ALL tasks for detailed date regardless of time.
     */
    suspend fun finalizeNightlyTaskXP(context: Context, date: String, completedTasks: List<String>, pendingTasks: List<String>): Int = withContext(Dispatchers.IO) {
        var xp = 0
        
        // +100 for every completed task
        xp += (completedTasks.size * 100)
        
        // -100 for every pending task
        xp -= (pendingTasks.size * 100)
        
        // Save to DB
        updateDailyStats(context, date) { current ->
            current.copy(taskXP = xp)
        }
        return@withContext xp
    }
    
    // Removed private isTaskDue as logic is now embedded in calculateTaskXP per spec

    // --- Persistence & Updates ---

    suspend fun updateDailyStats(
        context: Context, 
        date: String, 
        plannedMins: Long? = null,
        effectiveMins: Long? = null,
        update: (XPBreakdown) -> XPBreakdown
    ) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.dailyStatsDao()
        
        val existing = dao.getStatsForDate(date)
        val currentBreakdown = if (existing != null) {
            parseBreakdown(existing.breakdownJson, date).copy(
                plannedMinutes = existing.totalPlannedMinutes,
                effectiveMinutes = existing.totalEffectiveMinutes
            )
        } else {
            XPBreakdown(date)
        }
        
        var updatedBreakdown = update(currentBreakdown)
        
        // Update minutes if provided
        if (plannedMins != null || effectiveMins != null) {
            updatedBreakdown = updatedBreakdown.copy(
                plannedMinutes = plannedMins ?: updatedBreakdown.plannedMinutes,
                effectiveMinutes = effectiveMins ?: updatedBreakdown.effectiveMinutes
            )
        }
        val totalDaily = updatedBreakdown.tapasyaXP + updatedBreakdown.taskXP + updatedBreakdown.sessionXP + 
                         updatedBreakdown.screenTimeXP + updatedBreakdown.reflectionXP + updatedBreakdown.bonusXP - updatedBreakdown.penaltyXP
                         
        val finalBreakdown = updatedBreakdown.copy(totalDailyXP = totalDaily)
        
        // Update Cache if it's today
        if (date == LocalDate.now().toString()) {
            currentBreakdownCache = finalBreakdown
        }

        // Save to DB
        val entity = DailyStats(
            date = date,
            totalXP = totalDaily,
            totalStudyTimeMinutes = updatedBreakdown.effectiveMinutes, 
            totalPlannedMinutes = updatedBreakdown.plannedMinutes,
            totalEffectiveMinutes = updatedBreakdown.effectiveMinutes,
            streak = getStreak(context),
            level = getLevel(context),
            breakdownJson = toJson(finalBreakdown)
        )
        dao.insertStats(entity)
        
        // Sync Global Stats from DB to ensure consistency
        recalculateGlobalStats(context)
        
        // Update SharedPreferences Legacy "Today XP" for UI sync (ONLY IF TODAY)
        if (date == LocalDate.now().toString()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt("today_xp", totalDaily)
                .apply()
        }
    }
    
    // Recalculate Total XP, Level, and Streak from DB History
    suspend fun recalculateGlobalStats(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val allStats = db.dailyStatsDao().getAllStats() // Need to ensure DAO has this or use range
        
        var calculatedTotalXP = 0
        var currentStreak = 0
        
        // Calculate Total
        allStats.forEach { calculatedTotalXP += it.totalXP }
        
        // Calculate Streak (Consecutive days ending today/yesterday)
        // Sort by date desc
        val sortedStats = allStats.sortedByDescending { LocalDate.parse(it.date).toEpochDay() }
        if (sortedStats.isNotEmpty()) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            var lastDate = LocalDate.parse(sortedStats[0].date)
            
            // Streak is active if last entry is Today or Yesterday
            if (lastDate.isEqual(today) || lastDate.isEqual(yesterday)) {
                // Check if lastDate itself was successful
                val firstDay = sortedStats[0]
                val isFirstDaySuccessful = if (firstDay.totalPlannedMinutes > 0) {
                    firstDay.totalEffectiveMinutes >= (firstDay.totalPlannedMinutes * 0.75)
                } else {
                    firstDay.totalEffectiveMinutes > 0 // Or just true? User said "75% of planned". If 0 planned, maybe any work counts.
                }

                if (isFirstDaySuccessful) {
                    currentStreak = 1
                    var previousDate = lastDate
                    
                    for (i in 1 until sortedStats.size) {
                        val stat = sortedStats[i]
                        val date = LocalDate.parse(stat.date)
                        
                        if (date.isEqual(previousDate.minusDays(1))) {
                            val isSuccessful = if (stat.totalPlannedMinutes > 0) {
                                stat.totalEffectiveMinutes >= (stat.totalPlannedMinutes * 0.75)
                            } else {
                                stat.totalEffectiveMinutes > 0
                            }
                            
                            if (isSuccessful) {
                                currentStreak++
                                previousDate = date
                            } else {
                                break // Streak broken by unsuccessful day
                            }
                        } else {
                            break // Streak broken by gap
                        }
                    }
                }
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(PREF_TOTAL_XP, calculatedTotalXP)
            .putInt(PREF_STREAK, currentStreak)
            .apply()
            
        checkLevelUp(context, calculatedTotalXP)
    }
    
    suspend fun deleteDailyStats(context: Context, date: String) {
        val db = AppDatabase.getDatabase(context)
        db.dailyStatsDao().deleteStatsForDate(date) // Need to ensure DAO has this
        recalculateGlobalStats(context)
        
        if (date == LocalDate.now().toString()) {
            currentBreakdownCache = null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt("today_xp", 0)
                .apply()
        }
    }
    
    suspend fun enforceRetentionPolicy(context: Context) = withContext(Dispatchers.IO) {
        // Enforce rigid 7-day retention as requested
        val retentionDays = 7
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong()).toString()
        val db = AppDatabase.getDatabase(context)
        db.dailyStatsDao().deleteOldStats(cutoffDate)
        
        // Sync globals after deletion
        recalculateGlobalStats(context)
    }

    // --- XP Types Helpers ---
    
    suspend fun addTapasyaXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(tapasyaXP = it.tapasyaXP + xp) }
    }

    suspend fun addSessionXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(sessionXP = it.sessionXP + xp) }
    }
    
    suspend fun addScreenTimeXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(screenTimeXP = it.screenTimeXP + xp) }
    }
    
    suspend fun addReflectionXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(reflectionXP = it.reflectionXP + xp) }
    }

    suspend fun setReflectionXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(reflectionXP = xp) }
    }

    suspend fun addBonusXP(context: Context, xp: Int, date: String = LocalDate.now().toString()) {
        updateDailyStats(context, date) { it.copy(bonusXP = it.bonusXP + xp) }
    }
    
    // --- Global Stats (SharedPreferences) ---

    fun getLevel(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_LEVEL, 1)
    }

    fun getTotalXP(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_TOTAL_XP, 0)
    }

    fun getStreak(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_STREAK, 0)
    }
    
    private fun addGlobalXP(context: Context, amount: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newTotal = prefs.getInt(PREF_TOTAL_XP, 0) + amount
        prefs.edit().putInt(PREF_TOTAL_XP, newTotal).apply()
        checkLevelUp(context, newTotal)
    }
    
    private fun checkLevelUp(context: Context, totalXP: Int) {
        val allLevels = getAllLevels(context)
        val currentStreak = getStreak(context)
        
        // Find highest level reached where BOTH XP and Streak criteria are met
        var currentLevel = 1
        for (lvl in allLevels) {
            if (totalXP >= lvl.requiredXP && currentStreak >= lvl.requiredStreak) {
                currentLevel = lvl.level
            } else {
                // levels are sorted, so if one fails, we stop ascending
                if (totalXP < lvl.requiredXP) break
                // If streak fails but XP passes, we just don't advance to THIS level yet, 
                // but we keep checking in case a future level has a different fitting 
                // (though unlikely in standard progression)
            }
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedLevel = prefs.getInt(PREF_LEVEL, 1)
        
        if (currentLevel != storedLevel) {
            prefs.edit().putInt(PREF_LEVEL, currentLevel).apply()
        }
    }

    // --- Retention ---

    fun setRetentionPolicy(context: Context, weeks: Int) {
        val days = weeks * 7
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(PREF_RETENTION_DAYS, days)
            .apply()
    }
    
    suspend fun cleanupOldData(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val retentionDays = prefs.getInt(PREF_RETENTION_DAYS, 7)
        
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong()).toString()
        val db = AppDatabase.getDatabase(context)
        db.dailyStatsDao().deleteOldStats(cutoffDate)
    }

    // --- JSON Helpers ---

    private fun toJson(bd: XPBreakdown): String {
        return JSONObject().apply {
            put("tapasyaXP", bd.tapasyaXP)
            put("taskXP", bd.taskXP)
            put("sessionXP", bd.sessionXP)
            put("screenTimeXP", bd.screenTimeXP)
            put("reflectionXP", bd.reflectionXP)
            put("bonusXP", bd.bonusXP)
            put("penaltyXP", bd.penaltyXP)
            put("totalDailyXP", bd.totalDailyXP)
            put("level", bd.level)
            put("streak", bd.streak)
        }.toString()
    }

    private fun parseBreakdown(json: String, date: String): XPBreakdown {
        if (json.isEmpty()) return XPBreakdown(date)
        return try {
            val obj = JSONObject(json)
            XPBreakdown(
                date = date,
                tapasyaXP = obj.optInt("tapasyaXP", 0),
                taskXP = obj.optInt("taskXP", 0),
                sessionXP = obj.optInt("sessionXP", 0),
                screenTimeXP = obj.optInt("screenTimeXP", 0),
                reflectionXP = obj.optInt("reflectionXP", 0),
                bonusXP = obj.optInt("bonusXP", 0),
                penaltyXP = obj.optInt("penaltyXP", 0),
                totalDailyXP = obj.optInt("totalDailyXP", 0),
                level = obj.optInt("level", 1),
                streak = obj.optInt("streak", 0)
            )
        } catch (e: Exception) {
            XPBreakdown(date)
        }
    }
    
    // --- Compatibility / Stubs ---

    fun getLevelName(context: Context, level: Int): String {
        val levels = getAllLevels(context)
        return levels.find { it.level == level }?.name ?: "Unknown"
    }

    fun saveLevelOverride(context: Context, level: Int, name: String, xp: Int, streak: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("level_${level}_name", name)
            .putInt("level_${level}_xp", xp)
            .putInt("level_${level}_streak", streak)
            .apply()
    }
    
    suspend fun getProjectedDailyXP(context: Context): XPBreakdown = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val db = AppDatabase.getDatabase(context)
        val stats = db.dailyStatsDao().getStatsForDate(today)
        val current = if (stats != null) {
            parseBreakdown(stats.breakdownJson, today).copy(level = stats.level, streak = stats.streak)
        } else {
            XPBreakdown(today, level = getLevel(context), streak = getStreak(context))
        }
        
        // Calculate Screen Time XP Live for UI projection
        val stLimit = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE).getInt("screen_time_limit_minutes", 0)
        val stUsedMillis = com.neubofy.reality.utils.UsageUtils.getFocusedAppsUsage(context)
        val stUsedMins = (stUsedMillis / 60000).toInt()
        
        var stXp = 0
        var penaltyXp = current.penaltyXP
        
        if (stLimit > 0) {
            if (stUsedMins > stLimit) {
                val over = stUsedMins - stLimit
                penaltyXp = (over * 10).coerceAtMost(500)
                stXp = 0
            } else {
                val left = stLimit - stUsedMins
                stXp = (left * 10).coerceAtMost(500)
                penaltyXp = 0 
            }
        }
        
        val combined = current.copy(
            screenTimeXP = stXp,
            penaltyXP = penaltyXp
        )
        
        val totalDaily = combined.tapasyaXP + combined.taskXP + combined.sessionXP + 
                         combined.screenTimeXP + combined.reflectionXP + combined.bonusXP - combined.penaltyXP
                         
        return@withContext combined.copy(totalDailyXP = totalDaily)
    }
    
    suspend fun performNightlyReflection(context: Context, date: String) {
        // No-op
    }
}
