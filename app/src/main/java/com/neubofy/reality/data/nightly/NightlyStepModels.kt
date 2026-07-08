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
 * Constants and helper functions for the 13-step Nightly Protocol.
 */
object NightlySteps {
    // 6-Step Protocol
    const val STEP_FETCH_ANALYTICS = 1      // Google Tasks, Calendar, Tapasya, Screen Time
    const val STEP_CREATE_DIARY = 2         // AI Questions, Google Docs Diary
    const val STEP_SAVE_ANALYTICS = 3       // AI Reflection, XP, Google Sheets Backup
    const val STEP_CREATE_PLAN = 4          // Google Docs Plan
    const val STEP_APPLY_PLAN = 5           // AI Parse Plan, Tasks, Calendar, Alarm, Distraction
    const val STEP_REPORT = 6               // AI Report, PDF Generation

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
    const val DEFAULT_DIARY_TEMPLATE = "# 📔 Daily Reflection Diary - {date}\n\n## 📊 Day Summary Data\n{data}\n\n## 💡 Personalized Questions\n{questions}\n\n## ✍️ My Reflection\n(Write your answers here...)\n"
    const val DEFAULT_PLAN_TEMPLATE = "# My Plan for Tomorrow\n\n## 🎯 Top Priorities\n- [ ] \n\n## 📅 Schedule\n- \n\n## 🚀 Tapasya Focus\n- \n"
    
    const val DEFAULT_TASK_NORMALIZER_TEMPLATE = """You are a smart Task Manager Agent. 
Your goal is to clean up a user's task list for the Next Planning Day: {target_date}.

INPUT DATA (Existing Tasks):
{tasks_json}

{list_context}

[STRICT CLEANUP RULES]
1. IDENTIFY DUPLICATES:
   - Identify tasks with same/similar titles (e.g., "Buy milk" and "Get milk").
   - Mark multiple occurrences for DELETION.
   
2. TASK LIST CORRECTION:
   - Check if each task is in the most appropriate list based on the [AVAILABLE TASK LISTS] descriptions.
   - If a task is in the wrong list, mark it for DELETION and create a RE-ADD entry for the correct list.

3. DUE TIME EXTRACTION:
   - Extract the intended time for the task as "startTime" in 24h format (HH:mm).
   - IMPORTANT: If no time is explicitly mentioned, use "00:00" as a default.
   - Strip any time prefix/suffix from the "title" field.

4. RESCHEDULE:
   - All tasks being re-added MUST have their due date set to {target_date}.
   - Formatting: RFC 3339 timestamp (e.g., 2024-01-30T00:00:00.000Z).

[JSON OUTPUT FORMAT]
(Return ONLY valid JSON. No markdown. No preamble.)
{
  "delete_ids": ["task_id_1", "task_id_2"],
  "readd_tasks": [
    { 
      "title": "Clean Title", 
      "startTime": "HH:mm (ALWAYS REQUIRED)", 
      "taskListId": "EXACT_ID_FROM_CONTEXT", 
      "notes": "Original notes" 
    }
  ]
}"""

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
        STEP_CREATE_DIARY -> "Create Diary"
        STEP_SAVE_ANALYTICS -> "Save Analytics"
        STEP_CREATE_PLAN -> "Create Plan"
        STEP_APPLY_PLAN -> "Apply Plan"
        STEP_REPORT -> "Generate Report"
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
