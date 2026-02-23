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
    private const val PREF_ACCUMULATED_XP = "accumulated_xp" // The Vault
    private const val PREF_LEVEL = "current_level"
    private const val PREF_STREAK = "current_streak"
    private const val PREF_RETENTION_DAYS = "xp_retention_days"
    private const val PREF_VAULT_LAST_DATE = "vault_last_date" // Last date added to vault

    // Data Class for XP Breakdown
    data class XPBreakdown(
        val date: String,
        val tapasyaXP: Int = 0,
        val taskXP: Int = 0,
        val sessionXP: Int = 0,
        val distractionXP: Int = 0,  // Renamed from screenTimeXP
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
    
    // --- Core Logic: Distraction Penalty (formerly Screen Time XP) ---
    private suspend fun calculateScreenTimeXP(context: Context, date: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val limitMins = prefs.getInt("screen_time_limit_minutes", 0)
        
        // UNIFIED LOGIC: Use SavedPreferencesLoader to get blocked apps
        val spLoader = SavedPreferencesLoader(context)
        val blockedApps = spLoader.loadBlockedApps()

        // If no apps are blocked, no penalty logic applies (unless user wants general screen time limit?)
        // User requested: "apply logic on those app only... list of app came from blocklist"
        if (blockedApps.isEmpty()) return@withContext Pair(0, 0)
        
        val dateObj = LocalDate.parse(date)
        val startOfDay = dateObj.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = dateObj.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        // Use UsageStatsManager to get time for that range
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
        var totalDistractedMillis = 0L
        
        if (usageStatsManager != null) {
             val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                endOfDay
            )
             
             for (stat in stats) {
                if (stat.totalTimeInForeground <= 0) continue
                // ONLY count apps in the Blocklist
                if (blockedApps.contains(stat.packageName)) {
                    totalDistractedMillis += stat.totalTimeInForeground
                }
             }
        }
        
        val distractedMins = (totalDistractedMillis / 60000).toInt()
        
        // Logic: 
        // If Used > Limit: Penalty
        // If Used < Limit: Bonus? Or purely penalty for distraction?
        // User said: "screen time xp is not a normal screen time xp... we apply logic on those app only"
        // Let's keep the Bonus/Penalty structure but apply it to this "Distracted Time"
        
        var signedXP = 0
        
        if (limitMins > 0) {
            // New Linear Formula requested by User: 
            // (Limit - Used) * 3
            // If Used < Limit -> Postive * 3 -> Bonus
            // If Used > Limit -> Negative * 3 -> Penalty
            
            val diff = limitMins - distractedMins
            signedXP = diff * 3
            
            TerminalLogger.log("DistractionXP Calc: Limit=$limitMins, Used=$distractedMins, Diff=$diff, XP=$signedXP (new x3 formula)")
        } else {
            // Fallback if no limit set? 
            // If Usage Limit is 0 (disabled?), maybe 0 XP. 
            // Or if they mean strict "0 minute limit", then (0 - used) * 3 = -Used * 3.
            // Assuming 0 means "Not Configured" -> 0 XP.
            signedXP = 0
            TerminalLogger.log("DistractionXP Calc: No limit set, XP=0")
        }
        
        // Save to DB (Unified)
        updateDailyStats(context, date) { current ->
            current.copy(distractionXP = signedXP, penaltyXP = 0)
        }
        
        return@withContext Pair(signedXP, 0)
    }

    suspend fun calculateSessionXP(context: Context, date: String, externalEvents: List<com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent>? = null): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dateObj = LocalDate.parse(date)
        val startOfDay = dateObj.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = dateObj.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        // 1. Fetch Data
        val plannedEvents = if (externalEvents != null) {
            externalEvents // Use Cloud Events if provided (Nightly)
        } else {
            val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
            calendarRepo.getEventsInRange(startOfDay, endOfDay) // Use Device Events (Live)
        }
        
        val actualSessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
        
        var xp = 0
        
        plannedEvents.forEach { event ->
            val matchingSession = actualSessions.find { session ->
                session.startTime < event.endTime && session.endTime > event.startTime
            }
            
            if (matchingSession != null) {
                xp += 100
                val diffStart = matchingSession.startTime - event.startTime
                val fifteenMins = 15 * 60 * 1000L
                if (diffStart <= -fifteenMins) xp += 50
                else if (diffStart >= fifteenMins) xp -= 50
            } else {
                if (event.endTime < System.currentTimeMillis()) {
                    xp -= 100
                }
            }
        }
        
        updateDailyStats(context, date) { current ->
            current.copy(sessionXP = xp)
        }
        
        return@withContext xp
    }

    /**
     * UNIFIED RECALCULATION:
     * Re-runs ALL XP formulas for a specific date using strict DB data.
     * Trusted source of truth for both Live Refresh and Nightly Protocol.
     * @param externalEvents Optional list of events (e.g. from Cloud API) to override device calendar.
     */
    suspend fun recalculateDailyStats(context: Context, date: String, externalEvents: List<com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent>? = null) {
        calculateTaskXP(context) // Updates taskXP
        calculateSessionXP(context, date, externalEvents) // Updates sessionXP
        calculateScreenTimeXP(context, date) // Updates distractionXP
        
        // Re-sum Tapasya XP
        val db = AppDatabase.getDatabase(context)
        val dateObj = LocalDate.parse(date)
        val startOfDay = dateObj.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = dateObj.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
        
        var totalTapasyaXP = 0
        sessions.forEach { session ->
             totalTapasyaXP += calculateTapasyaXP((session.effectiveTimeMs / 60000).toInt())
        }
        
        updateDailyStats(context, date) { current ->
            current.copy(tapasyaXP = totalTapasyaXP)
        }
        
        // Update Projected XP for today only
        if (date == LocalDate.now().toString()) {
            try {
                val projected = getProjectedDailyXP(context)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putInt("projected_xp", projected.total)
                    .apply()
            } catch (e: Exception) {
                TerminalLogger.log("Projected XP calculation failed: ${e.message}")
            }
        }
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
                         updatedBreakdown.distractionXP + updatedBreakdown.reflectionXP + updatedBreakdown.bonusXP - updatedBreakdown.penaltyXP
                         
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
        
        // Ring Buffer: Archive expired days to vault and delete
        archiveExpiredDays(context)
    }
    
    // Recalculate Total XP, Level, and Streak from DB History
    suspend fun recalculateGlobalStats(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val allStats = db.dailyStatsDao().getAllStats() // Need to ensure DAO has this or use range
        
        var calculatedTotalXP = 0
        var currentStreak = 0
        
        // Calculate Total from live DB + Vault
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val vaultXP = prefs.getInt(PREF_ACCUMULATED_XP, 0)
        allStats.forEach { calculatedTotalXP += it.totalXP }
        
        // Final Total = Vault (Archived) + Live (DB)
        calculatedTotalXP += vaultXP
        
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
                    firstDay.totalEffectiveMinutes > 0 
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

        prefs.edit()
            .putInt(PREF_TOTAL_XP, calculatedTotalXP)
            .putInt(PREF_STREAK, currentStreak)
            .apply()
            
        checkLevelUp(context, calculatedTotalXP)
    }
    
    suspend fun deleteDailyStats(context: Context, date: String) {
        val db = AppDatabase.getDatabase(context)
        val stats = db.dailyStatsDao().getStatsForDate(date)
        
        // Archive this day's XP to vault before deleting (preserves total XP)
        if (stats != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentVault = prefs.getInt(PREF_ACCUMULATED_XP, 0)
            prefs.edit().putInt(PREF_ACCUMULATED_XP, currentVault + stats.totalXP).apply()
            TerminalLogger.log("Archived ${stats.totalXP} XP to vault before deleting $date")
        }
        
        db.dailyStatsDao().deleteStatsForDate(date)
        recalculateGlobalStats(context)
        
        if (date == LocalDate.now().toString()) {
            currentBreakdownCache = null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt("today_xp", 0)
                .apply()
        }
    }
    
    suspend fun enforceRetentionPolicy(context: Context) = withContext(Dispatchers.IO) {
        // 1. Archive expired days BEFORE deleting (preserves total XP in vault)
        archiveExpiredDays(context)
        
        // 2. Now delete is safe - read retention from user preference
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val retentionDays = prefs.getInt(PREF_RETENTION_DAYS, 7) // Use saved setting!
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
        updateDailyStats(context, date) { it.copy(distractionXP = it.distractionXP + xp) }
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
        
        // 1. Archive before delete (The Vault Strategy)
        // Find all rows older than cutoff
        // Note: deleteOldStats usually deletes WHERE date < cutoff
        // We need to SELECT sum(totalXP) WHERE date < cutoff first
        
        // Since DAO might not have sum query exposed, let's fetch all and filter in memory (dataset is small, <365 rows likely)
        val allStats = db.dailyStatsDao().getAllStats()
        val oldStats = allStats.filter { LocalDate.parse(it.date).isBefore(LocalDate.parse(cutoffDate)) }
        
        if (oldStats.isNotEmpty()) {
            val archivedSum = oldStats.sumOf { it.totalXP }
            val currentVault = prefs.getInt(PREF_ACCUMULATED_XP, 0)
            
            // Add to Vault
            prefs.edit().putInt(PREF_ACCUMULATED_XP, currentVault + archivedSum).apply()
            
            // Now safe to delete
            db.dailyStatsDao().deleteOldStats(cutoffDate)
            
            TerminalLogger.log("XPManager: Archived ${oldStats.size} days ($archivedSum XP) to Vault. New Vault Total: ${currentVault + archivedSum}")
        }
        
        // Recalculate to update UI
        recalculateGlobalStats(context)
    }

    /**
     * RING BUFFER: Archive expired days to vault and delete.
     * Tracks last archived date to only archive 1 day at a time.
     */
    private suspend fun archiveExpiredDays(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val retentionDays = prefs.getInt(PREF_RETENTION_DAYS, 7)
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
        
        val vaultLastDateStr = prefs.getString(PREF_VAULT_LAST_DATE, null)
        val vaultLastDate = vaultLastDateStr?.let { 
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }
        
        val db = AppDatabase.getDatabase(context)
        val dao = db.dailyStatsDao()
        val allStats = dao.getAllStats()
        
        // Find days that need archiving:
        // - Date is before cutoff (outside retention window)
        // - Date is after vaultLastDate (not yet archived)
        val toArchive = allStats.filter { stat ->
            val date = LocalDate.parse(stat.date)
            date.isBefore(cutoffDate) && (vaultLastDate == null || date.isAfter(vaultLastDate))
        }
        
        if (toArchive.isNotEmpty()) {
            val xpToArchive = toArchive.sumOf { it.totalXP }
            val currentVault = prefs.getInt(PREF_ACCUMULATED_XP, 0)
            val newestArchivedDate = toArchive.maxOfOrNull { LocalDate.parse(it.date) }
            
            prefs.edit()
                .putInt(PREF_ACCUMULATED_XP, currentVault + xpToArchive)
                .putString(PREF_VAULT_LAST_DATE, newestArchivedDate?.toString())
                .apply()
            
            // Delete archived rows
            dao.deleteOldStats(cutoffDate.toString())
            
            TerminalLogger.log("Ring Buffer: Archived ${toArchive.size} days ($xpToArchive XP) to vault. New vault: ${currentVault + xpToArchive}")
        }
    }

    /**
     * UNIFIED LIVE STATS
     * Single source of truth for UI (Home & Reflection).
     */
    data class GamificationStats(
        val totalXP: Int,
        val todayXP: Int,
        val projectedXP: Int,  // Maximum potential XP for today
        val level: Int,
        val streak: Int,
        val levelName: String,
        val nextLevelXP: Int
    )

    fun getLiveGamificationStats(context: Context): GamificationStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalXP = prefs.getInt(PREF_TOTAL_XP, 0)
        val todayXP = prefs.getInt("today_xp", 0)
        val projectedXP = prefs.getInt("projected_xp", 0)
        val level = prefs.getInt(PREF_LEVEL, 1)
        val streak = prefs.getInt(PREF_STREAK, 0)
        
        val levels = getAllLevels(context)
        val currentLvl = levels.find { it.level == level }
        val nextLvl = levels.find { it.level == level + 1 }
        
        return GamificationStats(
            totalXP = totalXP,
            todayXP = todayXP,
            projectedXP = projectedXP,
            level = level,
            streak = streak,
            levelName = currentLvl?.name ?: "Unknown",
            nextLevelXP = nextLvl?.requiredXP ?: currentLvl?.requiredXP ?: 0
        )
    }
    
    /**
     * PROJECTED XP
     * Shows maximum potential XP if user completes all planned activities today.
     */
    data class ProjectedXP(
        val tapasyaXP: Int,      // Based on planned session time
        val sessionXP: Int,      // +100 per calendar event
        val taskXP: Int,         // +100 per task due today
        val diaryXP: Int,        // Fixed 500 (best case)
        val distractionXP: Int,  // Fixed 0 (best case)
        val total: Int
    )
    
    /**
     * Calculate projected daily XP based on planned activities.
     * Assumes best-case scenario: all tasks completed, no distractions.
     */
    suspend fun getProjectedDailyXP(context: Context): ProjectedXP = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        // 1. TAPASYA XP: Calculate from planned session minutes
        var plannedMins = 0L
        val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
        val plannedEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
        plannedEvents.forEach { event ->
            val durationMins = (event.endTime - event.startTime) / 60000
            plannedMins += durationMins
        }
        val tapasyaXP = calculateTapasyaXP(plannedMins.toInt())
        
        // 2. SESSION XP: +100 per planned calendar event (if all attended)
        val sessionXP = plannedEvents.size * 100
        
        // 3. TASK XP: +100 per task due today (from Google Tasks)
        var taskCount = 0
        try {
            val todayStr = today.toString() // yyyy-MM-dd format
            val taskStats = GoogleTasksManager.getTasksForDate(context, todayStr)
            taskCount = taskStats.pendingCount + taskStats.completedCount // Total due today
        } catch (e: Exception) {
            // Google Tasks not available or not signed in
            TerminalLogger.log("Projected XP: Could not fetch tasks - ${e.message}")
        }
        val taskXP = taskCount * 100
        
        // 4. DIARY XP: Fixed 500 (assuming nightly reflection is completed)
        val diaryXP = 500
        
        // 5. DISTRACTION XP: 0 (best case - stay under screen time limit)
        val distractionXP = 0
        
        val total = tapasyaXP + sessionXP + taskXP + diaryXP + distractionXP
        
        return@withContext ProjectedXP(
            tapasyaXP = tapasyaXP,
            sessionXP = sessionXP,
            taskXP = taskXP,
            diaryXP = diaryXP,
            distractionXP = distractionXP,
            total = total
        )
    }

    // --- JSON Helpers ---

    private fun toJson(bd: XPBreakdown): String {
        return JSONObject().apply {
            put("tapasyaXP", bd.tapasyaXP)
            put("taskXP", bd.taskXP)
            put("sessionXP", bd.sessionXP)
            put("distractionXP", bd.distractionXP)
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
            
            // Unification Logic:
            // Combine legacy bonus/penalty/screenTimeXP fields into unified distractionXP
            val rawScreenTime = obj.optInt("distractionXP", obj.optInt("screenTimeXP", 0))
            val legacyBonus = obj.optInt("bonusXP", 0)
            val legacyPenalty = obj.optInt("penaltyXP", 0)
            
            val unifiedScreenTime = rawScreenTime + legacyBonus - legacyPenalty
            
            XPBreakdown(
                date = date,
                tapasyaXP = obj.optInt("tapasyaXP", 0),
                taskXP = obj.optInt("taskXP", 0),
                sessionXP = obj.optInt("sessionXP", 0),
                distractionXP = unifiedScreenTime, // Unified Field
                reflectionXP = obj.optInt("reflectionXP", 0),
                bonusXP = 0, // Force 0
                penaltyXP = 0, // Force 0
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
    
    // getProjectedDailyXP removed - was using legacy formula
    
    suspend fun performNightlyReflection(context: Context, date: String) {
        // No-op
    }
}
