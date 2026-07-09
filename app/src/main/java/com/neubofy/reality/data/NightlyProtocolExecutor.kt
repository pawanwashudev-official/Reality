package com.neubofy.reality.data

import android.content.Context
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.data.nightly.NightlyPhaseAnalysis
import com.neubofy.reality.data.nightly.NightlyPhaseData
import com.neubofy.reality.data.nightly.NightlyPhasePlanning
import com.neubofy.reality.data.nightly.NightlyProgressListener
import com.neubofy.reality.data.nightly.NightlySteps
import com.neubofy.reality.data.nightly.StepProgress
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.google.GoogleDocsManager
import com.neubofy.reality.google.GoogleDriveManager
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orchestrates the Nightly Protocol process:
 * 1. Collect day data (calendar events, tapasya sessions)
 * 2. Generate AI reflection questions
 * 3. Check if diary already exists
 * 4. Create Google Doc diary
 * 
 * REFACTORED: Now delegates to phase-specific classes:
 * - NightlyPhaseData (Steps 1-5)
 * - NightlyPhaseAnalysis (Steps 6-7)
 * - NightlyPhasePlanning (Steps 8-13)
 */
class NightlyProtocolExecutor(
    private val context: Context,
    private val diaryDate: LocalDate,
    private val listener: NightlyProgressListener
) {
    /**
     * Legacy interface alias for backward compatibility.
     * New code should use [com.neubofy.reality.data.nightly.NightlyProgressListener] directly.
     */
    interface NightlyProgressListener : com.neubofy.reality.data.nightly.NightlyProgressListener
    
    // Phase Executors (lazy initialization)
    private val phaseData: NightlyPhaseData by lazy {
        NightlyPhaseData(context, diaryDate, listener)
    }
    
    private val phaseAnalysis: NightlyPhaseAnalysis by lazy {
        NightlyPhaseAnalysis(context, diaryDate, listener)
    }
    
    private val phasePlanning: NightlyPhasePlanning by lazy {
        NightlyPhasePlanning(context, diaryDate, listener)
    }
    
    // In-memory state (for backward compatibility)
    private var daySummary: DaySummary? = null
    private var diaryDocId: String? = null
    private var reportHtml: String? = null
    private var nightlyStartTime: Long = System.currentTimeMillis()
    
    // Compat: fetchedTasks + generatedQuestions via phaseData
    private val generatedQuestions: List<String>
        get() = phaseData.generatedQuestions
    
    companion object {
        // 6-Step Protocol Constants
        const val STEP_FETCH_ANALYTICS = NightlySteps.STEP_FETCH_ANALYTICS
        const val STEP_CREATE_DIARY = NightlySteps.STEP_CREATE_DIARY
        const val STEP_SAVE_ANALYTICS = NightlySteps.STEP_SAVE_ANALYTICS
        const val STEP_CREATE_PLAN = NightlySteps.STEP_CREATE_PLAN
        const val STEP_APPLY_PLAN = NightlySteps.STEP_APPLY_PLAN
        const val STEP_GENERATE_REPORT = NightlySteps.STEP_GENERATE_REPORT

        // 13-Step Protocol Constants
        const val STEP_FETCH_TASKS = NightlySteps.STEP_FETCH_TASKS
        const val STEP_FETCH_SESSIONS = NightlySteps.STEP_FETCH_SESSIONS
        const val STEP_CALC_SCREEN_TIME = NightlySteps.STEP_CALC_SCREEN_TIME
        const val STEP_GENERATE_QUESTIONS = NightlySteps.STEP_GENERATE_QUESTIONS
        const val STEP_CREATE_DIARY_COMPAT = NightlySteps.STEP_CREATE_DIARY
        const val STEP_ANALYZE_REFLECTION = NightlySteps.STEP_ANALYZE_REFLECTION
        const val STEP_FINALIZE_XP = NightlySteps.STEP_FINALIZE_XP
        const val STEP_CREATE_PLAN_DOC = NightlySteps.STEP_CREATE_PLAN_DOC
        const val STEP_GENERATE_PLAN = NightlySteps.STEP_GENERATE_PLAN
        const val STEP_GENERATE_REPORT_COMPAT = NightlySteps.STEP_GENERATE_REPORT
        const val STEP_GENERATE_PDF = NightlySteps.STEP_GENERATE_PDF
        const val STEP_BACKUP_SHEET = NightlySteps.STEP_BACKUP_SHEET
        
        // Legacy aliases (for backward compatibility)
        @Deprecated("Use STEP_FETCH_TASKS") const val STEP_CHECK_DIARY = 1
        @Deprecated("Use STEP_FETCH_SESSIONS") const val STEP_COLLECT_DATA = 2
        @Deprecated("Use STEP_FINALIZE_XP") const val STEP_CALCULATE_XP = 7
        @Deprecated("Use STEP_ANALYZE_REFLECTION") const val STEP_READ_DIARY = 6
        
        // Protocol States
        const val STATE_IDLE = NightlySteps.STATE_IDLE
        const val STATE_CREATING = NightlySteps.STATE_CREATING
        const val STATE_PENDING_REFLECTION = NightlySteps.STATE_PENDING_REFLECTION
        const val STATE_ANALYZING = NightlySteps.STATE_ANALYZING
        const val STATE_COMPLETE = NightlySteps.STATE_COMPLETE
        const val STATE_PLANNING_READY = NightlySteps.STATE_PLANNING_READY
        
        private const val PREFS_NAME = NightlySteps.PREFS_NAME
        
        // Templates
        const val DEFAULT_DIARY_TEMPLATE = NightlySteps.DEFAULT_DIARY_TEMPLATE
        const val DEFAULT_PLAN_TEMPLATE = NightlySteps.DEFAULT_PLAN_TEMPLATE
        
        // Date-keyed helper functions
        fun getStateKey(date: LocalDate): String = NightlySteps.getStateKey(date)
        fun getDiaryDocIdKey(date: LocalDate): String = NightlySteps.getDiaryDocIdKey(date)
        fun getPlanDocIdKey(date: LocalDate): String = NightlySteps.getPlanDocIdKey(date)
        fun getPlanVerifiedKey(date: LocalDate): String = NightlySteps.getPlanVerifiedKey(date)
        
        // Mock loader for agent testing
        fun mockLoadStepDataForAgent(context: android.content.Context, date: java.time.LocalDate, stepId: Int): String? {
            val prefs = context.getSharedPreferences("nightly_diary_${date}", android.content.Context.MODE_PRIVATE)
            return prefs.getString("step_${stepId}_result", null)
        }
        
        // Get state for a specific date
        suspend fun getStateForDate(context: Context, date: LocalDate): Int {
            return NightlyRepository.getSessionStatus(context, date)
        }
        
        // Get diary doc ID for a specific date
        suspend fun getDiaryDocIdForDate(context: Context, date: LocalDate): String? {
            return NightlyRepository.getDiaryDocId(context, date)
        }
        
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
        
        // Clear all protocol memory (States and Diary IDs)
        fun clearMemory(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Remove legacy keys
            editor.remove("protocol_state")
            editor.remove("current_diary_doc_id")
            
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
        
        // Step state loading (delegated to Repository)
        suspend fun loadStepState(context: Context, date: LocalDate, step: Int): com.neubofy.reality.data.nightly.StepProgress {
            return NightlyRepository.loadStepState(context, date, step)
        }
        
        suspend fun loadStepData(context: Context, date: LocalDate, step: Int): com.neubofy.reality.data.repository.StepData {
            return NightlyRepository.loadStepData(context, date, step)
        }
        
        suspend fun saveStepState(
            context: Context, 
            date: LocalDate, 
            step: Int, 
            status: Int, 
            details: String?,
            resultJson: String? = null,
            linkUrl: String? = null
        ) {
            NightlyRepository.saveStepState(context, date, step, status, details, resultJson, linkUrl)
        }

        fun getStepName(step: Int): String = NightlySteps.getStepName(step)
        
        fun getStatusText(status: Int): String = when (status) {
            StepProgress.STATUS_PENDING -> "⏳ Pending"
            StepProgress.STATUS_RUNNING -> "🔄 Running"
            StepProgress.STATUS_COMPLETED -> "✅ Completed"
            StepProgress.STATUS_SKIPPED -> "⏭️ Skipped"
            StepProgress.STATUS_ERROR -> "❌ Error"
            else -> "Unknown"
        }
    }
    
    // Granular Step State Persistence (Legacy alias)
    // Use com.neubofy.reality.data.nightly.StepProgress instead
    @Deprecated("Use com.neubofy.reality.data.nightly.StepProgress")
    data class StepProgress(
        val status: Int,
        val details: String? = null
    ) {
        companion object {
            const val STATUS_PENDING = com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING
            const val STATUS_RUNNING = com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING
            const val STATUS_COMPLETED = com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED
            const val STATUS_SKIPPED = com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED
            const val STATUS_ERROR = com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR
        }
    }
    
    // --- Phase 1: Creation (Steps 1-2) ---
    
    suspend fun startCreationPhase() {
        cleanupOldEntries(context, diaryDate)
        
        try {
            setProtocolState(STATE_CREATING)
            nightlyStartTime = System.currentTimeMillis()
            
            // Step 1: Fetch Analytics
            if (NightlyRepository.isStepEnabled(context, STEP_FETCH_ANALYTICS)) {
                phaseData.step1_fetchAnalytics()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_FETCH_ANALYTICS, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_FETCH_ANALYTICS, "Fetch Analytics", "Disabled in settings")
            }
            
            // Step 2: Create Diary Document
            if (NightlyRepository.isStepEnabled(context, STEP_CREATE_DIARY)) {
                phaseData.step2_createDiary()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_CREATE_DIARY, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_CREATE_DIARY, "Create Diary Document", "Disabled in settings")
            }
            
            // Update internal state
            daySummary = phaseData.daySummary
            diaryDocId = phaseData.diaryDocId
            
            // --- BREAK: End of Phase 1 ---
            setProtocolState(STATE_PENDING_REFLECTION)
            listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
            
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    // --- Phase 2: Analysis (Step 3) ---
    
    suspend fun finishAnalysisPhase() {
        try {
            setProtocolState(STATE_ANALYZING)
            
            // Recover diaryDocId from repo if needed
            diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
            
            if (diaryDocId == null) {
                if (!checkDiaryExists()) {
                     listener.onError(STEP_SAVE_ANALYTICS, "Diary document not found")
                     setProtocolState(STATE_IDLE)
                     return
                }
            }
            
            // Check if analysis is already complete
            val state = Companion.loadStepState(context, diaryDate, STEP_SAVE_ANALYTICS)
            if (state.status == com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED) {
                listener.onStepSkipped(STEP_SAVE_ANALYTICS, "Save Today Analytics", "Skipped (Already completed)")
                setProtocolState(STATE_PLANNING_READY)
                listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
                return
            }
            
            // Step 3: Save Today Analytics
            if (NightlyRepository.isStepEnabled(context, STEP_SAVE_ANALYTICS)) {
                phaseAnalysis.step3_saveAnalytics()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_SAVE_ANALYTICS, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_SAVE_ANALYTICS, "Save Today Analytics", "Disabled in settings")
            }
            
            // --- BREAK: End of Phase 2 ---
            setProtocolState(STATE_PLANNING_READY)
            listener.onStepCompleted(STEP_SAVE_ANALYTICS, "Analysis Complete", "Ready for Planning")
            
        } catch (e: Exception) {
            handleError(e)
        }
    }

    // --- Phase 3 & 4: Planning & Report (Steps 4-6) ---
    
    suspend fun executePlanningPhase() {
        try {
            // Step 4: Create Plan Document
            if (NightlyRepository.isStepEnabled(context, STEP_CREATE_PLAN)) {
                phasePlanning.step4_createPlan()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_CREATE_PLAN, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_CREATE_PLAN, "Create Plan Document", "Disabled in settings")
            }
            
            // Step 5: Apply Plan
            if (NightlyRepository.isStepEnabled(context, STEP_APPLY_PLAN)) {
                phasePlanning.step5_applyPlan()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_APPLY_PLAN, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_APPLY_PLAN, "Apply Plan", "Disabled in settings")
            }
            
            // Step 6: Report
            if (NightlyRepository.isStepEnabled(context, STEP_GENERATE_REPORT)) {
                phasePlanning.step6_generateReport()
            } else {
                Companion.saveStepState(context, diaryDate, STEP_GENERATE_REPORT, StepProgress.STATUS_SKIPPED, "Step disabled")
                listener.onStepSkipped(STEP_GENERATE_REPORT, "Report & Finalize", "Disabled in settings")
            }
            
            setProtocolState(STATE_COMPLETE)
            diaryDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
            listener.onComplete(diaryDocId, diaryDocId?.let { getDiaryUrl(it) })
            
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    // --- Public API for Granular Control ---
    
    suspend fun executeSpecificStep(step: Int) {
        when (step) {
            STEP_FETCH_ANALYTICS -> phaseData.step1_fetchAnalytics()
            STEP_CREATE_DIARY -> phaseData.step2_createDiary()
            STEP_SAVE_ANALYTICS -> phaseAnalysis.step3_saveAnalytics()
            STEP_CREATE_PLAN -> phasePlanning.step4_createPlan()
            STEP_APPLY_PLAN -> phasePlanning.step5_applyPlan()
            STEP_GENERATE_REPORT -> phasePlanning.step6_generateReport()
            else -> throw IllegalArgumentException("Unknown step: $step")
        }
    }
    
    // --- Helper Functions ---
    
    private suspend fun setProtocolState(state: Int) {
        NightlyRepository.updateSessionStatus(context, diaryDate, state)
        TerminalLogger.log("Nightly: State updated to $state for date $diaryDate")
    }
    
    private fun handleError(e: Exception) {
        TerminalLogger.log("Nightly: Error: ${e.message}")
        listener.onError(-1, e.message ?: "Unknown error")
    }
    
    private fun getDiaryTitle(): String {
        return "Diary of ${diaryDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}"
    }
    
    private fun getDiaryUrl(docId: String): String {
        return "https://docs.google.com/document/d/$docId"
    }
    
    private suspend fun checkDiaryExists(): Boolean {
        Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING, "Checking Drive...")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diaryFolderId = prefs.getString("diary_folder_id", null)
        
        if (diaryFolderId.isNullOrEmpty()) {
            val err = "Diary folder not configured"
            listener.onError(STEP_FETCH_TASKS, err)
            Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR, err)
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
                    
                    listener.onStepCompleted(STEP_FETCH_TASKS, "Diary Found", diaryTitle)
                    Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, "Found: $diaryTitle")
                    Companion.saveStepState(context, diaryDate, STEP_CREATE_DIARY, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, diaryTitle)
                    true
                } else {
                    listener.onStepCompleted(STEP_FETCH_TASKS, "No existing diary")
                    Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, "Not found (Will create)")
                    false
                }
            } catch (e: Exception) {
                listener.onError(STEP_FETCH_TASKS, "Check failed: ${e.message}")
                Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR, e.message)
                false
            }
        }
    }
    
    /**
     * Collect day data silently (no UI updates).
     * Delegates to phaseData.
     */
    suspend fun collectDayDataSilently(): DaySummary {
        return phaseData.collectDayDataSilently()
    }
    
    /**
     * Read diary content from Google Docs.
     * Delegates to phaseAnalysis.
     */
    suspend fun readDiaryContent(): String {
        return phaseAnalysis.readDiaryContent()
    }
    
    // --- Debug / UI Support ---
    
    suspend fun getStepDebugData(step: Int): String {
        return withContext(Dispatchers.IO) {
            val stepData = Companion.loadStepData(context, diaryDate, step)
            val rawJson = stepData.resultJson ?: return@withContext "Step: ${Companion.getStepName(step)}\nStatus: ${Companion.getStatusText(stepData.status)}\n\nNo detailed data stored for this step."
            
            val sb = StringBuilder()
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("📌 STEP ${step}: ${Companion.getStepName(step).uppercase()}")
            sb.appendLine("📟 STATUS: ${Companion.getStatusText(stepData.status)}")
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
    
    /**
     * Restores Completed status for Data Collection steps (1 & 2) if DaySummary is valid.
     * This handles cases where user deleted data or re-ran report without running previous steps explicitly.
     */
    private suspend fun restoreCollectedDataState(summary: DaySummary) {
        // Heal Step 1: Tasks
        val fetched = com.neubofy.reality.data.model.FetchedTasks(summary.tasksCompleted, summary.tasksDue)
        val tasksJson = com.google.gson.Gson().toJson(fetched)
        val taskDetails = "Tasks: ${summary.tasksCompleted.size} Done, ${summary.tasksDue.size} Due"
        
        Companion.saveStepState(context, diaryDate, STEP_FETCH_TASKS, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, taskDetails, tasksJson)
        
        // Heal Step 2: Sessions
        val sessionCount = summary.completedSessions.size
        val focusMins = summary.totalEffectiveMinutes
        val sessionDetails = "Sessions: $sessionCount ($focusMins mins)"
        
        Companion.saveStepState(context, diaryDate, STEP_FETCH_SESSIONS, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, sessionDetails, "{}")
    }

    // --- Fallback Questions (REMOVED - AI is always required now) ---
    // getFallbackQuestions() is no longer needed since step4 always uses AI
}
