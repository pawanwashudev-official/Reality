package com.neubofy.reality.data.nightly

import android.content.Context
import com.neubofy.reality.data.repository.NightlyRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared models, interfaces, and constants for the Nightly Protocol.
 * Extracted from NightlyProtocolExecutor for modularity.
 */

// Progress listener interface
interface NightlyProgressListener {
    fun onStepStarted(step: Int, stepName: String)
    fun onStepCompleted(step: Int, stepName: String, details: String? = null, linkUrl: String? = null)
    fun onStepSkipped(step: Int, stepName: String, reason: String)
    fun onError(step: Int, error: String)
    fun onQuestionsReady(questions: List<String>)
    fun onAnalysisFeedback(feedback: String)
    fun onComplete(diaryDocId: String?, diaryUrl: String?)
    fun onStepLog(step: Int, logLine: String)
}

// Step progress tracking
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

/**
 * Constants and helper functions for the 6-step Nightly Protocol.
 */
object NightlySteps {
    // 6-Step Protocol
    const val STEP_FETCH_ANALYTICS = 1
    const val STEP_CREATE_DIARY = 2
    const val STEP_SAVE_ANALYTICS = 3
    const val STEP_CREATE_PLAN = 4
    const val STEP_APPLY_PLAN = 5
    const val STEP_GENERATE_REPORT = 6

    // Legacy Aliases for seamless compilation
    const val STEP_FETCH_TASKS = STEP_FETCH_ANALYTICS
    const val STEP_FETCH_SESSIONS = STEP_FETCH_ANALYTICS
    const val STEP_CALC_SCREEN_TIME = STEP_FETCH_ANALYTICS
    const val STEP_GENERATE_QUESTIONS = STEP_CREATE_DIARY
    const val STEP_ANALYZE_REFLECTION = STEP_SAVE_ANALYTICS
    const val STEP_FINALIZE_XP = STEP_SAVE_ANALYTICS
    const val STEP_CREATE_PLAN_DOC = STEP_CREATE_PLAN
    const val STEP_GENERATE_PLAN = STEP_APPLY_PLAN
    const val STEP_GENERATE_PDF = STEP_GENERATE_REPORT
    const val STEP_BACKUP_SHEET = STEP_GENERATE_REPORT

    // Protocol States
    const val STATE_IDLE = 0
    const val STATE_CREATING = 1
    const val STATE_PENDING_REFLECTION = 2
    const val STATE_ANALYZING = 3
    const val STATE_COMPLETE = 4
    const val STATE_PLANNING_READY = 5

    // Prefs name
    const val PREFS_NAME = "nightly_prefs"

    // Templates
    const val DEFAULT_DIARY_TEMPLATE = "# 📔 Daily Reflection Diary - {date}\n\n## 📊 Performance & Day Summary Data\n{data}\n\n## 💡 Personalized Reflection Questions\n{questions}\n\n## ✍️ My Reflection & Learning\n(Please write your answers to the reflection questions below each prompt in Google Docs)\n"
    const val DEFAULT_PLAN_TEMPLATE = "# Plan for Tomorrow\n\n## 🎯 Top Priorities\n- [ ] 08:30 Finish project documentation\n- [ ] 14:00 Team sync meeting\n- [ ] 18:00 Gym workout\n\n## 📅 Time-Blocked Schedule\n- 07:00 - 08:00 Wake up & Morning Routine\n- 08:30 - 12:00 Deep Work Focus Session\n- 12:00 - 13:00 Lunch & Rest\n- 13:30 - 17:00 Study Kotlin & Android Development\n- 17:30 - 19:00 Workout & Cardio\n- 22:30 Wind down & Sleep\n\n## 🚀 Tapasya Focus (Deep Study/Work Sessions)\n- 08:30 - 12:00 Coding (3.5 hours)\n- 13:30 - 17:00 Learning (3.5 hours)\n"

    // Key generators for date-specific storage
    fun getStateKey(date: LocalDate): String = "state_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    fun getDiaryDocIdKey(date: LocalDate): String = "diary_doc_id_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    fun getPlanDocIdKey(date: LocalDate): String = "plan_doc_id_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    fun getPlanVerifiedKey(date: LocalDate): String = "plan_verified_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"

    // State management helpers (delegate to Repository)
    suspend fun getStateForDate(context: Context, date: LocalDate): Int {
        return NightlyRepository.getSessionStatus(context, date)
    }

    suspend fun getDiaryDocIdForDate(context: Context, date: LocalDate): String? {
        return NightlyRepository.getDiaryDocId(context, date)
    }

    // Step name for UI/debug
    fun getStepName(step: Int): String = when (step) {
        STEP_FETCH_ANALYTICS -> "Fetch Analytics"
        STEP_CREATE_DIARY -> "Create Diary Document"
        STEP_SAVE_ANALYTICS -> "Save Today Analytics"
        STEP_CREATE_PLAN -> "Create Plan Document"
        STEP_APPLY_PLAN -> "Apply Plan"
        STEP_GENERATE_REPORT -> "Report & Finalize"
        else -> "Unknown Step"
    }

    fun getStatusText(status: Int): String = when (status) {
        StepProgress.STATUS_PENDING -> "⏳ Pending"
        StepProgress.STATUS_RUNNING -> "🔄 Running"
        StepProgress.STATUS_COMPLETED -> "✅ Completed"
        StepProgress.STATUS_SKIPPED -> "⏭️ Skipped"
        StepProgress.STATUS_ERROR -> "❌ Error"
        else -> "Unknown"
    }
}

/**
 * Configuration for customizable prompts.
 * Users can edit these in Nightly Settings.
 */
data class NightlyPromptConfig(
    val questionsPrompt: String? = null,   // custom_ai_prompt
    val analyzerPrompt: String? = null,    // custom_analyzer_prompt
    val planPrompt: String? = null,        // custom_plan_prompt
    val reportPrompt: String? = null       // custom_report_prompt
) {
    companion object {
        fun load(context: Context): NightlyPromptConfig {
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            return NightlyPromptConfig(
                questionsPrompt = prefs.getString("custom_ai_prompt", null),
                analyzerPrompt = prefs.getString("custom_analyzer_prompt", null),
                planPrompt = prefs.getString("custom_plan_prompt", null),
                reportPrompt = prefs.getString("custom_report_prompt", null)
            )
        }

        fun save(context: Context, config: NightlyPromptConfig) {
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (config.questionsPrompt != null) putString("custom_ai_prompt", config.questionsPrompt)
                if (config.analyzerPrompt != null) putString("custom_analyzer_prompt", config.analyzerPrompt)
                if (config.planPrompt != null) putString("custom_plan_prompt", config.planPrompt)
                if (config.reportPrompt != null) putString("custom_report_prompt", config.reportPrompt)
                apply()
            }
        }
    }
}
