package com.neubofy.reality.data

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.CalendarEvent
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.google.GoogleDocsManager
import com.neubofy.reality.google.GoogleDriveManager
import com.neubofy.reality.ui.activity.AISettingsActivity
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.XPManager
import com.neubofy.reality.health.HealthManager
// UsageUtils is used via full path com.neubofy.reality.utils.UsageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileInputStream
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.client.http.FileContent
import com.google.gson.Gson
import com.neubofy.reality.google.GoogleAuthManager
import org.json.JSONObject
import org.json.JSONArray
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.data.nightly.NightlyReporter
import com.neubofy.reality.data.nightly.NightlyDataCollector

/**
 * Orchestrates the Nightly Protocol process:
 * 1. Collect day data (calendar events, tapasya sessions)
 * 2. Generate AI reflection questions
 * 3. Check if diary already exists
 * 4. Create Google Doc diary
 */
class NightlyProtocolExecutor(
    private val context: Context,
    private val diaryDate: LocalDate,
    private val listener: NightlyProgressListener
) {
    
    interface NightlyProgressListener {
        fun onStepStarted(step: Int, stepName: String)
        fun onStepCompleted(step: Int, stepName: String, details: String? = null, linkUrl: String? = null)
        fun onStepSkipped(step: Int, stepName: String, reason: String)
        fun onError(step: Int, error: String)
        fun onQuestionsReady(questions: List<String>)
        fun onAnalysisFeedback(feedback: String)
        fun onComplete(diaryDocId: String?, diaryUrl: String?)
    }
    
    // Helpers
    private val reporter = NightlyReporter(context)
    private val dataCollector = NightlyDataCollector(context)

    // Granular Step State Persistence
    data class StepProgress(
        val status: Int, // 0=Pending, 1=Running, 2=Completed, 3=Skipped, 4=Error
        val details: String? = null
    ) {
        companion object {
            const val STATUS_PENDING = 0
            const val STATUS_RUNNING = 1
            const val STATUS_COMPLETED = 2
            const val STATUS_SKIPPED = 3
            const val STATUS_ERROR = 4
        }
    }
    
    companion object {
        // 12-Step Protocol
        const val STEP_FETCH_TASKS = 1          // Google Tasks API
        const val STEP_FETCH_SESSIONS = 2       // Tapasya DB + Calendar
        const val STEP_CALC_SCREEN_TIME = 3     // UsageStats
        const val STEP_GENERATE_QUESTIONS = 4   // AI
        const val STEP_CREATE_DIARY = 5         // Google Docs
        const val STEP_ANALYZE_REFLECTION = 6   // AI
        const val STEP_FINALIZE_XP = 7          // XPManager
        const val STEP_CREATE_PLAN_DOC = 8      // Google Docs
        const val STEP_GENERATE_PLAN = 9        // AI
        const val STEP_PROCESS_PLAN = 10        // Google Tasks + Calendar
        const val STEP_GENERATE_REPORT = 11     // AI -> NightlySession
        const val STEP_GENERATE_PDF = 12        // PDF -> Google Drive

        
        // Legacy aliases (for backward compatibility)
        @Deprecated("Use STEP_FETCH_TASKS") const val STEP_CHECK_DIARY = 1
        @Deprecated("Use STEP_FETCH_SESSIONS") const val STEP_COLLECT_DATA = 2
        @Deprecated("Use STEP_FINALIZE_XP") const val STEP_CALCULATE_XP = 7
        @Deprecated("Use STEP_ANALYZE_REFLECTION") const val STEP_READ_DIARY = 6
        
        // Protocol States
        const val STATE_IDLE = 0
        const val STATE_CREATING = 1
        const val STATE_PENDING_REFLECTION = 2
        const val STATE_ANALYZING = 3
        const val STATE_COMPLETE = 4

        fun mockLoadStepDataForAgent(context: android.content.Context, date: java.time.LocalDate, stepId: Int): String? {
            val prefs = context.getSharedPreferences("nightly_diary_${date}", android.content.Context.MODE_PRIVATE)
            return prefs.getString("step_${stepId}_result", null)
        }
        const val STATE_PLANNING_READY = 5
        
        private const val PREFS_NAME = "nightly_prefs"
        
        // Date-keyed helper functions
        fun getStateKey(date: LocalDate): String = "state_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        fun getDiaryDocIdKey(date: LocalDate): String = "diary_doc_id_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        fun getPlanDocIdKey(date: LocalDate): String = "plan_doc_id_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        fun getPlanVerifiedKey(date: LocalDate): String = "plan_verified_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        
        // Legacy keys for backwards compatibility
        private const val KEY_STATE = "protocol_state"
        private const val KEY_DIARY_DOC_ID = "current_diary_doc_id"
        
        // Get state for a specific date
        suspend fun getStateForDate(context: Context, date: LocalDate): Int {
            return NightlyRepository.getSessionStatus(context, date)
        }
        
        // Get diary doc ID for a specific date
        suspend fun getDiaryDocIdForDate(context: Context, date: LocalDate): String? {
            return NightlyRepository.getDiaryDocId(context, date)
        }
        
        // Granular Step State Persistence

        

        
        // ... (Existing Cleanup & Clear Memory) ...
        

        
        // Cleanup old entries (keep only last 3 days)
        suspend fun cleanupOldEntries(context: Context, activeDate: LocalDate) {
            val today = LocalDate.now()
            val cutoffDate = today.minusDays(2) // 3 days including today: today, yesterday, day before
            
            TerminalLogger.log("Nightly: Strict cleanup - removing data older than $cutoffDate")
            
            // 1. DB Cleanup (Strict)
            NightlyRepository.cleanupOldData(context, cutoffDate)
            
            // 2. Legacy Prefs Cleanup
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Collect keys to remove
            val keysToRemove = prefs.all.keys.filter { key ->
                (key.startsWith("state_") || 
                 key.startsWith("diary_doc_id_") || 
                 key.startsWith("plan_doc_id_") || 
                 key.startsWith("step_state_") || 
                 key.startsWith("step_details_") ||
                 key.startsWith("questions_")) &&
                try {
                    val parts = key.split("_")
                    val datePart = parts.find { it.length == 10 && it.contains("-") }
                    if (datePart != null) {
                        val keyDate = LocalDate.parse(datePart)
                        // EXEMPTION: Never cleanup the date currently being processed by the protocol
                        keyDate.isBefore(cutoffDate) && keyDate != activeDate
                    } else false
                } catch (e: Exception) { false }
            }
            
            keysToRemove.forEach { editor.remove(it) }
            if (keysToRemove.isNotEmpty()) {
                TerminalLogger.log("Nightly: Cleaned up ${keysToRemove.size} old keys")
                editor.apply()
            }
        }
        
        const val DEFAULT_DIARY_TEMPLATE = "# 📔 Daily Reflection Diary - {date}\n\n## 📊 Day Summary Data\n{data}\n\n## 💡 Personalized Questions\n{questions}\n\n## ✍️ My Reflection\n(Write your answers here...)\n"
        const val DEFAULT_PLAN_TEMPLATE = "# My Plan for Tomorrow\n\n## 🎯 Top Priorities\n- [ ] \n\n## 📅 Schedule\n- \n\n## 🚀 Tapasya Focus\n- \n"

        // Clear all protocol memory (States and Diary IDs)
        fun clearMemory(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Remove legacy keys
            editor.remove(KEY_STATE)
            editor.remove(KEY_DIARY_DOC_ID)
            
            // Remove all date-specific keys
            prefs.all.keys.filter { key ->
                key.startsWith("state_") || 
                key.startsWith("diary_doc_id_") ||
                key.startsWith("step_state_") || 
                key.startsWith("step_details_")
            }.forEach { key ->
                editor.remove(key)
            }
            
            editor.apply()
            TerminalLogger.log("Nightly: Memory cleared (All states and diary IDs removed)")
        }
    }
    
    private var daySummary: DaySummary? = null
    private var diaryDocId: String? = null
    
    // Debug/State Data (Restored for Layout Fixes)
    private var reportHtml: String? = null
    private var nightlyStartTime: Long = System.currentTimeMillis()

    // --- State Management Helpers (Delegated to Repository) ---
    // Note: These now delegate to NightlyRepository to avoid duplicating DB logic
    
    private suspend fun loadStepState(context: Context, date: LocalDate, step: Int): StepProgress {
        return com.neubofy.reality.data.repository.NightlyRepository.loadStepState(context, date, step)
    }
    
    private suspend fun loadStepData(context: Context, date: LocalDate, step: Int): com.neubofy.reality.data.repository.StepData {
        return com.neubofy.reality.data.repository.NightlyRepository.loadStepData(context, date, step)
    }

    private suspend fun saveStepState(
        context: Context, 
        date: LocalDate, 
        step: Int, 
        status: Int, 
        details: String?,
        resultJson: String? = null,
        linkUrl: String? = null
    ) {
        com.neubofy.reality.data.repository.NightlyRepository.saveStepState(context, date, step, status, details, resultJson, linkUrl)
    }
    
    // --- Phase 1: Creation ---
    
    suspend fun startCreationPhase() {
        val today = LocalDate.now()
        cleanupOldEntries(context, diaryDate)
        
        try {
            setProtocolState(STATE_CREATING)
            
            // --- Step 1: Check if diary already exists ---
            val checkState = loadStepState(context, diaryDate, STEP_CHECK_DIARY)
            if (checkState.status == StepProgress.STATUS_COMPLETED) {
                // Already checked, try to load ID from prefs
                diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
                listener.onStepCompleted(STEP_CHECK_DIARY, "Diary Check", checkState.details)
            } else {
                checkDiaryExists() // This updates diaryDocId if found
            }
            
            // If diary creation is already complete, skip to end
            val createState = loadStepState(context, diaryDate, STEP_CREATE_DIARY)
            if (createState.status == StepProgress.STATUS_COMPLETED && diaryDocId != null) {
                // VERIFY: Check if file actually exists (persistence resilience)
                // If it's 404, we must reset. If network error, we assume it exists.
                val fileExists = com.neubofy.reality.google.GoogleDriveManager.checkFileExists(context, diaryDocId!!)
                
                if (fileExists) {
                    listener.onStepSkipped(STEP_COLLECT_DATA, "Data Collection", "Skipped (Diary exists)")
                    listener.onStepSkipped(STEP_GENERATE_QUESTIONS, "Questions", "Skipped (Diary exists)")
                    listener.onStepCompleted(STEP_CREATE_DIARY, "Diary Ready", createState.details)
                    setProtocolState(STATE_PENDING_REFLECTION)
                    listener.onComplete(diaryDocId, getDiaryUrl(diaryDocId!!))
                    return
                } else {
                    // File deleted externally! Reset state.
                    TerminalLogger.log("Nightly: Diary file missing! Reseting creation phase.")
                    diaryDocId = null
                    NightlyRepository.saveDiaryDocId(context, diaryDate, null)
                    saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_PENDING, null)
                    // Continue to re-create...
                }
            }
            
            // --- Step 2: Collect data ---
            val dataState = loadStepState(context, diaryDate, STEP_COLLECT_DATA)
            if (dataState.status == StepProgress.STATUS_COMPLETED) {
                // Data was collected previously. 
                // FETCH FRESH DATA silently to ensure we have late-breaking updates (e.g. new tasks completed).
                collectDayDataSilently() 
                // Note: collectDayDataSilently NOW updates the persistent state too!
                val newState = loadStepState(context, diaryDate, STEP_COLLECT_DATA) // Reload fresh state
                listener.onStepCompleted(STEP_COLLECT_DATA, "Data (Refreshed)", newState.details)
            } else {
                collectDayData()
            }
            
            // --- Step 3: Generate Questions ---
            val questionState = loadStepState(context, diaryDate, STEP_GENERATE_QUESTIONS)
            if (questionState.status == StepProgress.STATUS_COMPLETED) {
                // Questions were generated. Load from details if possible, else regenerate.
                // For simplicity, we'll regenerate since we don't persist the list itself.
                if (generatedQuestions.isEmpty()) {
                    generateQuestionsSilently()
                }
                listener.onStepCompleted(STEP_GENERATE_QUESTIONS, "Questions (Cached)", questionState.details)
                listener.onQuestionsReady(generatedQuestions)
            } else {
                generateQuestions()
            }
            
            // --- Step 4: Create Diary (or update existing) ---
            if (createState.status != StepProgress.STATUS_COMPLETED) {
                if (diaryDocId != null) {
                    // Diary doc exists but wasn't marked complete (maybe interrupted). Update it.
                    updateExistingDiary()
                } else {
                    createDiary()
                }
            } else {
                // Already handled above, but for safety:
                diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
                listener.onStepCompleted(STEP_CREATE_DIARY, "Diary Ready", createState.details)
            }
            
            // End of Phase 1
            setProtocolState(STATE_PENDING_REFLECTION)
            listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
            
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    // --- Phase 2: Analysis ---
    
    suspend fun finishAnalysisPhase() {
        try {
            setProtocolState(STATE_ANALYZING)
            diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
            
            if (diaryDocId == null) {
                if (!checkDiaryExists()) {
                     listener.onError(STEP_READ_DIARY, "Diary document not found")
                     setProtocolState(STATE_IDLE)
                     return
                }
            }
            
            // Re-collect data silently if needed
            if (daySummary == null) {
                collectDayDataSilently()
            }
            
            // Check if XP calculation is already complete (entire phase done)
            val xpState = loadStepState(context, diaryDate, STEP_CALCULATE_XP)
            if (xpState.status == StepProgress.STATUS_COMPLETED) {
                // Analysis phase already completed for this date
                listener.onStepSkipped(STEP_READ_DIARY, "Read Diary", "Skipped (Already analyzed)")
                listener.onStepSkipped(STEP_ANALYZE_REFLECTION, "AI Analysis", "Skipped (Already completed)")
                listener.onStepCompleted(STEP_CALCULATE_XP, "XP Calculated", xpState.details)
                setProtocolState(STATE_COMPLETE)
                listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
                return
            }
            
            // Step 5: Read Diary (always run - cheap operation)
            val diaryContent = readDiaryContent()
            if (diaryContent.length < 50) {
                 listener.onError(STEP_READ_DIARY, "Diary seems empty. Please write your reflection.")
                 setProtocolState(STATE_PENDING_REFLECTION)
                 return
            }
            
            // Step 6: AI Analysis - check persistence
            val analysisState = loadStepState(context, diaryDate, STEP_ANALYZE_REFLECTION)
            var qualityXP = 0
            
            if (analysisState.status == StepProgress.STATUS_COMPLETED) {
                // Analysis already done, extract XP from details if possible
                listener.onStepCompleted(STEP_ANALYZE_REFLECTION, "Analysis (Cached)", analysisState.details)
                // Try to parse XP from details like "Quality: 35/50 XP"
                val xpMatch = Regex("(\\d+)/50").find(analysisState.details ?: "")
                qualityXP = xpMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } else {
                // Run AI Analysis
                listener.onStepStarted(STEP_ANALYZE_REFLECTION, "AI Analyzing Reflection")
                saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_RUNNING, "AI Analyzing...")
                
                val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
                val nightlyModel = AISettingsActivity.getNightlyModel(context)
                
                if (nightlyModel.isNullOrEmpty()) {
                    listener.onStepSkipped(STEP_ANALYZE_REFLECTION, "No AI Model", "Skipping analysis")
                    saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_SKIPPED, "No AI Model")
                    qualityXP = 0
                } else {
                    try {
                        val analysis = NightlyAIHelper.analyzeReflection(
                            context, nightlyModel, userIntro, diaryContent
                        )
                        
                        if (!analysis.satisfied) {
                            listener.onAnalysisFeedback(analysis.feedback)
                            setProtocolState(STATE_PENDING_REFLECTION)
                            return
                        }
                        
                        qualityXP = analysis.xp
                        val details = "Quality: ${analysis.xp}/50 XP"
                        listener.onStepCompleted(STEP_ANALYZE_REFLECTION, "Reflection Accepted", details)
                        saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_COMPLETED, details)
                        
                    } catch (e: Exception) {
                        listener.onError(STEP_ANALYZE_REFLECTION, "Analysis Failed: ${e.message}")
                        saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_ERROR, e.message)
                        setProtocolState(STATE_PENDING_REFLECTION)
                        return
                    }
                }
            }
            
            // Step 7: Calculate XP
            step7_finalizeXp()
            
            // --- BREAK: End of Phase 2 ---
            setProtocolState(STATE_PLANNING_READY)
            // Trigger UI update but don't proceed
            // We use onComplete with nulls to signal "Action Required" or just let the state update handle it
            listener.onStepCompleted(STEP_CALCULATE_XP, "Analysis Complete", "Ready for Planning")
            
        } catch (e: Exception) {
            handleError(e)
        }
    }

    suspend fun executePlanningPhase() {
        try {
             // --- Phase 3: Planning ---
            
            // Step 8/9: Create Plan Doc
            createPlanDoc()
            
            // Step 9: Generate AI Plan
            step9_generatePlan()
            
            // Step 10: Process Plan
            processPlanToTasks()
            
            // Step 11: Generate Report (AI Summary)
            step11_generateReport()
            
            // Step 12: Generate PDF Report
            step12_generatePdf()
            
            setProtocolState(STATE_COMPLETE)
            diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
            listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
            
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    private suspend fun setProtocolState(state: Int) {
        NightlyRepository.updateSessionStatus(context, diaryDate, state)
        TerminalLogger.log("Nightly: State updated to $state for date $diaryDate")
    }
    
    private fun handleError(e: Exception) {
        TerminalLogger.log("Nightly: Error: ${e.message}")
        listener.onError(-1, e.message ?: "Unknown error")
    }
    
    private suspend fun collectDayData() {
        listener.onStepStarted(STEP_COLLECT_DATA, "Collecting day data")
        saveStepState(context, diaryDate, STEP_COLLECT_DATA, StepProgress.STATUS_RUNNING, "Collecting...")
        
        try {
            // Delegated to DataCollector
            val taskStats = dataCollector.fetchTasks(diaryDate)
            daySummary = dataCollector.collectDayData(diaryDate, taskStats)
        
            val details = "${daySummary?.calendarEvents?.size ?: 0} events, ${daySummary?.completedSessions?.size ?: 0} sessions, ${(daySummary?.tasksDue?.size ?: 0) + (daySummary?.tasksCompleted?.size ?: 0)} tasks"
            listener.onStepCompleted(STEP_COLLECT_DATA, "Data collected", details)
            saveStepState(context, diaryDate, STEP_COLLECT_DATA, StepProgress.STATUS_COMPLETED, details)
        } catch (e: Exception) { 
             val err = "Data collection failed: ${e.message}"
             listener.onError(STEP_COLLECT_DATA, err)
             saveStepState(context, diaryDate, STEP_COLLECT_DATA, StepProgress.STATUS_ERROR, err)
        }
    }
    
    // ========== 12-STEP PROTOCOL: Granular Data Collection ==========
    
    private var fetchedTasks: com.neubofy.reality.google.GoogleTasksManager.TaskStats? = null
    private var screenTimeMinutes: Int = 0
    private var screenTimeXpDelta: Int = 0
    private var reflectionXp: Int = 0
    private var generatedQuestions: List<String> = emptyList()
    
    private suspend fun step1_fetchTasks() {
        val stepData = loadStepData(context, diaryDate, STEP_FETCH_TASKS)
        
        // If completed, try to restore from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val dueTasks = mutableListOf<String>()
                val completedTasks = mutableListOf<String>()
                
                json.optJSONArray("dueTasks")?.let { arr ->
                    for (i in 0 until arr.length()) dueTasks.add(arr.getString(i))
                }
                json.optJSONArray("completedTasks")?.let { arr ->
                    for (i in 0 until arr.length()) completedTasks.add(arr.getString(i))
                }
                
                fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(
                    dueTasks, completedTasks, 
                    json.optInt("pendingCount", dueTasks.size), 
                    json.optInt("completedCount", completedTasks.size)
                )
                TerminalLogger.log("Nightly: Step 1 restored from DB (${fetchedTasks?.completedTasks?.size} done)")
                return
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 1 JSON parse failed, re-fetching: ${e.message}")
            }
        }
        
        // Fetch fresh from API using Helper
        listener.onStepStarted(STEP_FETCH_TASKS, "Fetching Tasks")
        saveStepState(context, diaryDate, STEP_FETCH_TASKS, StepProgress.STATUS_RUNNING, "Fetching...")
        
        try {
            fetchedTasks = dataCollector.fetchTasks(diaryDate)
            
            val details = "${fetchedTasks?.completedTasks?.size ?: 0} done, ${fetchedTasks?.dueTasks?.size ?: 0} pending"
            
            // Build resultJson
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("date", diaryDate.toString())
                })
                put("output", JSONObject().apply {
                    put("dueTasks", JSONArray(fetchedTasks?.dueTasks ?: emptyList<String>()))
                    put("completedTasks", JSONArray(fetchedTasks?.completedTasks ?: emptyList<String>()))
                    put("pendingCount", fetchedTasks?.pendingCount ?: 0)
                    put("completedCount", fetchedTasks?.completedCount ?: 0)
                })
                // Legacy compatibility
                put("dueTasks", JSONArray(fetchedTasks?.dueTasks ?: emptyList<String>()))
                put("completedTasks", JSONArray(fetchedTasks?.completedTasks ?: emptyList<String>()))
            }.toString()
            
            listener.onStepCompleted(STEP_FETCH_TASKS, "Tasks Fetched", details)
            saveStepState(context, diaryDate, STEP_FETCH_TASKS, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
            listener.onError(STEP_FETCH_TASKS, "Task Fetch Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_FETCH_TASKS, StepProgress.STATUS_ERROR, e.message)
        }
    }
    
    private suspend fun step2_fetchSessions() {
        val stepData = loadStepData(context, diaryDate, STEP_FETCH_SESSIONS)
        
        // If completed, try to restore stats from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val plannedMins = json.optLong("plannedMinutes", 0)
                val effectiveMins = json.optLong("effectiveMinutes", 0)
                val sessionCount = json.optInt("sessionCount", 0)
                val eventCount = json.optInt("eventCount", 0)
                
                // Re-fetch from local DB (fast) to populate full objects for AI use
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                    
                    val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
                    val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
                    val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                    val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)
                    
                    daySummary = DaySummary(
                        date = diaryDate,
                        calendarEvents = calendarEvents,
                        completedSessions = sessions,
                        tasksDue = fetchedTasks?.dueTasks ?: emptyList(),
                        tasksCompleted = fetchedTasks?.completedTasks ?: emptyList(),
                        plannedEvents = plannedEvents,
                        totalPlannedMinutes = plannedMins,
                        totalEffectiveMinutes = effectiveMins
                    )
                }
                TerminalLogger.log("Nightly: Step 2 restored from DB ($sessionCount sessions, $eventCount events)")
                return
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 2 JSON parse failed, re-fetching: ${e.message}")
            }
        }
        
        // Fetch fresh
        listener.onStepStarted(STEP_FETCH_SESSIONS, "Fetching Sessions")
        saveStepState(context, diaryDate, STEP_FETCH_SESSIONS, StepProgress.STATUS_RUNNING, "Fetching...")
        
        try {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                
                val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
                val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
                val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)
                
                val totalPlannedMinutes = calendarEvents.sumOf { (it.endTime - it.startTime) / 60000 }
                val totalEffectiveMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }
                
                daySummary = DaySummary(
                    date = diaryDate,
                    calendarEvents = calendarEvents,
                    completedSessions = sessions,
                    tasksDue = fetchedTasks?.dueTasks ?: emptyList(),
                    tasksCompleted = fetchedTasks?.completedTasks ?: emptyList(),
                    plannedEvents = plannedEvents,
                    totalPlannedMinutes = totalPlannedMinutes,
                    totalEffectiveMinutes = totalEffectiveMinutes
                )
            }
            
            val details = "${daySummary?.completedSessions?.size ?: 0} sessions, ${daySummary?.calendarEvents?.size ?: 0} events"
            
            // Build resultJson
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                   put("date", diaryDate.toString())
                })
                put("output", JSONObject().apply {
                    put("sessionCount", daySummary?.completedSessions?.size ?: 0)
                    put("eventCount", daySummary?.calendarEvents?.size ?: 0)
                    put("plannedMinutes", daySummary?.totalPlannedMinutes ?: 0)
                    put("effectiveMinutes", daySummary?.totalEffectiveMinutes ?: 0)
                })
                // Legacy compatibility
                put("plannedMinutes", daySummary?.totalPlannedMinutes ?: 0)
                put("effectiveMinutes", daySummary?.totalEffectiveMinutes ?: 0)
                put("sessionCount", daySummary?.completedSessions?.size ?: 0)
                put("eventCount", daySummary?.calendarEvents?.size ?: 0)
            }.toString()
            
            listener.onStepCompleted(STEP_FETCH_SESSIONS, "Sessions Fetched", details)
            saveStepState(context, diaryDate, STEP_FETCH_SESSIONS, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            listener.onError(STEP_FETCH_SESSIONS, "Session Fetch Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_FETCH_SESSIONS, StepProgress.STATUS_ERROR, e.message)
        }
    }
    
    private suspend fun step3_calcScreenTime() {
        val stepData = loadStepData(context, diaryDate, STEP_CALC_SCREEN_TIME)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            // Restore local calculation results if needed for other steps
             if (stepData.resultJson != null) {
                try {
                    val json = JSONObject(stepData.resultJson)
                    screenTimeMinutes = json.optInt("usedMinutes", 0)
                    screenTimeXpDelta = json.optInt("xpDelta", 0)
                } catch (e: Exception) { }
            }
            return
        }
        
        listener.onStepStarted(STEP_CALC_SCREEN_TIME, "Calculating Health & Screen Time")
        saveStepState(context, diaryDate, STEP_CALC_SCREEN_TIME, StepProgress.STATUS_RUNNING, "Analyzing Usage...")
        
        try {
            val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
            val limitMinutes = prefs.getInt("screen_time_limit_minutes", 0)
            
            // 1. Fetch DIGITAL Usage (Screen Time, Unlocks, Streak) for specific Diary Date
            // 1. Fetch DIGITAL Usage (Screen Time, Unlocks, Streak) for specific Diary Date
            val usageMetrics = com.neubofy.reality.utils.UsageUtils.getProUsageMetrics(context, diaryDate)
            
            screenTimeMinutes = (usageMetrics.screenTimeMs / 60000).toInt()
            val unlocks = usageMetrics.pickupCount
            val streakMins = (usageMetrics.longestStreakMs / 60000).toInt()
            
            // 2. Fetch PHYSICAL Health (Steps, Sleep) via HealthManager
            // Note: HealthManager might require permissions. We handle gracefully if 0/empty.
            val healthManager = HealthManager(context)
            val steps = try { healthManager.getSteps(diaryDate) } catch (e: Exception) { 0L }
            val sleepInfo = try { healthManager.getSleep(diaryDate) } catch (e: Exception) { "No data" }
            val sleepMins = if (sleepInfo.contains("h")) {
                // simple parse estimation or just pass string? Let's pass string to report, store raw?
                // For JSON data, maybe 0 if complexity. Let's just store the string representation for AI.
                0 
            } else 0
            
            // 3. Calculate Reality Ratio
            // If today: use minutes since midnight. If past: use 1440 (24h).
            val totalMinutesAvailable = if (diaryDate == LocalDate.now()) {
                val now = java.time.LocalTime.now()
                (now.hour * 60) + now.minute
            } else {
                1440
            }
            
            val phonelessMinutes = (totalMinutesAvailable - screenTimeMinutes).coerceAtLeast(0)
            val realityRatio = if (totalMinutesAvailable > 0) {
                (phonelessMinutes * 100) / totalMinutesAvailable
            } else 0
            
            // 4. Calculate XP (Bonus/Penalty)
            if (limitMinutes > 0) {
                if (screenTimeMinutes > limitMinutes) {
                    val over = screenTimeMinutes - limitMinutes
                    screenTimeXpDelta = -(over * 10).coerceAtMost(500)
                } else {
                    val left = limitMinutes - screenTimeMinutes
                    screenTimeXpDelta = (left * 10).coerceAtMost(500)
                }
            }
            
            val details = "${screenTimeMinutes}m • $unlocks Unlocks • $realityRatio% Reality"
            
            // Build resultJson with ALL metrics
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("limitMinutes", limitMinutes)
                    put("date", diaryDate.toString())
                })
                put("output", JSONObject().apply {
                    put("usedMinutes", screenTimeMinutes)
                    put("limitMinutes", limitMinutes)
                    put("xpDelta", screenTimeXpDelta)
                    put("unlocks", unlocks)
                    put("streakMinutes", streakMins)
                    put("phonelessMinutes", phonelessMinutes)
                    put("realityRatio", realityRatio)
                    put("steps", steps)
                    put("sleepInfo", sleepInfo)
                })
                // Legacy compatibility
                put("usedMinutes", screenTimeMinutes)
                put("xpDelta", screenTimeXpDelta)
            }.toString()
            
            listener.onStepCompleted(STEP_CALC_SCREEN_TIME, "Health Metrics Calculated", details)
            saveStepState(context, diaryDate, STEP_CALC_SCREEN_TIME, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            listener.onError(STEP_CALC_SCREEN_TIME, "Health Calc Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_CALC_SCREEN_TIME, StepProgress.STATUS_ERROR, e.message)
        }
    }
    
    // Silent version - no UI updates, just populates daySummary for AI context
    private suspend fun collectDayDataSilently(): DaySummary {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            
            val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
            val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
            
            val dateString = diaryDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val taskStats = try {
                fetchedTasks ?: com.neubofy.reality.google.GoogleTasksManager.getTasksForDate(context, dateString)
            } catch (e: Exception) {
                com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
            }
            
            val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
            val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)
            
            val totalPlannedMinutes = calendarEvents.sumOf { (it.endTime - it.startTime) / 60000 }
            val totalEffectiveMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }
            
            val summary = DaySummary(
                date = diaryDate,
                calendarEvents = calendarEvents,
                completedSessions = sessions,
                tasksDue = taskStats.dueTasks,
                tasksCompleted = taskStats.completedTasks,
                plannedEvents = plannedEvents,
                totalPlannedMinutes = totalPlannedMinutes,
                totalEffectiveMinutes = totalEffectiveMinutes
            )
            daySummary = summary
            summary
        }
    }
    
    // Silent version - regenerates questions without UI updates
    private suspend fun generateQuestionsSilently() {
        val summary = daySummary ?: return
        val userIntro = AISettingsActivity.getUserIntroduction(context)
        val nightlyModel = AISettingsActivity.getNightlyModel(context)
        
        if (nightlyModel.isNullOrEmpty()) {
            generatedQuestions = getFallbackQuestions()
            return
        }
        
        // Prepare Health Data from Step 3 (reuse logic)
        var healthDataStr = "No health data collected."
        val step3Data = loadStepData(context, diaryDate, STEP_CALC_SCREEN_TIME)
        if (step3Data.resultJson != null) {
            try {
                val json = JSONObject(step3Data.resultJson)
                val used = json.optInt("usedMinutes", 0)
                val unlocks = json.optInt("unlocks", 0)
                val steps = json.optLong("steps", 0)
                val sleep = json.optString("sleepInfo", "No data")
                val ratio = json.optInt("realityRatio", 0)
                val streak = json.optInt("streakMinutes", 0)
                
                healthDataStr = """
                    - Screen Time: ${used / 60}h ${used % 60}m
                    - Reality Ratio: $ratio% (Phoneless Time)
                    - Unlocks: $unlocks
                    - Longest Focus Streak: ${streak / 60}h ${streak % 60}m
                    - Steps: $steps
                    - Sleep: $sleep
                """.trimIndent()
            } catch (e: Exception) { }
        }

        try {
            generatedQuestions = NightlyAIHelper.generateQuestions(
                context = context,
                modelString = nightlyModel,
                userIntroduction = userIntro ?: "",
                daySummary = summary,
                healthData = healthDataStr
            )
        } catch (e: Exception) {
            generatedQuestions = getFallbackQuestions()
        }
    }
    
    // Update existing diary document with new content
    private suspend fun updateExistingDiary() {
        listener.onStepStarted(STEP_CREATE_DIARY, "Updating existing diary")
        saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_RUNNING, "Updating...")
        
        val summary = daySummary ?: run {
            val err = "No day data available"
            listener.onError(STEP_CREATE_DIARY, err)
            saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, err)
            return
        }
        
        val docId = diaryDocId ?: run {
            val err = "No diary ID found"
            listener.onError(STEP_CREATE_DIARY, err)
            saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, err)
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Clear existing content and append new
                val content = buildDiaryContent(summary, generatedQuestions)
                
                // For simplicity, we won't clear - just append a new section
                // This preserves user's existing reflection if any
                GoogleDocsManager.appendText(context, docId, "\n\n--- Updated Content ---\n$content")
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(getDiaryDocIdKey(diaryDate), docId)
                    .putString(KEY_DIARY_DOC_ID, docId)
                    .apply()
                
                val diaryTitle = getDiaryTitle()
                listener.onStepCompleted(STEP_CREATE_DIARY, "Diary updated", diaryTitle)
                saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_COMPLETED, diaryTitle)
                
            } catch (e: Exception) {
                val err = "Update failed: ${e.message}"
                listener.onError(STEP_CREATE_DIARY, err)
                saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, err)
            }
        }
    }
    
    private suspend fun step4_generateQuestions() {
        val stepData = loadStepData(context, diaryDate, STEP_GENERATE_QUESTIONS)
        
        // If completed, restore questions from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val questionsArr = json.optJSONArray("questions")
                if (questionsArr != null && questionsArr.length() > 0) {
                    val questions = mutableListOf<String>()
                    for (i in 0 until questionsArr.length()) {
                        questions.add(questionsArr.getString(i))
                    }
                    generatedQuestions = questions
                    TerminalLogger.log("Nightly: Step 4 restored ${questions.size} questions from DB")
                    listener.onQuestionsReady(generatedQuestions)
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 4 JSON parse failed, regenerating: ${e.message}")
            }
        }
        
        // Ensure data is ready (silently)
        if (daySummary == null) collectDayDataSilently()
        
        generateQuestions() 
    }

    private suspend fun step5_createDiary() {
        val stepData = loadStepData(context, diaryDate, STEP_CREATE_DIARY)
        
        // If completed, restore diaryDocId from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val savedDocId = json.optString("docId")
                val savedUrl = json.optString("docUrl")
                
                if (savedDocId.isNotEmpty()) {
                    diaryDocId = savedDocId
                    TerminalLogger.log("Nightly: Step 5 restored diaryDocId from DB: $savedDocId")
                    // Notify UI with the link
                    if (savedUrl != null) {
                        listener.onStepCompleted(STEP_CREATE_DIARY, "Diary Ready", "Restored", savedUrl)
                    }
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 5 JSON parse failed: ${e.message}")
            }
        }
        
        // Not completed yet - create new diary (no duplicate checking to save API calls)
        createDiary()
    }

    // ... (Step 6-12 placeholders remain) ...

    private suspend fun generateQuestions() {
        listener.onStepStarted(STEP_GENERATE_QUESTIONS, "Generating reflection questions")
        saveStepState(context, diaryDate, STEP_GENERATE_QUESTIONS, StepProgress.STATUS_RUNNING, "Generating questions...")
        
        val summary = daySummary ?: run {
             collectDayDataSilently()
             daySummary ?: run {
                val err = "No day data collected"
                listener.onError(STEP_GENERATE_QUESTIONS, err)
                saveStepState(context, diaryDate, STEP_GENERATE_QUESTIONS, StepProgress.STATUS_ERROR, err)
                return
             }
        }
        
        val userIntro = AISettingsActivity.getUserIntroduction(context)
        val nightlyModel = AISettingsActivity.getNightlyModel(context)
        
        if (nightlyModel.isNullOrEmpty()) {
             // Fallback to offline questions
             generatedQuestions = getFallbackQuestions()
             val details = "Offline (No AI Model)"
             listener.onStepCompleted(STEP_GENERATE_QUESTIONS, "Questions Ready", details)
             saveStepState(context, diaryDate, STEP_GENERATE_QUESTIONS, StepProgress.STATUS_COMPLETED, details)
             return
        }
        
        // Prepare Health Data from Step 3
        var healthDataStr = "No health data collected."
        val step3Data = loadStepData(context, diaryDate, STEP_CALC_SCREEN_TIME)
        if (step3Data.resultJson != null) {
            try {
                val json = JSONObject(step3Data.resultJson)
                val used = json.optInt("usedMinutes", 0)
                val unlocks = json.optInt("unlocks", 0)
                val steps = json.optLong("steps", 0)
                val sleep = json.optString("sleepInfo", "No data")
                val ratio = json.optInt("realityRatio", 0)
                val streak = json.optInt("streakMinutes", 0)
                
                healthDataStr = """
                    - Screen Time: ${used / 60}h ${used % 60}m
                    - Reality Ratio: $ratio% (Phoneless Time)
                    - Unlocks: $unlocks
                    - Longest Focus Streak: ${streak / 60}h ${streak % 60}m
                    - Steps: $steps
                    - Sleep: $sleep
                """.trimIndent()
            } catch (e: Exception) {
                healthDataStr = "Error parsing health data."
            }
        }
        
        try {
            generatedQuestions = NightlyAIHelper.generateQuestions(
                context = context,
                modelString = nightlyModel,
                userIntroduction = userIntro ?: "",
                daySummary = summary,
                healthData = healthDataStr
            )
            listener.onQuestionsReady(generatedQuestions)
            
            // Build resultJson for persistence
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("daySummarySnippet", summary.toString().take(1000))
                    put("healthDataSnippet", healthDataStr.take(1000))
                    put("model", nightlyModel)
                    put("userIntro", userIntro?.take(500))
                })
                put("output", JSONObject().apply {
                    put("questions", JSONArray(generatedQuestions))
                    put("count", generatedQuestions.size)
                    put("source", "ai")
                })
                // Legacy compatibility
                put("questions", JSONArray(generatedQuestions))
            }.toString()
            
            val details = "${generatedQuestions.size} AI Questions"
            listener.onStepCompleted(STEP_GENERATE_QUESTIONS, "Questions Ready", details)
            saveStepState(context, diaryDate, STEP_GENERATE_QUESTIONS, StepProgress.STATUS_COMPLETED, details, resultJson)
            
        } catch (e: Exception) {
            generatedQuestions = getFallbackQuestions()
            
            // Save fallback questions too
            val resultJson = JSONObject().apply {
                put("questions", JSONArray(generatedQuestions))
                put("count", generatedQuestions.size)
                put("source", "fallback")
                put("error", e.message)
            }.toString()
            
            val details = "Fallback (AI Error: ${e.message})"
            listener.onStepCompleted(STEP_GENERATE_QUESTIONS, "Questions Ready", details)
            saveStepState(context, diaryDate, STEP_GENERATE_QUESTIONS, StepProgress.STATUS_COMPLETED, details, resultJson)
        }
    }

    private suspend fun createDiary() {
        listener.onStepStarted(STEP_CREATE_DIARY, "Creating diary document")
        saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_RUNNING, "Creating...")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var diaryFolderId = prefs.getString("diary_folder_id", null)
        val realityFolderId = prefs.getString("reality_folder_id", null)

        // Dynamic Folder Resolution for Diary
        if (diaryFolderId.isNullOrEmpty() && !realityFolderId.isNullOrEmpty()) {
            val credential = GoogleAuthManager.getGoogleAccountCredential(context)
            if (credential != null) {
                val driveService = Drive.Builder(GoogleAuthManager.getHttpTransport(), GoogleAuthManager.getJsonFactory(), credential).setApplicationName("Reality").build()
                val folders = withContext(Dispatchers.IO) {
                    val result = driveService.files().list()
                        .setQ("'$realityFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                        .setOrderBy("name")
                        .setFields("files(id, name)")
                        .execute()
                    result.files ?: emptyList<com.google.api.services.drive.model.File>()
                }
                if (folders.isNotEmpty()) {
                    diaryFolderId = folders[0].id // 1st folder for Diary
                    TerminalLogger.log("Nightly: Dynamic Diary folder resolved: ${folders[0].name}")
                }
            }
        }
        
        if (diaryFolderId.isNullOrEmpty()) {
            val err = "Diary folder not configured"
            listener.onError(STEP_CREATE_DIARY, err)
            saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, err)
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Prepare content using Template
                // 1. Get Template
                val template = prefs.getString("template_diary", DEFAULT_DIARY_TEMPLATE) ?: DEFAULT_DIARY_TEMPLATE
                
                // 2. Format Variables
                val dateStr = diaryDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                
                // Ensure questions are available by loading from Step 4 result if memory is empty
                if (generatedQuestions.isEmpty()) {
                    val step4Data = loadStepData(context, diaryDate, STEP_GENERATE_QUESTIONS)
                    if (step4Data.status == StepProgress.STATUS_COMPLETED && step4Data.resultJson != null) {
                        try {
                            val json = JSONObject(step4Data.resultJson)
                            val questionsArr = json.optJSONArray("questions")
                            if (questionsArr != null) {
                                val questions = mutableListOf<String>()
                                for (i in 0 until questionsArr.length()) questions.add(questionsArr.getString(i))
                                generatedQuestions = questions
                            }
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
                
                // If STILL empty, use fallbacks
                if (generatedQuestions.isEmpty()) {
                    generatedQuestions = getFallbackQuestions()
                }
                
                val summary = daySummary ?: run {
                     collectDayDataSilently()
                     daySummary!!
                }
                
                // Use unified builder
                val content = buildDiaryContent(summary, generatedQuestions)
                
                val diaryTitle = getDiaryTitle()
                
                // 4. Search or Create
                // Check if doc ID is already saved
                var docId = prefs.getString(getDiaryDocIdKey(diaryDate), null)
                
                // If not in prefs, search in folder
                if (docId == null) {
                    docId = GoogleDriveManager.searchFile(context, diaryTitle, diaryFolderId)
                    if (docId != null) {
                         TerminalLogger.log("Nightly: Found existing diary '$diaryTitle' (ID: $docId)")
                         // Save recovered ID IMMEDIATELY
                         prefs.edit().putString(getDiaryDocIdKey(diaryDate), docId).apply()
                         NightlyRepository.saveDiaryDocId(context, diaryDate, docId)
                    }
                }
                
                val processedUrl: String
                 
                if (docId != null) {
                    // Update existing
                    // Logic: If content is just template (contains {data} or {stats}) or very short, overwrite/append.
                    val currentContent = GoogleDocsManager.getDocumentContent(context, docId) ?: ""
                    
                    // Check if "raw template" exists in content
                    val hasRawTemplate = currentContent.contains("{data}") || currentContent.contains("{questions}")
                    
                    if (currentContent.length < 50 || hasRawTemplate) {
                         // Append (or ideally replace, but API is append-only for now)
                         GoogleDocsManager.appendText(context, docId, "\n\n" + content)
                         TerminalLogger.log("Nightly: Injected data into existing diary (was empty or raw).")
                    } else {
                         TerminalLogger.log("Nightly: Diary exists and seems populated. Skipping injection.")
                    }
                    processedUrl = "https://docs.google.com/document/d/$docId"
                     
                } else {
                    // Create New
                    docId = GoogleDocsManager.createDocument(context, diaryTitle)
                    if (docId != null) {
                        GoogleDriveManager.moveFileToFolder(context, docId, diaryFolderId)
                        GoogleDocsManager.appendText(context, docId, content)
                        processedUrl = "https://docs.google.com/document/d/$docId"
                        
                        // Save ID to *both* keys for safety
                        prefs.edit()
                            .putString(getDiaryDocIdKey(diaryDate), docId)
                            .putString(KEY_DIARY_DOC_ID, docId) // Legacy/Current key
                            .apply()
                        
                        // Save to Repository for Step 11 recovery
                        NightlyRepository.saveDiaryDocId(context, diaryDate, docId)
                    } else {
                         throw IllegalStateException("Failed to create document ID")
                    }
                }
                
                // 5. Finish
                diaryDocId = docId
                
                // Build resultJson for persistence
                val resultJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("title", diaryTitle)
                        put("folderId", diaryFolderId)
                        put("questionsCount", generatedQuestions.size)
                    })
                    put("output", JSONObject().apply {
                        put("docId", docId)
                        put("docUrl", processedUrl)
                    })
                    // Legacy compatibility
                    put("docId", docId)
                    put("docUrl", processedUrl)
                    put("title", diaryTitle)
                }.toString()
                
                listener.onStepCompleted(STEP_CREATE_DIARY, "Diary Ready", diaryTitle, processedUrl)
                saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_COMPLETED, diaryTitle, resultJson, processedUrl) 
                
            } catch (e: Exception) {
                listener.onError(STEP_CREATE_DIARY, e.message ?: "Failed to create diary")
                saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, e.message)
            }
        }
    }
    
    private suspend fun readDiaryContent(): String {
        listener.onStepStarted(STEP_READ_DIARY, "Reading diary for analysis")
        saveStepState(context, diaryDate, STEP_READ_DIARY, StepProgress.STATUS_RUNNING, "Reading...")
        
        return withContext(Dispatchers.IO) {
            if (diaryDocId == null) {
                // 1. Try Repository (New source of truth)
                diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
                
                // 2. FALLBACK: Try SharedPreferences
                if (diaryDocId == null) {
                    diaryDocId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(getDiaryDocIdKey(diaryDate), null)
                }
                
                // 3. DOUBLE FALLBACK: Search in Drive
                if (diaryDocId == null) {
                     val diaryTitle = getDiaryTitle()
                     val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                     val folderId = prefs.getString("diary_folder_id", null)
                     if (folderId != null) {
                         diaryDocId = GoogleDriveManager.searchFile(context, diaryTitle, folderId)
                         if (diaryDocId != null) {
                             prefs.edit().putString(getDiaryDocIdKey(diaryDate), diaryDocId).apply()
                             NightlyRepository.saveDiaryDocId(context, diaryDate, diaryDocId)
                         }
                     }
                }

                if (diaryDocId == null) {
                    saveStepState(context, diaryDate, STEP_READ_DIARY, StepProgress.STATUS_ERROR, "No Doc ID")
                    throw IllegalStateException("No diary ID found")
                }
            }
            
            try {
                val content = GoogleDocsManager.getDocumentContent(context, diaryDocId!!) 
                    ?: throw IllegalStateException("Failed to read document")
                    
                listener.onStepCompleted(STEP_READ_DIARY, "Diary Read", "${content.length} chars")
                saveStepState(context, diaryDate, STEP_READ_DIARY, StepProgress.STATUS_COMPLETED, "Read ${content.length} chars")
                content
            } catch (e: Exception) {
                saveStepState(context, diaryDate, STEP_READ_DIARY, StepProgress.STATUS_ERROR, e.message)
                throw e
            }
        }
    }

    /**
     * Step 7: Calculate XP, Streak, and Level
     */
    private suspend fun step6_analyzeReflection() {
        val stepData = loadStepData(context, diaryDate, STEP_ANALYZE_REFLECTION)
        
        // If completed, restore reflectionXp from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val accepted = json.optBoolean("accepted", false)
                if (accepted) {
                    reflectionXp = json.optInt("xp", 0)
                    TerminalLogger.log("Nightly: Step 6 restored reflectionXp from DB: $reflectionXp")
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 6 JSON parse failed: ${e.message}")
            }
        }
        
        listener.onStepStarted(STEP_ANALYZE_REFLECTION, "Analyzing Reflection")
        saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_RUNNING, "Reading Diary...")
        
        try {
            // 1. Read Diary
            val content = readDiaryContent()
            if (content.isEmpty()) {
                throw IllegalStateException("Diary is empty")
            }
            
            saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_RUNNING, "AI Analyzing...")
            
            // 2. AI Analysis
            val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
            val nightlyModel = AISettingsActivity.getNightlyModel(context) ?: "Gemini: gemini-pro"
            
            val result = NightlyAIHelper.analyzeReflection(
                context = context,
                modelString = nightlyModel,
                userIntroduction = userIntro,
                diaryContent = content
            )
            
            if (result.satisfied) {
                // 3. Success
                reflectionXp = result.xp
                val details = "Accepted! XP: ${result.xp}. \"${result.feedback}\""
                
                // CRITICAL: Save directly to XPManager (Daily Stats) AND NightlyRepository (Session Log)
                // This ensures Step 7 (Finalize) reads the correct value from DB.
                XPManager.setReflectionXP(context, result.xp, diaryDate.toString())
                NightlyRepository.setReflectionXp(context, diaryDate, result.xp)
                
                // Build resultJson for persistence
                val resultJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("diarySnippet", content.take(1000) + (if (content.length > 1000) "..." else ""))
                        put("model", nightlyModel)
                    })
                    put("output", JSONObject().apply {
                        put("accepted", true)
                        put("xp", result.xp)
                        put("feedback", result.feedback)
                    })
                    // Legacy compatibility
                    put("accepted", true)
                    put("xp", result.xp)
                    put("feedback", result.feedback)
                }.toString()
                
                listener.onStepCompleted(STEP_ANALYZE_REFLECTION, "Reflection Accepted", details)
                saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_COMPLETED, details, resultJson)
                    
            } else {
                // 4. Rejection - Mark as ERROR so Retry button appears
                // Don't save resultJson so step doesn't appear "completed"
                val errorDetails = "Rejected: ${result.feedback}"
                saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_ERROR, errorDetails)
                listener.onAnalysisFeedback(result.feedback)
            }
            
        } catch (e: Exception) {
            listener.onError(STEP_ANALYZE_REFLECTION, "Analysis Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_ANALYZE_REFLECTION, StepProgress.STATUS_ERROR, e.message)
        }
    }

    private suspend fun step7_finalizeXp() {
        val stepData = loadStepData(context, diaryDate, STEP_FINALIZE_XP)
        if (stepData.status == StepProgress.STATUS_COMPLETED) return
        
        listener.onStepStarted(STEP_FINALIZE_XP, "Finalizing XP & Streak")
        saveStepState(context, diaryDate, STEP_FINALIZE_XP, StepProgress.STATUS_RUNNING, "Calculating...")
        
        try {
            // UNIFICATION STRATEGY: 
            // 1. Fetch Cloud Events (Nightly uses Cloud API as source of truth for Plan).
            //    We filter out All-Day/Family keys internally in GoogleCalendarManager.
            
            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            
            val cloudEvents = com.neubofy.reality.google.GoogleCalendarManager.getEvents(context, startOfDay, endOfDay)
            
            // Map to Internal Format
            val mappedEvents = cloudEvents.map { event ->
                com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent(
                    id = 0, // Not needed for logic
                    title = event.summary ?: "No Title",
                    description = event.description,
                    startTime = event.start.dateTime.value,
                    endTime = event.end.dateTime.value,
                    color = 0,
                    location = event.location
                )
            }
            
            TerminalLogger.log("Nightly Step 7: Fetched ${mappedEvents.size} cloud events for XP calculation.")
        
            // 2. Force recalculation of ALL stats using Cloud Events for Session XP
            // This ensures Nightly Report is authoritative.
            XPManager.recalculateDailyStats(context, diaryDate.toString(), externalEvents = mappedEvents)
            
            // Get Updated Stats to display in report
            val finalStats = XPManager.getDailyStats(context, diaryDate.toString())
                ?: throw IllegalStateException("Failed to retrieve stats after calc")
            
            // Build resultJson
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("cloudEventsCount", mappedEvents.size)
                    put("cloudEventsSummary", mappedEvents.joinToString { it.title }.take(500))
                })
                put("output", JSONObject().apply {
                    put("diaryXp", 50) 
                    put("reflectionXp", finalStats.reflectionXP)
                    put("sessionXp", finalStats.sessionXP)
                    put("tapasyaXp", finalStats.tapasyaXP)
                    put("taskXp", finalStats.taskXP)
                    put("screenTimeXp", finalStats.screenTimeXP) 
                    put("penaltyXp", finalStats.penaltyXP)
                    put("totalXp", finalStats.totalDailyXP)
                })
                // Legacy compatibility
                put("totalXp", finalStats.totalDailyXP)
            }.toString()
            
            val details = "XP Finalized: +${finalStats.totalDailyXP}"
            listener.onStepCompleted(STEP_FINALIZE_XP, "Day Complete", details)
            saveStepState(context, diaryDate, STEP_FINALIZE_XP, StepProgress.STATUS_COMPLETED, details, resultJson)
            
        } catch (e: Exception) {
            listener.onError(STEP_FINALIZE_XP, "XP Finalization Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_FINALIZE_XP, StepProgress.STATUS_ERROR, e.message)
        }
    }

    private suspend fun createPlanDoc() {
        listener.onStepStarted(STEP_CREATE_PLAN_DOC, "Creating Plan Document")
        saveStepState(context, diaryDate, STEP_CREATE_PLAN_DOC, StepProgress.STATUS_RUNNING, "Creating...")
        
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var planFolderId = prefs.getString("plan_folder_id", null) 
            val diaryFolderId = prefs.getString("diary_folder_id", null)
            val realityFolderId = prefs.getString("reality_folder_id", null)

            // Dynamic Folder Resolution if not set
            if (planFolderId.isNullOrEmpty() && !realityFolderId.isNullOrEmpty()) {
                val credential = GoogleAuthManager.getGoogleAccountCredential(context)
                if (credential != null) {
                    val driveService = Drive.Builder(GoogleAuthManager.getHttpTransport(), GoogleAuthManager.getJsonFactory(), credential).setApplicationName("Reality").build()
                    val folders = withContext(Dispatchers.IO) {
                        val result = driveService.files().list()
                            .setQ("'$realityFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                            .setOrderBy("name")
                            .setFields("files(id, name)")
                            .execute()
                        result.files ?: emptyList<com.google.api.services.drive.model.File>()
                    }
                    if (folders.size >= 2) {
                        planFolderId = folders[1].id // 2nd folder for Plans
                        TerminalLogger.log("Nightly: Dynamic Plan folder resolved: ${folders[1].name}")
                    }
                }
            }
            
            if (planFolderId.isNullOrEmpty()) planFolderId = diaryFolderId // Fallback to diary if still null
            
            val title = "Plan - ${diaryDate.plusDays(1).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"
            val template = prefs.getString("template_plan", DEFAULT_PLAN_TEMPLATE) ?: DEFAULT_PLAN_TEMPLATE
            // Handle common variable replacements just in case
            val content = template
                .replace("{date}", diaryDate.plusDays(1).format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
                .replace("{data}", "[Plan Details]") // Placeholder if user uses {data} here too
            
            // Search or Create Logic
            var planId = prefs.getString(getPlanDocIdKey(diaryDate), null)
            
            if (planId == null) {
                // Try search in folder
                 if (planFolderId != null) {
                    planId = GoogleDriveManager.searchFile(context, title, planFolderId)
                    if (planId != null) {
                        TerminalLogger.log("Nightly: Found existing plan '$title' (ID: $planId)")
                        prefs.edit().putString(getPlanDocIdKey(diaryDate), planId).apply()
                        NightlyRepository.savePlanDocId(context, diaryDate, planId)
                    }
                 }
            }
            
            val docUrl: String
            
            if (planId != null) {
                // Check content if recovered
                val currentContent = GoogleDocsManager.getDocumentContent(context, planId) ?: ""
                val hasRawTemplate = currentContent.contains("{date}") || currentContent.contains("{data}")
                
                if (currentContent.length < 50 || hasRawTemplate) {
                     GoogleDocsManager.appendText(context, planId, "\n" + content)
                     TerminalLogger.log("Nightly: Injected template into existing empty/raw plan.")
                }
                docUrl = "https://docs.google.com/document/d/$planId"
                
            } else {
                // Create New
                planId = GoogleDocsManager.createDocument(context, title)
                if (planId != null) {
                    if (planFolderId != null) {
                        GoogleDriveManager.moveFileToFolder(context, planId, planFolderId)
                    }
                    GoogleDocsManager.appendText(context, planId, content)
                    docUrl = "https://docs.google.com/document/d/$planId"
                    
                    prefs.edit().putString(getPlanDocIdKey(diaryDate), planId).apply()
                    NightlyRepository.savePlanDocId(context, diaryDate, planId)
                } else {
                    throw IllegalStateException("Failed to create Plan Doc ID")
                }
            }
            
            // Build resultJson for persistence
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("title", title)
                    put("folderId", planFolderId)
                })
                put("output", JSONObject().apply {
                    put("docId", planId)
                    put("docUrl", docUrl)
                })
                // Legacy compatibility
                put("docId", planId)
                put("docUrl", docUrl)
                put("title", title)
            }.toString()
            
            listener.onStepCompleted(STEP_CREATE_PLAN_DOC, "Plan Doc Ready", "Ready to Edit", docUrl)
            saveStepState(context, diaryDate, STEP_CREATE_PLAN_DOC, StepProgress.STATUS_COMPLETED, "Ready to Edit", resultJson, docUrl)
            
        } catch (e: Exception) {
            listener.onError(STEP_CREATE_PLAN_DOC, "Failed to create plan: ${e.message}")
            saveStepState(context, diaryDate, STEP_CREATE_PLAN_DOC, StepProgress.STATUS_ERROR, e.message)
            // Throwing might be aggressive if user just wants to retry
        }
    }
    
    private suspend fun processPlanToTasks() {
        val stepData = loadStepData(context, diaryDate, STEP_PROCESS_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) return
        
        listener.onStepStarted(STEP_PROCESS_PLAN, "Processing Plan to Tasks & Events")
        saveStepState(context, diaryDate, STEP_PROCESS_PLAN, StepProgress.STATUS_RUNNING, "Processing...")
        
        try {
            // Get JSON from Step 9
            val step9Data = loadStepData(context, diaryDate, STEP_GENERATE_PLAN)
            if (step9Data.resultJson == null) {
                throw IllegalStateException("Step 9 not completed. Run AI Plan first.")
            }
            
            val step9Json = JSONObject(step9Data.resultJson)
            val tasks = step9Json.optJSONArray("tasks") ?: JSONArray()
            val events = step9Json.optJSONArray("events") ?: JSONArray()
            val wakeupTime = step9Json.optString("wakeupTime")
            val sleepStartTime = step9Json.optString("sleepStartTime")
            
            // Persist Sleep/Wake times for global access (Bedtime Mode, etc.)
            val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("planner_wakeup_time", wakeupTime)
                putString("planner_sleep_time", sleepStartTime)
                if (sleepStartTime.isNotEmpty()) {
                    TerminalLogger.log("Nightly: Saved planned sleep time: $sleepStartTime")
                }
            }.apply()
            
            val createdItems = JSONArray() // Moved up
            val nextDay = diaryDate.plusDays(1)
            
            // Auto-Set Alarm Logic
            // Auto-Set Alarm Logic (Smart - Internal AlarmManager)
            if (wakeupTime.isNotEmpty()) {
                val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
                val autoAlarm = prefs.getBoolean("auto_set_alarm", false)
                
                if (autoAlarm) {
                    try {
                        val parts = wakeupTime.split(":")
                        if (parts.size == 2) {
                            val hour = parts[0].toInt()
                            val min = parts[1].toInt()
                            
                            // Calculate alarm time for TOMORROW
                            val alarmTime = nextDay.atTime(hour, min)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                                
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                            
                            if (alarmManager != null) {
                                // Check permission for Android 12+
                                val canSchedule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    alarmManager.canScheduleExactAlarms()
                                } else {
                                    true
                                }
                                
                                if (canSchedule) {
                                    val alarmIntent = android.content.Intent(context, com.neubofy.reality.receivers.ReminderReceiver::class.java).apply {
                                        putExtra("id", "nightly_wakeup") // Constant ID allows rescheduling!
                                        putExtra("title", "Wake Up (Reality Plan)")
                                        putExtra("mins", 0)
                                        putExtra("source", "NIGHTLY")
                                        putExtra("snoozeEnabled", true)
                                        putExtra("autoSnoozeEnabled", true)
                                    }
                                    
                                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                                        context,
                                        777, // Constant Request Code
                                        alarmIntent,
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    
                                    // Set Exact Alarm (Idle safe)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                                    } else {
                                        alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                                    }
                                    
                                    // 2. VISIBILITY: Save to CustomReminders so it shows in the App UI
                                    try {
                                        val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
                                        val reminders = prefsLoader.loadCustomReminders()
                                        
                                        // Remove existing nightly wakeup if present (to update it)
                                        reminders.removeAll { it.id == "nightly_wakeup" }
                                        
                                        // Add updated one
                                        reminders.add(com.neubofy.reality.data.CustomReminder(
                                            id = "nightly_wakeup",
                                            title = "Wake Up (AI Plan)",
                                            hour = hour,
                                            minute = min,
                                            isEnabled = true,
                                            repeatDays = emptyList(), // Runs everyday (updated daily by AI)
                                            snoozeEnabled = true,
                                            autoSnoozeEnabled = true,
                                            redirectUrl = "nightly_plan" // Special tag to open plan?
                                        ))
                                        
                                        prefsLoader.saveCustomReminders(reminders)
                                        TerminalLogger.log("Nightly: Saved alarm to UI list (CustomReminders)")
                                        
                                    } catch (e: Exception) {
                                        TerminalLogger.log("Nightly: Failed to save alarm to UI - ${e.message}")
                                    }
                                    
                                    TerminalLogger.log("Nightly: Smart Alarm scheduled for $wakeupTime")
                                    createdItems.put(JSONObject().apply {
                                        put("type", "ALARM")
                                        put("time", wakeupTime)
                                        put("status", "SUCCESS (Smart Internal + UI)")
                                    })
                                } else {
                                    TerminalLogger.log("Nightly: Alarm permission missing")
                                    createdItems.put(JSONObject().apply {
                                        put("type", "ALARM")
                                        put("time", wakeupTime)
                                        put("status", "FAILED: Permission Missing")
                                        put("warning", "Please grant Alarm permission in Settings.")
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        TerminalLogger.log("Nightly: Failed to set alarm - ${e.message}")
                        createdItems.put(JSONObject().apply {
                            put("type", "ALARM")
                            put("time", wakeupTime)
                            put("status", "FAILED: ${e.message}")
                        })
                    }
                }
            }
            
            var tasksCreated = 0
            var eventsCreated = 0
            // val nextDay = diaryDate.plusDays(1) // Moved up
            
            // For feedback/debug (Moved declaration up)
            
            // Load Task List Configs for mapping names to IDs
            val listConfigs = com.neubofy.reality.data.db.AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            
            // Create Google Tasks
            for (i in 0 until tasks.length()) {
                val taskLog = JSONObject()
                try {
                    val taskObj = tasks.getJSONObject(i)
                    val title = taskObj.optString("title", "").trim()
                    val notes = taskObj.optString("notes")
                    val rawListId = taskObj.optString("taskListId", "@default")
                    val startTime = taskObj.optString("startTime")
                    
                    taskLog.put("inputTitle", title)
                    taskLog.put("inputStartTime", startTime)
                    taskLog.put("inputList", rawListId)
                    
                    if (title.isNotEmpty()) {
                        // Resolve List ID (AI might return the Name instead of ID)
                        var finalTaskListId = "@default"
                        val matchedConfig = listConfigs.find { 
                            it.googleListId == rawListId || it.displayName.equals(rawListId, ignoreCase = true) 
                        }
                        
                        if (matchedConfig != null) {
                            finalTaskListId = matchedConfig.googleListId
                        } else if (rawListId != "@default" && rawListId.isNotEmpty()) {
                            // AI returned an unknown list ID/Name (e.g. "inbox") -> Fallback to Default to avoid API Error
                            taskLog.put("warning", "List '$rawListId' not found. Used Default.")
                            TerminalLogger.log("Nightly: List '$rawListId' not found. Fallback to @default.")
                            finalTaskListId = "@default" 
                        }
                        
                        val finalTitle = if (!startTime.isNullOrEmpty()) "$startTime|$title" else title
                        val dueDate = nextDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T09:00:00.000Z"
                        
                        val createdTask = com.neubofy.reality.google.GoogleTasksManager.createTask(context, finalTitle, notes, dueDate, finalTaskListId)
                        
                        if (createdTask != null) {
                            tasksCreated++
                            taskLog.put("status", "SUCCESS")
                            taskLog.put("finalTitle", finalTitle)
                            taskLog.put("finalList", finalTaskListId)
                            TerminalLogger.log("Nightly: Created task - $finalTitle in list $finalTaskListId")
                        } else {
                            taskLog.put("status", "FAILED (API Error)")
                        }
                    } else {
                        taskLog.put("status", "FAILED (Empty Title)")
                    }
                } catch (e: Exception) {
                    taskLog.put("status", "ERROR: ${e.message}")
                    TerminalLogger.log("Nightly: Failed to process task at index $i - ${e.message}")
                }
                taskLog.put("type", "TASK")
                createdItems.put(taskLog)
            }
            
            // Create Calendar Events
            for (i in 0 until events.length()) {
                val eventLog = JSONObject()
                try {
                    val eventObj = events.getJSONObject(i)
                    val title = eventObj.optString("title", "").trim()
                    val startTime = eventObj.optString("startTime", "")
                    val endTime = eventObj.optString("endTime", "")
                    val description = eventObj.optString("description")
                    
                    eventLog.put("inputTitle", title)
                    eventLog.put("inputTime", "$startTime - $endTime")
                    
                    if (title.isNotEmpty() && startTime.isNotEmpty() && endTime.isNotEmpty()) {
                        // Convert HH:mm to epoch milliseconds for next day
                        val startParts = startTime.split(":")
                        val endParts = endTime.split(":")
                        
                        if (startParts.size >= 2 && endParts.size >= 2) {
                            val startMs = nextDay.atTime(startParts[0].toInt(), startParts[1].toInt())
                                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endMs = nextDay.atTime(endParts[0].toInt(), endParts[1].toInt())
                                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            
                            val eventId = com.neubofy.reality.google.GoogleCalendarManager.createEvent(
                                context, title, startMs, endMs, description
                            )
                            
                            if (eventId != null) {
                                eventsCreated++
                                eventLog.put("status", "SUCCESS")
                                TerminalLogger.log("Nightly: Created Cloud Google Calendar event - $title (ID: $eventId)")
                            } else {
                                eventLog.put("status", "FAILED (Cloud API Error)")
                            }
                        } else {
                            eventLog.put("status", "FAILED (Invalid Time Format)")
                        }
                    } else {
                        eventLog.put("status", "FAILED (Missing Title or Time)")
                    }
                } catch (e: Exception) {
                    eventLog.put("status", "ERROR: ${e.message}")
                    TerminalLogger.log("Nightly: Failed to create calendar event at index $i - ${e.message}")
                }
                eventLog.put("type", "EVENT")
                createdItems.put(eventLog)
            }
            
            val details = "$tasksCreated tasks, $eventsCreated events created"
            
            // Build resultJson
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("tasksCount", tasks.length())
                    put("eventsCount", events.length())
                    put("wakeupTime", wakeupTime)
                    put("sleepStartTime", sleepStartTime)
                })
                put("output", JSONObject().apply {
                    put("tasksCreated", tasksCreated)
                    put("eventsCreated", eventsCreated)
                    put("items", createdItems)
                })
                // Legacy compatibility
                put("tasksCreated", tasksCreated)
                put("eventsCreated", eventsCreated)
                put("items", createdItems)
            }.toString()
            
            listener.onStepCompleted(STEP_PROCESS_PLAN, "Plan Processed", details)
            saveStepState(context, diaryDate, STEP_PROCESS_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)
            
            // 5. GLOBAL SYNC: Update Bedtime Start/End Times
            syncBedtime(wakeupTime, sleepStartTime)
            
        } catch (e: Exception) {
            listener.onError(STEP_PROCESS_PLAN, "Process Failed: ${e.message}")
            saveStepState(context, diaryDate, STEP_PROCESS_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }

    private fun syncBedtime(wakeupTimeStr: String, sleepTimeStr: String) {
        try {
            if (wakeupTimeStr.isEmpty() && sleepTimeStr.isEmpty()) return
            
            val prefs = com.neubofy.reality.utils.SavedPreferencesLoader(context)
            val bedtime = prefs.getBedtimeData()
            var modified = false
            
            if (sleepTimeStr.isNotEmpty()) {
                val mins = timeToMinutes(sleepTimeStr)
                if (mins >= 0) {
                    bedtime.startTimeInMins = mins
                    modified = true
                }
            }
            
            if (wakeupTimeStr.isNotEmpty()) {
                val mins = timeToMinutes(wakeupTimeStr)
                if (mins >= 0) {
                    bedtime.endTimeInMins = mins
                    modified = true
                }
            }
            
            if (modified) {
                // Ensure enabled if we are syncing from protocol
                bedtime.isEnabled = true
                prefs.saveBedtimeData(bedtime)
                TerminalLogger.log("Nightly: Bedtime Synced -> Start: $sleepTimeStr, End: $wakeupTimeStr")
            }
        } catch (e: Exception) {
            TerminalLogger.log("Nightly: Bedtime sync error - ${e.message}")
        }
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                (h * 60) + m
            } else -1
        } catch (e: Exception) { -1 }
    }

    
    private fun getDiaryTitle(): String {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        return "Diary of ${diaryDate.format(formatter)}"
    }
    
    private fun getDiaryUrl(docId: String): String {
        return "https://docs.google.com/document/d/$docId/edit"
    }
    
    private fun buildDiaryContent(summary: DaySummary, questions: List<String>): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val template = prefs.getString("template_diary", DEFAULT_DIARY_TEMPLATE) ?: DEFAULT_DIARY_TEMPLATE
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val dateStr = summary.date.format(dateFormatter)

        // 1. Build Metrics/Stats Block
        val totalPlanned = summary.totalPlannedMinutes
        val totalEffective = summary.totalEffectiveMinutes
        val efficiency = if (totalPlanned > 0) (totalEffective * 100 / totalPlanned) else 0
        
        val statsSb = StringBuilder()
        statsSb.appendLine("## 📊 Today's Metrics")
        statsSb.appendLine("---")
        statsSb.appendLine("- **Scheduled Time**: $totalPlanned minutes")
        statsSb.appendLine("- **Effective Study**: $totalEffective minutes")
        statsSb.appendLine("- **Efficiency**: $efficiency%")
        statsSb.appendLine()
        
        statsSb.appendLine("### ⏱️ Productive Sessions (Tapasya)")
        if (summary.completedSessions.isEmpty()) {
            statsSb.appendLine("_No focused sessions completed today._")
        } else {
            summary.completedSessions.forEach { session ->
                val effectiveMins = session.effectiveTimeMs / 60000
                val targetMins = session.targetTimeMs / 60000
                statsSb.appendLine("- **${session.name}**: ${effectiveMins}min (Target: ${targetMins}min)")
            }
        }
        statsSb.appendLine()
        
        statsSb.appendLine("### 📋 Task Summary")
        if (summary.tasksCompleted.isEmpty() && summary.tasksDue.isEmpty()) {
            statsSb.appendLine("_No tasks recorded for today._")
        } else {
            summary.tasksCompleted.forEach { task -> statsSb.appendLine("- ✓ $task") }
            summary.tasksDue.forEach { task -> statsSb.appendLine("- ○ $task (pending)") }
        }
        statsSb.appendLine()
        
        statsSb.appendLine("### 📅 Calendar Schedule")
        if (summary.calendarEvents.isEmpty()) {
            statsSb.appendLine("_No calendar events recorded._")
        } else {
            summary.calendarEvents.forEach { event ->
                val start = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(event.startTime))
                val end = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(event.endTime))
                statsSb.appendLine("- **$start - $end**: ${event.title}")
            }
        }
        val statsBlock = statsSb.toString()

        // 2. Build Questions Block
        val qSb = StringBuilder()
        qSb.appendLine("## 💡 Personalized Reflection Questions")
        qSb.appendLine("---")
        qSb.appendLine("_Answer the following questions to gain clarity on your progress:_")
        qSb.appendLine()
        
        if (questions.isEmpty()) {
            qSb.appendLine("_Generating AI questions..._")
        } else {
            questions.forEachIndexed { index, question ->
                qSb.appendLine("### Q${index + 1}: $question")
                qSb.appendLine()
                qSb.appendLine("> ") // Placeholder for answer
                qSb.appendLine()
                qSb.appendLine()
            }
        }
        val questionsBlock = qSb.toString()

        // 3. Perform Replacements
        return template
            .replace("{date}", dateStr)
            .replace("{questions}", questionsBlock)
            .replace("{stats}", statsBlock)
            .replace("{data}", statsBlock)
    }
    
    private fun getFallbackQuestions(): List<String> {
        return listOf(
            "What was the most productive moment of your day today?",
            "What distracted you the most, and how can you minimize it tomorrow?",
            "Did you achieve what you set out to do? If not, what got in the way?",
            "What's one thing you're grateful for from today?",
            "What's your top priority for tomorrow, and how will you protect time for it?"
        )
    }
    
    // ========== STEP 11 & 12: Report Generation ==========
    
    // ========== PUBLIC INTERFACE FOR GRANULAR CONTROL ==========
    
    suspend fun executeSpecificStep(step: Int) {
        when (step) {
            STEP_FETCH_TASKS -> step1_fetchTasks()
            STEP_FETCH_SESSIONS -> step2_fetchSessions()
            STEP_CALC_SCREEN_TIME -> step3_calcScreenTime()
            STEP_GENERATE_QUESTIONS -> step4_generateQuestions()
            STEP_CREATE_DIARY -> step5_createDiary()
            STEP_ANALYZE_REFLECTION -> step6_analyzeReflection()
            STEP_FINALIZE_XP -> step7_finalizeXp()
            STEP_CREATE_PLAN_DOC -> step8_createPlanDoc()
            STEP_GENERATE_PLAN -> step9_generatePlan()
            STEP_PROCESS_PLAN -> processPlanToTasks()
            STEP_GENERATE_REPORT -> step11_generateReport()
            STEP_GENERATE_PDF -> step12_generatePdf()
            else -> throw IllegalArgumentException("Unknown step: $step")
        }
    }

    // ========== INDIVIDUAL STEP IMPLEMENTATIONS (Extracted) ==========

    // Steps 1-3 are already defined above (step1_fetchTasks, etc.)



    private suspend fun step8_createPlanDoc() {
        createPlanDoc() // Existing function
    }
    
    private suspend fun step9_generatePlan() {
        val stepData = loadStepData(context, diaryDate, STEP_GENERATE_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) return
        
        listener.onStepStarted(STEP_GENERATE_PLAN, "AI Parsing Plan")
        saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Reading plan...")
        
        try {
            val nightlyModel = AISettingsActivity.getNightlyModel(context)
            
            if (nightlyModel.isNullOrEmpty()) {
                listener.onStepSkipped(STEP_GENERATE_PLAN, "Plan AI", "No Model Configured")
                saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_SKIPPED, "No Model")
                return
            }
            
            // 1. Read Plan Document content
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val planId = prefs.getString(getPlanDocIdKey(diaryDate), null)
            
            if (planId == null) {
                listener.onError(STEP_GENERATE_PLAN, "Plan Document missing")
                saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, "No Plan Doc")
                return
            }
            
            val planContent = GoogleDocsManager.getDocumentContent(context, planId) ?: ""
            
            if (planContent.length < 20) {
                // Plan too short, reject
                val errorDetails = "Plan too short. Please write your tasks and schedule in the plan document."
                saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                listener.onAnalysisFeedback(errorDetails)
                return
            }
            
            saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "AI parsing...")
            
            // 2. Call AI to parse plan into JSON {tasks: [], events: []}
            // Fetch Task List Configs for AI Context
            val taskListConfigs = AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            val aiResponse = NightlyAIHelper.analyzePlan(context, nightlyModel, planContent, taskListConfigs)
            
            // 3. Validate JSON with Robust Extraction
            try {
                // Find first '{' and last '}' to extract JSON block
                val start = aiResponse.indexOf('{')
                val end = aiResponse.lastIndexOf('}')
                
                val jsonStr = if (start != -1 && end != -1 && end > start) {
                    aiResponse.substring(start, end + 1)
                } else {
                    aiResponse // Fallback
                }
                
                val json = JSONObject(jsonStr.trim())
                
                val tasks = json.optJSONArray("tasks") ?: JSONArray()
                val events = json.optJSONArray("events") ?: JSONArray()
                
                if (tasks.length() == 0 && events.length() == 0) {
                    // No items extracted, reject
                    val errorDetails = "Could not extract tasks or events from your plan. Please write clearer items with times."
                    saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                    listener.onAnalysisFeedback(errorDetails)
                    return
                }
                
                // 4. Accept - Save parsed JSON with rich debug info
                val details = "${tasks.length()} tasks, ${events.length()} events extracted"
                val mentorship = json.optString("mentorship", "No advice generated.")
                val wakeupTime = json.optString("wakeupTime", "")
                val sleepStartTime = json.optString("sleepStartTime", "")
                
                val resultJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("planContent", planContent)
                        put("model", nightlyModel)
                        put("planDocId", planId)
                    })
                    put("output", JSONObject().apply {
                        put("tasks", tasks)
                        put("events", events)
                        put("mentorship", mentorship)
                        put("wakeupTime", wakeupTime)
                        put("sleepStartTime", sleepStartTime)
                    })
                    // Legacy compatibility (DO NOT CHANGE THESE KEYS)
                    put("tasks", tasks)
                    put("events", events)
                    put("wakeupTime", wakeupTime)
                    put("sleepStartTime", sleepStartTime)
                    put("rawResponse", aiResponse)
                    put("sanitizedJson", jsonStr)
                }.toString()
                
                listener.onStepCompleted(STEP_GENERATE_PLAN, "Plan Parsed", details)
                saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)
                
            } catch (e: Exception) {
                // JSON parse failed, reject
                val errorDetails = "AI response was not valid JSON. Please try again or simplify your plan."
                TerminalLogger.log("Step 9 JSON parse error: ${e.message}, Raw: $aiResponse")
                
                // Save the raw response even on failure for debugging in double-tap window
                val failureJson = JSONObject().apply {
                    put("rawResponse", aiResponse)
                    put("error", e.message)
                }.toString()
                
                saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails, failureJson)
                listener.onAnalysisFeedback(errorDetails)
            }
            
        } catch (e: Exception) {
             listener.onError(STEP_GENERATE_PLAN, "AI Plan Failed: ${e.message}")
             saveStepState(context, diaryDate, STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }
    // ========== REPORT GENERATION (STEPS 11 & 12) are defined at the end of file ==========

    // Restores Completed status for Data Collection steps (1 & 2) if DaySummary is valid.
    // This handles cases where user deleted data or re-ran report without running previous steps explicitly.
    private suspend fun restoreCollectedDataState(summary: DaySummary) {
        // Heal Step 1: Tasks
        // We use com.neubofy.reality.data.model.FetchedTasks to match saved format
        val fetched = com.neubofy.reality.data.model.FetchedTasks(summary.tasksCompleted, summary.tasksDue)
        val tasksJson = com.google.gson.Gson().toJson(fetched)
        val taskDetails = "Tasks: ${summary.tasksCompleted.size} Done, ${summary.tasksDue.size} Due"
        
        saveStepState(context, diaryDate, STEP_FETCH_TASKS, StepProgress.STATUS_COMPLETED, taskDetails, tasksJson)
        
        // Update local memory
        this.fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(
            fetched.dueTasks,
            fetched.completedTasks,
            fetched.dueTasks.size,
            fetched.completedTasks.size
        )
        
        // Heal Step 2: Sessions
        val sessionCount = summary.completedSessions.size
        val focusMins = summary.totalEffectiveMinutes
        val sessionDetails = "Sessions: $sessionCount ($focusMins mins)"
        
        // Step 2 doesn't use complex JSON for result, currently just counts?
        // Check collectDayData: usually saves daySummary? Or just stats.
        // Step 2 usually saves "Sessions: N..." and maybe resultJson is null in legacy or summary json?
        // We'll save just details which sets status to COMPLETED.
        // Actually, Step 2 should ideally save the DaySummary JSON?
        // But DaySummary is large.
        // For now, restoring Status is priority.
        saveStepState(context, diaryDate, STEP_FETCH_SESSIONS, StepProgress.STATUS_COMPLETED, sessionDetails, "{}")
    }
    

    // ========== DEBUG / UI SUPPORT ==========
    
    suspend fun getStepDebugData_LEGACY(step: Int): String {
        return withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            
            // Common Header
            sb.append("Details for Step $step\n")
            sb.append("────────────────────────\n\n")
            
            try {
                when (step) {
                    STEP_FETCH_TASKS -> {
                        sb.append("RAW OUTPUT:\n")
                        val completed = fetchedTasks?.completedTasks ?: emptyList()
                        val due = fetchedTasks?.dueTasks ?: emptyList()
                        if (completed.isEmpty() && due.isEmpty()) {
                            // No data available
                            sb.append("No task data in memory. (Run step to fetch)\n")
                        }
                        
                        sb.append("• Completed Tasks (${fetchedTasks?.completedTasks?.size}):\n")
                        fetchedTasks?.completedTasks?.forEach { sb.append("  - $it\n") }
                        
                        sb.append("\n• Due Tasks (${fetchedTasks?.dueTasks?.size}):\n")
                        fetchedTasks?.dueTasks?.forEach { sb.append("  - $it\n") }
                    }
                    
                    STEP_FETCH_SESSIONS -> {
                        if (daySummary == null) try { collectDayDataSilently() } catch (e: Exception) {}
                        val s = daySummary
                        if (s != null) {
                            sb.append("• Sessions (${s.completedSessions.size}):\n")
                            s.completedSessions.forEach { 
                                sb.append("  - ${it.name} (${it.effectiveTimeMs/60000}m / ${it.targetTimeMs/60000}m)\n") 
                            }
                            sb.append("\n• Calendar Events (${s.calendarEvents.size}):\n")
                            s.calendarEvents.forEach { sb.append("  - ${it.title}\n") }
                        } else {
                            sb.append("No session data available.")
                        }
                    }
                    
                    STEP_CALC_SCREEN_TIME -> {
                        sb.append("• Total Screen Time: $screenTimeMinutes mins\n")
                        sb.append("• XP Impact: $screenTimeXpDelta XP\n")
                        // If we had limit info, we'd show it here.
                    }
                    
                    STEP_GENERATE_QUESTIONS -> {
                        sb.append("INPUTS:\n")
                        sb.append("- User Intro: ${AISettingsActivity.getUserIntroduction(context)?.take(50)}...\n")
                        sb.append("- Tasks: ${fetchedTasks?.completedTasks?.size} done\n")
                        sb.append("- Sessions: ${daySummary?.completedSessions?.size}\n\n")
                        
                        sb.append("OUTPUT (Start Time: $nightlyStartTime):\n")
                        if (generatedQuestions.isNotEmpty()) {
                             generatedQuestions.forEachIndexed { i, q -> sb.append("${i+1}. $q\n\n") }
                        } else {
                            // Try to load from prefs
                            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("questions_${diaryDate}", null)
                            if (saved != null) {
                                saved.split("|").forEachIndexed { i, q -> sb.append("${i+1}. $q\n\n") }
                            } else {
                                sb.append("No questions generated or saved.")
                            }
                        }
                    }
                    
                    STEP_CREATE_DIARY -> {
                         val template = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("template_diary", DEFAULT_DIARY_TEMPLATE)
                         sb.append("TEMPLATE USED:\n${template?.take(100)}...\n\n")
                         sb.append("SAVED DOC ID:\n$diaryDocId\n")
                    }
                    
                    STEP_ANALYZE_REFLECTION -> {
                        sb.append("INPUT:\n")
                        sb.append("Diary Doc ID: $diaryDocId\n")
                        val xp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("reflection_xp_${diaryDate}", 0)
                        sb.append("OUTPUT:\n")
                        sb.append("Calculated XP: $xp\n")
                    }
                    
                    STEP_GENERATE_REPORT -> {
                        sb.append("GENERATED REPORT CONTENT:\n")
                        val content = NightlyRepository.getReportContent(context, diaryDate)
                        if (content != null) {
                            sb.append(content.take(500) + "...\n")
                        } else {
                             sb.append("Report not generated yet.")
                        }
                    }
                    
                    else -> sb.append("No detailed debug info for this step.")
                }
            } catch (e: Exception) {
                sb.append("Error fetching details: ${e.message}")
            }
            sb.toString()
        }
    }

    private suspend fun checkDiaryExists(): Boolean {
        saveStepState(context, diaryDate, STEP_CHECK_DIARY, StepProgress.STATUS_RUNNING, "Checking Drive...")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diaryFolderId = prefs.getString("diary_folder_id", null)
        
        if (diaryFolderId.isNullOrEmpty()) {
            val err = "Diary folder not configured"
            listener.onError(STEP_CHECK_DIARY, err)
            saveStepState(context, diaryDate, STEP_CHECK_DIARY, StepProgress.STATUS_ERROR, err)
            return false
        }
        
        val diaryTitle = getDiaryTitle()
        
        return withContext(Dispatchers.IO) {
            try {
                val existingFiles = GoogleDriveManager.listFilesInFolder(context, diaryFolderId)
                val matchingFile = existingFiles.find { it.name == diaryTitle }
                
                if (matchingFile != null) {
                    diaryDocId = matchingFile.id
                    NightlyRepository.saveDiaryDocId(context, diaryDate, matchingFile.id)
                    
                    listener.onStepCompleted(STEP_CHECK_DIARY, "Diary Found", diaryTitle)
                    saveStepState(context, diaryDate, STEP_CHECK_DIARY, StepProgress.STATUS_COMPLETED, "Found: $diaryTitle")
                    saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_COMPLETED, diaryTitle)
                    true
                } else {
                    listener.onStepCompleted(STEP_CHECK_DIARY, "No existing diary")
                    saveStepState(context, diaryDate, STEP_CHECK_DIARY, StepProgress.STATUS_COMPLETED, "Not found (Will create)")
                    false
                }
            } catch (e: Exception) {
                listener.onError(STEP_CHECK_DIARY, "Check failed: ${e.message}")
                saveStepState(context, diaryDate, STEP_CHECK_DIARY, StepProgress.STATUS_ERROR, e.message)
                false
            }
        }
    }

    // Removed redundant calculateReflection bridge as per refactor.
    // Step 6 now saves XP directly, and Step 7 reads it via XPManager/Repository flow.


    suspend fun getStepDebugData(step: Int): String {
        return withContext(Dispatchers.IO) {
            val stepData = loadStepData(context, diaryDate, step)
            val rawJson = stepData.resultJson ?: return@withContext "Step: ${getStepName(step)}\nStatus: ${getStatusText(stepData.status)}\n\nNo detailed data stored for this step."
            
            val sb = StringBuilder()
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("📌 STEP ${step}: ${getStepName(step).uppercase()}")
            sb.appendLine("📟 STATUS: ${getStatusText(stepData.status)}")
            if (!stepData.details.isNullOrEmpty()) sb.appendLine("📝 DETAILS: ${stepData.details}")
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

            try {
                val json = JSONObject(rawJson)
                
                // 1. INPUT SECTION
                if (json.has("input")) {
                    sb.appendLine("📥 [INPUT DATA]")
                    val input = json.getJSONObject("input")
                    formatJsonBlock(input, sb)
                    sb.appendLine()
                }

                // 2. OUTPUT SECTION
                if (json.has("output")) {
                    sb.appendLine("📤 [OUTPUT DATA]")
                    val output = json.getJSONObject("output")
                    formatJsonBlock(output, sb)
                    sb.appendLine()
                }

                // 3. LEGACY/OTHER DATA (if not in input/output)
                val otherKeys = json.keys().asSequence().filter { it != "input" && it != "output" && !it.startsWith("_") }.toList()
                if (otherKeys.isNotEmpty()) {
                    sb.appendLine("📋 [OTHER DATA]")
                    otherKeys.forEach { key ->
                        val value = json.get(key)
                        sb.appendLine("  • $key: $value")
                    }
                    sb.appendLine()
                }

                // 4. METADATA (Internal)
                sb.appendLine("⚙️ [RAW JSON REFERENCE]")
                sb.appendLine(json.toString())

            } catch (e: Exception) {
                sb.appendLine("❌ [FORMATTING ERROR]")
                sb.appendLine("Failed to parse structure: ${e.message}")
                sb.appendLine("\n[RAW DATA]")
                sb.appendLine(rawJson)
            }
            sb.toString()
        }
    }

    private fun formatJsonBlock(json: JSONObject, sb: StringBuilder) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            
            if (value is JSONArray) {
                sb.appendLine("  • $key (${value.length()} items):")
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    if (item is JSONObject) {
                        // For structured items (like tasks/events)
                        val title = item.optString("title").ifEmpty { item.optString("inputTitle") }
                        val status = item.optString("status")
                        val prefix = if (status == "SUCCESS") "✅" else if (status.startsWith("FAILED") || status.startsWith("ERROR")) "❌" else "○"
                        sb.appendLine("    $prefix $title")
                        
                        // Show extra info for processed items
                        val type = item.optString("type")
                        if (type == "TASK") {
                             val list = item.optString("inputList")
                             val time = item.optString("inputStartTime")
                             if (list.isNotEmpty() || time.isNotEmpty()) sb.appendLine("       └ $list @ $time")
                        }
                    } else {
                        sb.appendLine("    - $item")
                    }
                }
            } else {
                // Shorten very long strings for readability (like raw responses)
                val valStr = value.toString()
                val displayVal = if (valStr.length > 500) valStr.take(500) + "... [truncated]" else valStr
                sb.appendLine("  • $key: $displayVal")
            }
        }
    }

    private fun getStepName(step: Int): String = when (step) {
        STEP_FETCH_TASKS -> "Fetch Tasks"
        STEP_FETCH_SESSIONS -> "Fetch Sessions"
        STEP_CALC_SCREEN_TIME -> "Calc Screen Time"
        STEP_GENERATE_QUESTIONS -> "Generate Questions"
        STEP_CREATE_DIARY -> "Create Diary"
        STEP_ANALYZE_REFLECTION -> "Analyze Reflection"
        STEP_FINALIZE_XP -> "Finalize XP"
        STEP_CREATE_PLAN_DOC -> "Create Plan Doc"
        STEP_GENERATE_PLAN -> "Generate Plan"
        STEP_PROCESS_PLAN -> "Process Plan"
        STEP_GENERATE_REPORT -> "Generate Report"
        STEP_GENERATE_PDF -> "Generate PDF"
        else -> "Unknown Step"
    }

    private fun getStatusText(status: Int): String = when (status) {
        StepProgress.STATUS_PENDING -> "⏳ Pending"
        StepProgress.STATUS_RUNNING -> "🔄 Running"
        StepProgress.STATUS_COMPLETED -> "✅ Completed"
        StepProgress.STATUS_SKIPPED -> "⏭️ Skipped"
        StepProgress.STATUS_ERROR -> "❌ Error"
        else -> "Unknown"
    }

    
    // ========== STEP 11: Generate Report ==========
    private suspend fun step11_generateReport() {
        // Check if already completed
        val stepData = loadStepData(context, diaryDate, STEP_GENERATE_REPORT)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            TerminalLogger.log("Nightly: Step 11 already completed for $diaryDate")
            return
        }
        
        listener.onStepStarted(STEP_GENERATE_REPORT, "Generating Report")
        saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Collecting day summary...")
        
        try {
            // 1. Recover Day Summary
            val summary = daySummary ?: collectDayDataSilently()
            TerminalLogger.log("Nightly Step 11: Summary recovered (${summary.tasksCompleted.size} tasks done)")
            
            // 2. Recover Doc IDs
            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Retrieving document IDs...")
            val dId = diaryDocId ?: NightlyRepository.getDiaryDocId(context, diaryDate)
            val pId = NightlyRepository.getPlanDocId(context, diaryDate)
            
            if (dId == null) {
                TerminalLogger.log("Nightly Step 11 ERROR: Diary Doc ID is missing.")
            }
            if (pId == null) {
                TerminalLogger.log("Nightly Step 11 ERROR: Plan Doc ID is missing.")
            }

            // 3. Fetch Diary Content
            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Fetching Diary content...")
            val diaryContent = withContext(Dispatchers.IO) {
                if (dId != null) {
                    val content = GoogleDocsManager.getDocumentContent(context, dId)
                    if (content == null) TerminalLogger.log("Nightly Step 11 ERROR: Failed to read Diary doc $dId")
                    content ?: ""
                } else ""
            }
            
            // 4. Fetch Plan Content
            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Fetching Plan content...")
            val planContent = withContext(Dispatchers.IO) {
                if (pId != null) {
                    val content = GoogleDocsManager.getDocumentContent(context, pId)
                    if (content == null) TerminalLogger.log("Nightly Step 11 ERROR: Failed to read Plan doc $pId")
                    content ?: ""
                } else ""
            }
            
            if (diaryContent.isEmpty() && planContent.isEmpty()) {
                val errorMsg = "Both Diary and Plan are empty. Please ensure documents have content before generating report (Diary ID: $dId, Plan ID: $pId)."
                throw Exception(errorMsg)
            }

            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "AI Generating report...")
            TerminalLogger.log("Nightly Step 11: Prompting AI with ${diaryContent.length} chars diary and ${planContent.length} chars plan")

            // 5. Generate report using AI
            val report = reporter.generateReport(
                diaryDate,
                summary,
                diaryContent,
                planContent
            )
            
            // 6. Save results with detailed snippets for debug transparency
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("diarySnippet", if (diaryContent.length > 1000) diaryContent.take(1000) + "..." else diaryContent)
                    put("planSnippet", if (planContent.length > 1000) planContent.take(1000) + "..." else planContent)
                    put("efficiency", "${summary.totalEffectiveMinutes} / ${summary.totalPlannedMinutes} mins")
                })
                put("output", JSONObject().apply {
                    put("reportLength", report.length)
                    put("reportSnippet", if (report.length > 1000) report.take(1000) + "..." else report)
                    put("date", diaryDate.toString())
                })
                // Legacy compatibility
                put("diaryDocId", dId ?: "N/A")
                put("planDocId", pId ?: "N/A")
                put("efficiency", "${summary.totalEffectiveMinutes} / ${summary.totalPlannedMinutes} mins")
            }.toString()
            
            val details = "Report generated (${report.length} chars)"
            listener.onStepCompleted(STEP_GENERATE_REPORT, "Report Generated", details)
            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_COMPLETED, details, resultJson)
            
            // SMART ALERT
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: Report Ready", "Analysis complete. Tap to view.", STEP_GENERATE_REPORT)
            
        } catch (e: Exception) {
            val err = "Step 11 Error: ${e.message}"
            TerminalLogger.log("Nightly Protocol ERROR (Step 11): $err")
            listener.onError(STEP_GENERATE_REPORT, err)
            saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_ERROR, err)
            
            // SMART ALERT ERROR
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: Failed", "Report generation failed. Checking logs.", STEP_GENERATE_REPORT)
        }
    }
    
    // ========== STEP 12: Generate PDF ==========
    private suspend fun step12_generatePdf() {
        listener.onStepStarted(STEP_GENERATE_PDF, "Creating PDF Report")
        saveStepState(context, diaryDate, STEP_GENERATE_PDF, StepProgress.STATUS_RUNNING, "Creating PDF...")
        
        try {
            // Get report content from Step 11 (stored in database)
            val reportContent = NightlyRepository.getReportContent(context, diaryDate)
            
            if (reportContent.isNullOrEmpty()) {
                val err = "No report content available (Step 11 may have failed)"
                listener.onStepSkipped(STEP_GENERATE_PDF, "PDF Skipped", err)
                saveStepState(context, diaryDate, STEP_GENERATE_PDF, StepProgress.STATUS_SKIPPED, err)
                return
            }
            
            // Get report folder ID
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val reportFolderId = prefs.getString("report_folder_id", null)
            
            if (reportFolderId.isNullOrEmpty()) {
                val err = "Report folder not configured"
                listener.onError(STEP_GENERATE_PDF, err)
                saveStepState(context, diaryDate, STEP_GENERATE_PDF, StepProgress.STATUS_ERROR, err)
                return
            }
            
            val dateFormatted = diaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val title = "Reality Report - $dateFormatted"
            val fileName = "Reality_Report_$dateFormatted"
            
            // Generate PDF from report content (markdown)
            val pdfFile = withContext(Dispatchers.IO) {
                com.neubofy.reality.utils.PdfGenerator.generatePdfFile(
                    context,
                    reportContent,
                    title,
                    fileName
                )
            }
            
            // Upload to Google Drive
            val pdfFileId = withContext(Dispatchers.IO) {
                val credential = GoogleAuthManager.getGoogleAccountCredential(context)
                    ?: throw Exception("Not signed in")
                
                val driveService = Drive.Builder(
                    GoogleAuthManager.getHttpTransport(),
                    GoogleAuthManager.getJsonFactory(),
                    credential
                ).setApplicationName("Reality").build()
                
                val fileMetadata = DriveFile()
                fileMetadata.setName("$fileName.pdf")
                fileMetadata.setParents(listOf(reportFolderId))
                fileMetadata.setMimeType("application/pdf")
                
                val mediaContent = FileContent("application/pdf", pdfFile)
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute()
                
                // Clean up temp file
                pdfFile.delete()
                
                uploadedFile.id
            }
            
            // Save PDF ID to database
            NightlyRepository.saveReportPdfId(context, diaryDate, pdfFileId)
            
            val details = "PDF uploaded: $pdfFileId"
            val pdfUrl = "https://drive.google.com/file/d/$pdfFileId/view"
            listener.onStepCompleted(STEP_GENERATE_PDF, "PDF Created", details, pdfUrl)
            
            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("reportSnippet", if (reportContent.length > 1000) reportContent.take(1000) + "..." else reportContent)
                    put("folderId", reportFolderId)
                })
                put("output", JSONObject().apply {
                    put("pdfId", pdfFileId)
                    put("pdfUrl", pdfUrl)
                })
            }.toString()
            saveStepState(context, diaryDate, STEP_GENERATE_PDF, StepProgress.STATUS_COMPLETED, details, resultJson)
            
            // SMART ALERT
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: PDF Saved", "Report uploaded to Drive.", STEP_GENERATE_PDF)
            
        } catch (e: Exception) {
            val err = "PDF generation failed: ${e.message}"
            TerminalLogger.log("Nightly: $err")
            e.printStackTrace()
            listener.onError(STEP_GENERATE_PDF, err)
            saveStepState(context, diaryDate, STEP_GENERATE_PDF, StepProgress.STATUS_ERROR, err)
            
            // SMART ALERT ERROR
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: PDF Error", "Failed to create/upload PDF.", STEP_GENERATE_PDF)
        }
    }
}
