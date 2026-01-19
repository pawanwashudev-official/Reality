package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * XP Manager - Handles all gamification logic
 * 
 * XP Types:
 * - Tapasya XP: Live (15 XP per 1st fragment, 30 per 2nd, 45 per 3rd, etc.)
 * - Task XP: +100 per completed, -100 per incomplete (Nightly)
 * - Session XP: +100 attended, -100 missed, +50 early start (Nightly)
 * - Diary XP: AI assigned, max 500 (Nightly)
 * - Bonus XP: Screen time under limit (Nightly)
 * - Penalty XP: Screen time over limit (Nightly)
 */
data class GamificationLevel(
    val level: Int,
    val name: String,
    val requiredXP: Int,
    val requiredStreak: Int
)

object XPManager {
    
    private const val PREFS_NAME = "reflection_prefs"
    
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
    
    // Calculate XP for a single fragment
    fun getXPForFragment(fragmentNumber: Int): Int {
        return fragmentNumber * 15
    }
    
    // Get current fragment number
    fun getCurrentFragment(effectiveMinutes: Int): Int {
        return (effectiveMinutes / 15) + 1
    }
    
    // Add to Tapasya XP (and update daily totals)
    fun addTapasyaXP(context: Context, additionalXP: Int) {
        val prefs = getPrefs(context)
        checkDailyRollover(prefs)
        
        // Update Tapasya Component
        val currentTapasya = prefs.getInt("tapasya_xp", 0)
        val newTapasya = currentTapasya + additionalXP
        
        // Update Daily Total
        val currentToday = prefs.getInt("today_xp", 0)
        val newToday = currentToday + additionalXP
        
        // Update Global Total (Live)
        val currentTotal = prefs.getInt("total_xp", 0)
        val newTotal = currentTotal + additionalXP
        
        prefs.edit()
            .putInt("tapasya_xp", newTapasya)
            .putInt("today_xp", newToday)
            .putInt("total_xp", newTotal)
            .putString("last_xp_date", LocalDate.now().toString()) // Mark as active for today
            .apply()
            
        // Check for level up immediately
        updateLevel(context)
        
        TerminalLogger.log("XP: Added $additionalXP Tapasya XP. Today: $newToday")
    }
    
    // Check if new day, reset daily counters if needed
    private fun checkDailyRollover(prefs: SharedPreferences) {
        val lastDate = prefs.getString("last_xp_date", null)
        val today = LocalDate.now().toString()
        
        if (lastDate != today) {
            // New day detected (or first run) regarding XP updates
            // Force reset daily components because we are about to write new data for TODAY
            prefs.edit()
                .putInt("tapasya_xp", 0)
                .putInt("task_xp", 0)
                .putInt("session_xp", 0)
                .putInt("diary_xp", 0)
                .putInt("bonus_xp", 0)
                .putInt("penalty_xp", 0)
                .putInt("today_xp", 0)
                .putString("last_xp_date", today)
                .apply()
            TerminalLogger.log("XP: Rollover triggered (Last: $lastDate, Today: $today)")
        }
    }
    
    // Get all current XP values (checking specifically for rollover first)
    fun getXPBreakdown(context: Context): XPBreakdown {
        val prefs = getPrefs(context)
        checkDailyRollover(prefs)
        
        return XPBreakdown(
            totalXP = prefs.getInt("total_xp", 0),
            todayXP = prefs.getInt("today_xp", 0),
            tapasyaXP = prefs.getInt("tapasya_xp", 0),
            taskXP = prefs.getInt("task_xp", 0),
            sessionXP = prefs.getInt("session_xp", 0),
            diaryXP = prefs.getInt("diary_xp", 0),
            bonusXP = prefs.getInt("bonus_xp", 0),
            penaltyXP = prefs.getInt("penalty_xp", 0),
            streak = prefs.getInt("streak", 0),
            level = prefs.getInt("level", 1)
        )
    }
    
    // Calculate streak
    fun updateStreak(context: Context, studyPercent: Int): Int {
        val prefs = getPrefs(context)
        var streak = prefs.getInt("streak", 0)
        
        streak = when {
            studyPercent >= 75 -> streak + 1
            studyPercent >= 50 -> streak
            else -> 0
        }
        
        prefs.edit().putInt("streak", streak).apply()
        TerminalLogger.log("XP: Streak updated to $streak (study: $studyPercent%)")
        return streak
    }
    
    // Update level and save
    fun updateLevel(context: Context) {
        val prefs = getPrefs(context)
        val totalXP = prefs.getInt("total_xp", 0)
        val streak = prefs.getInt("streak", 0)
        
        val levelInfo = calculateLevel(context, totalXP, streak)
        
        prefs.edit().putInt("level", levelInfo.level).apply()
        TerminalLogger.log("XP: Level updated to ${levelInfo.level} (${levelInfo.name})")
    }
    
    // Get level name
    fun getLevelName(context: Context, level: Int): String {
        val levels = getAllLevels(context)
        return levels.find { it.level == level }?.name ?: "Unknown"
    }
    
    // Set custom level name (Legacy Wrapper)
    fun setCustomLevelName(context: Context, level: Int, name: String) {
        // Also save to new overrides
        val prefs = getPrefs(context)
        val overridesJson = prefs.getString("level_overrides", "{}")
        val overrides = try { JSONObject(overridesJson) } catch (e: Exception) { JSONObject() }
        
        // Preserve existing override data if any
        val data = overrides.optJSONObject(level.toString()) ?: JSONObject()
        data.put("name", name)
        overrides.put(level.toString(), data)
        prefs.edit().putString("level_overrides", overrides.toString()).apply()
        
        // Legacy support
        val customNamesJson = prefs.getString("custom_level_names", null)
        val customNames = if (customNamesJson != null) {
            try { JSONObject(customNamesJson) } catch (e: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        customNames.put(level.toString(), name)
        prefs.edit().putString("custom_level_names", customNames.toString()).apply()
    }

    // Set Total XP
    fun setTotalXP(context: Context, xp: Int) {
        val prefs = getPrefs(context)
        prefs.edit().putInt("total_xp", xp).apply()
        updateLevel(context)
    }

    // Set Streak
    fun setStreak(context: Context, streak: Int) {
        val prefs = getPrefs(context)
        prefs.edit().putInt("streak", streak).apply()
        updateLevel(context)
    }

    // Save Level Override
    fun saveLevelOverride(context: Context, levelId: Int, name: String, xp: Int, streak: Int) {
        val prefs = getPrefs(context)
        val overridesJson = prefs.getString("level_overrides", "{}")
        val overrides = try { JSONObject(overridesJson) } catch (e: Exception) { JSONObject() }
        
        val data = JSONObject()
        data.put("name", name)
        data.put("xp", xp)
        data.put("streak", streak)
        
        overrides.put(levelId.toString(), data)
        prefs.edit().putString("level_overrides", overrides.toString()).apply()
        
        setCustomLevelName(context, levelId, name)
        updateLevel(context)
    }

    // --- Projected XP Calculations (Live Preview) ---

    // Calculate projected Bonus/Penalty based on current usage
    // (Used by getProjectedDailyXP and ReflectionDetailActivity)
    fun calculateProjectedScreenTimeXP(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val limit = prefs.getInt("screen_time_limit_minutes", 0)
        
        if (limit > 0) {
            val usageMillis = try { UsageUtils.getFocusedAppsUsage(context) } catch(e: Exception) { 0L }
            val minutes = (usageMillis / 60000).toInt()
            val diff = limit - minutes
            
            return if (diff > 0) {
                Pair((diff * 10).coerceAtMost(500), 0)
            } else {
                Pair(0, (-diff * 10).coerceAtMost(500))
            }
        }
        return Pair(0, 0)
    }

    suspend fun getProjectedDailyXP(context: Context): XPBreakdown {
        val stored = getXPBreakdown(context)
        val today = LocalDate.now()
        val dateStr = today.toString()
        
        // 1. Task XP (Live projection disabled by user request - API limitations)
        // Only calculated during Nightly Protocol
        val projectedTaskXP = 0
        /* 
        val taskStats = try { ... } 
        */
        
        // 2. Session XP (Live from DB/Calendar)
        val projectedSessionXP = calculateProjectedSessionXP(context, today)
        
        // 3. Screen Time XP (Live from UsageStats)
        val screenTimeXP = calculateProjectedScreenTimeXP(context)
        val projectedBonus = screenTimeXP.first
        val projectedPenalty = screenTimeXP.second
        
        // 4. Calculate Totals
        val newTodayXP = stored.tapasyaXP + // Live
                        projectedTaskXP + 
                        projectedSessionXP + 
                        stored.diaryXP + // Stored (0 until done)
                        projectedBonus - 
                        projectedPenalty
        
        val projectedTotalXP = (stored.totalXP - stored.todayXP) + newTodayXP
        
        return stored.copy(
            totalXP = projectedTotalXP,
            todayXP = newTodayXP,
            taskXP = projectedTaskXP,
            sessionXP = projectedSessionXP,
            bonusXP = projectedBonus,
            penaltyXP = projectedPenalty
        )
    }
    
    private suspend fun calculateProjectedSessionXP(context: Context, date: LocalDate): Int {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)
                val startOfDay = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                
                val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
                val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
                val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                
                var sessionsAttended = 0
                var sessionsMissed = 0
                var earlyStarts = 0
                
                val now = System.currentTimeMillis()
                
                for (event in calendarEvents) {
                    val eventStart = event.startTime
                    val eventEnd = event.endTime
                    
                    val matchingSession = sessions.find { session ->
                        session.startTime < eventEnd && session.endTime > eventStart
                    }
                    
                    if (matchingSession != null) {
                        sessionsAttended++
                        if (matchingSession.startTime <= eventStart && 
                            matchingSession.startTime >= eventStart - 5 * 60 * 1000) {
                            earlyStarts++
                        }
                    } else {
                        if (eventEnd < now) {
                            sessionsMissed++
                        }
                    }
                }
                
                (sessionsAttended * 100) - (sessionsMissed * 100) + (earlyStarts * 50)
            } catch (e: Exception) {
                0
            }
        }
    }

    // Get All Levels
    fun getAllLevels(context: Context): List<GamificationLevel> {
        val prefs = getPrefs(context)
        val overridesJson = prefs.getString("level_overrides", "{}")
        val overrides = try { JSONObject(overridesJson) } catch (e: Exception) { JSONObject() }
        val legacyNames = try { JSONObject(prefs.getString("custom_level_names", "{}")) } catch(e: Exception) { JSONObject() }

        val list = ArrayList<GamificationLevel>()
        
        for (i in 1..100) {
            val def = LEVELS.getOrNull(i - 1)
            var name = def?.name ?: "Level $i"
            var xp = def?.requiredXP ?: (2500000 + (i - 50) * 100000)
            var streak = def?.requiredStreak ?: (1000 + (i - 50) * 20)

            if (legacyNames.has(i.toString())) {
                name = legacyNames.getString(i.toString())
            }

            if (overrides.has(i.toString())) {
                val data = overrides.getJSONObject(i.toString())
                name = data.optString("name", name)
                xp = data.optInt("xp", xp)
                streak = data.optInt("streak", streak)
            }
            
            list.add(GamificationLevel(i, name, xp, streak))
        }
        return list
    }
    
    // Updated calculateLevel
    fun calculateLevel(context: Context, totalXP: Int, streak: Int): GamificationLevel {
        val allLevels = getAllLevels(context)
        for (i in allLevels.indices.reversed()) {
            val level = allLevels[i]
            if (totalXP >= level.requiredXP && streak >= level.requiredStreak) {
                return level
            }
        }
        return allLevels[0]
    }
    
    // Deprecated: Uses only defaults
    fun calculateLevel(totalXP: Int, streak: Int): GamificationLevel {
         for (i in LEVELS.indices.reversed()) {
            val level = LEVELS[i]
            if (totalXP >= level.requiredXP && streak >= level.requiredStreak) {
                return level
            }
        }
        return LEVELS[0]
    }
    
    // Perform nightly reflection
    suspend fun performNightlyReflection(
        context: Context,
        tasksCompleted: Int,
        tasksIncomplete: Int,
        sessionsAttended: Int,
        sessionsMissed: Int,
        earlyStarts: Int,
        diaryXP: Int,
        screenTimeBonus: Int,
        screenTimePenalty: Int,
        studyPercent: Int,
        effectiveStudyMinutes: Int
    ) {
        val prefs = getPrefs(context)
        
        val taskXP = (tasksCompleted * 100) - (tasksIncomplete * 100)
        val sessionXP = (sessionsAttended * 100) - (sessionsMissed * 100) + (earlyStarts * 50)
        val bonusXP = screenTimeBonus
        val penaltyXP = screenTimePenalty
        
        val tapasyaXP = prefs.getInt("tapasya_xp", 0)
        val todayXP = tapasyaXP + taskXP + sessionXP + diaryXP + bonusXP - penaltyXP
        
        val currentTotalXP = prefs.getInt("total_xp", 0)
        val newTotalXP = (currentTotalXP + todayXP).coerceAtLeast(0)
        
        val newStreak = updateStreak(context, studyPercent)
        saveXPHistory(context, todayXP)
        
        prefs.edit()
            .putInt("total_xp", newTotalXP)
            .putInt("today_xp", todayXP)
            .putInt("task_xp", taskXP)
            .putInt("session_xp", sessionXP)
            .putInt("diary_xp", diaryXP)
            .putInt("bonus_xp", bonusXP)
            .putInt("penalty_xp", penaltyXP)
            .putString("last_xp_date", LocalDate.now().toString())
            .apply()
        
        updateLevel(context)
        val level = prefs.getInt("level", 1)

        // Save to DailyStats Database
        val breakdown = JSONObject().apply {
            put("tapasya", tapasyaXP)
            put("tasks", taskXP)
            put("sessions", sessionXP)
            put("diary", diaryXP)
            put("bonus", bonusXP)
            put("penalty", penaltyXP)
        }
        
        val today = LocalDate.now().toString()
        val stats = com.neubofy.reality.data.db.DailyStats(
            date = today,
            totalXP = todayXP, // Store daily XP, not cumulative
            totalStudyTimeMinutes = effectiveStudyMinutes.toLong(),
            streak = newStreak,
            level = level,
            breakdownJson = breakdown.toString()
        )
        
        try {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)
            db.dailyStatsDao().insertStats(stats)
            TerminalLogger.log("XP: Saved DailyStats for $today")
        } catch (e: Exception) {
            TerminalLogger.log("XP: Error saving DailyStats: ${e.message}")
        }
        
        TerminalLogger.log("XP: Nightly reflection complete. Today: $todayXP, Total: $newTotalXP")
    }
    
    // resetDailyXP removed: logic integrated into checkDailyRollover and called automatically
    
    private fun saveXPHistory(context: Context, todayXP: Int) {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString("xp_history", "[]")
        val history = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }
        val today = LocalDate.now().toString()
        val entry = JSONObject()
        entry.put("date", today)
        entry.put("xp", todayXP)
        history.put(entry)
        
        // Remove only if exceeding max raw retention (default 30)
        val rawLimit = prefs.getInt("retention_raw_days", 30)
        while (history.length() > rawLimit) {
            history.remove(0)
        }
        prefs.edit().putString("xp_history", history.toString()).apply()
    }
    
    suspend fun cleanupData(context: Context) {
        val prefs = getPrefs(context)
        val rawLimit = prefs.getInt("retention_raw_days", 30)
        val statsLimit = prefs.getInt("retention_stats_days", -1)
        val today = LocalDate.now()
        
        TerminalLogger.log("Nightly: Cleaning data. Raw Limit: $rawLimit days, Stats Limit: $statsLimit days")

        val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)

        // 1. Cleanup Raw XP History (JSON)
        // Using rawLimit
        val historyJson = prefs.getString("xp_history", "[]")
        val history = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }
        val rawCutoffDate = today.minusDays(rawLimit.toLong())
        
        val newHistory = JSONArray()
        for (i in 0 until history.length()) {
            val entry = history.optJSONObject(i) ?: continue
            val dateStr = entry.optString("date", "")
            try {
                val date = LocalDate.parse(dateStr)
                if (!date.isBefore(rawCutoffDate)) {
                    newHistory.put(entry)
                }
            } catch (e: Exception) { }
        }
        prefs.edit().putString("xp_history", newHistory.toString()).apply()
        
        // 2. Cleanup Raw DB Data (Tapasya Sessions, Calendar Events)
        val rawCutoffMillis = rawCutoffDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            db.tapasyaSessionDao().deleteOldSessions(rawCutoffMillis)
            db.calendarEventDao().deleteOldEvents(rawCutoffMillis)
            TerminalLogger.log("Nightly: Cleaned raw sessions/events older than $rawCutoffDate")
        } catch (e: Exception) {
            TerminalLogger.log("Nightly: Error cleaning raw DB data: ${e.message}")
        }

        // 3. Cleanup Daily Stats (Long-term)
        if (statsLimit != -1) {
            val statsCutoffDate = today.minusDays(statsLimit.toLong()).toString()
            try {
                db.dailyStatsDao().deleteOldStats(statsCutoffDate)
                TerminalLogger.log("Nightly: Cleaned DailyStats older than $statsCutoffDate")
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Error cleaning stats: ${e.message}")
            }
        }
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    data class XPBreakdown(
        val totalXP: Int,
        val todayXP: Int,
        val tapasyaXP: Int,
        val taskXP: Int,
        val sessionXP: Int,
        val diaryXP: Int,
        val bonusXP: Int,
        val penaltyXP: Int,
        val streak: Int,
        val level: Int
    )
    
    val LEVELS = listOf(
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
}
