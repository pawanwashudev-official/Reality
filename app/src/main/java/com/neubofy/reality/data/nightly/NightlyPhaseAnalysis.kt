package com.neubofy.reality.data.nightly

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.google.GoogleDocsManager
import com.neubofy.reality.google.GoogleDriveManager
import com.neubofy.reality.ui.activity.AISettingsActivity
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * Handles Steps 6-7 of the Nightly Protocol:
 * - Step 6: Analyze Reflection (AI)
 * - Step 7: Finalize XP & Stats
 *
 * Key Principle: Uses data from previous steps via database.
 * Depends on Phase Data (Steps 1-5) being complete.
 */
class NightlyPhaseAnalysis(
    private val context: Context,
    private val diaryDate: LocalDate,
    private val listener: NightlyProgressListener
) {
    // In-memory state
    var diaryDocId: String? = null
        private set
    var reflectionXp: Int = 0
        private set
    var daySummary: DaySummary? = null
        private set

    // ========== STEP 6: Analyze Reflection ==========
    suspend fun step6_analyzeReflection() {
        val stepData = loadStepData(NightlySteps.STEP_ANALYZE_REFLECTION)

        // If completed, restore from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                val accepted = output.optBoolean("accepted", false)
                if (accepted) {
                    reflectionXp = output.optInt("xp", 0)
                    TerminalLogger.log("Nightly Phase Analysis: Step 6 restored XP: $reflectionXp")
                    listener.onStepCompleted(NightlySteps.STEP_ANALYZE_REFLECTION, "Reflection Accepted", stepData.details)
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly Phase Analysis: Step 6 JSON parse failed")
            }
        }

        listener.onStepStarted(NightlySteps.STEP_ANALYZE_REFLECTION, "Analyzing Reflection")
        saveStepState(NightlySteps.STEP_ANALYZE_REFLECTION, StepProgress.STATUS_RUNNING, "Reading Diary...")

        try {
            // 1. Get Diary Doc ID from Step 5 result (from DB)
            diaryDocId = getDiaryDocIdFromDB()

            if (diaryDocId == null) {
                throw IllegalStateException("Diary document not found. Please run Steps 1-5 first.")
            }

            // 2. Read Diary Content
            val diaryContent = withContext(Dispatchers.IO) {
                GoogleDocsManager.getDocumentContent(context, diaryDocId!!)
                    ?: throw IllegalStateException("Failed to read diary document")
            }

            if (diaryContent.length < 50) {
                throw IllegalStateException("Diary seems empty. Please write your reflection first.")
            }

            saveStepState(NightlySteps.STEP_ANALYZE_REFLECTION, StepProgress.STATUS_RUNNING, "AI Analyzing...")

            // 3. Get AI Model (REQUIRED - no fallback)
            val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
            val nightlyModel = AISettingsActivity.getNightlyModel(context)

            if (nightlyModel.isNullOrEmpty()) {
                throw IllegalStateException("No AI Model configured. Please set up an AI model in Settings.")
            }

            // 4. AI Analysis
            val result = NightlyAIHelper.analyzeReflection(
                context = context,
                modelString = nightlyModel,
                userIntroduction = userIntro,
                diaryContent = diaryContent
            )

            if (result.satisfied) {
                // Success
                reflectionXp = result.xp
                val details = "Accepted! XP: ${result.xp}. \"${result.feedback}\""

                // Save directly to XPManager AND NightlyRepository
                XPManager.setReflectionXP(context, result.xp, diaryDate.toString())
                NightlyRepository.setReflectionXp(context, diaryDate, result.xp)

                val resultJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("diarySnippet", diaryContent.take(1000) + if (diaryContent.length > 1000) "..." else "")
                        put("model", nightlyModel)
                        put("diaryDocId", diaryDocId)
                    })
                    put("output", JSONObject().apply {
                        put("accepted", true)
                        put("xp", result.xp)
                        put("feedback", result.feedback)
                    })
                }.toString()

                listener.onStepCompleted(NightlySteps.STEP_ANALYZE_REFLECTION, "Reflection Accepted", details)
                saveStepState(NightlySteps.STEP_ANALYZE_REFLECTION, StepProgress.STATUS_COMPLETED, details, resultJson)
            } else {
                // Rejection - Mark as ERROR so Retry button appears
                val errorDetails = "Rejected: ${result.feedback}"
                saveStepState(NightlySteps.STEP_ANALYZE_REFLECTION, StepProgress.STATUS_ERROR, errorDetails)
                listener.onAnalysisFeedback(result.feedback)
            }
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_ANALYZE_REFLECTION, "Analysis Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_ANALYZE_REFLECTION, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 7: Finalize XP & Stats ==========
    suspend fun step7_finalizeXp() {
        val stepData = loadStepData(NightlySteps.STEP_FINALIZE_XP)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_FINALIZE_XP, "XP Finalized", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_FINALIZE_XP, "Finalizing XP & Streak")
        saveStepState(NightlySteps.STEP_FINALIZE_XP, StepProgress.STATUS_RUNNING, "Calculating...")

        try {
            // Fetch Cloud Events for XP calculation
            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val cloudEvents = com.neubofy.reality.google.GoogleCalendarManager.getEvents(context, startOfDay, endOfDay)

            // Map to internal format
            val mappedEvents = cloudEvents.map { event ->
                com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent(
                    id = 0,
                    title = event.summary ?: "No Title",
                    description = event.description,
                    startTime = event.start.dateTime.value,
                    endTime = event.end.dateTime.value,
                    color = 0,
                    location = event.location
                )
            }

            TerminalLogger.log("Nightly Phase Analysis: Step 7 fetched ${mappedEvents.size} cloud events")

            // Recalculate all stats using cloud events
            XPManager.recalculateDailyStats(context, diaryDate.toString(), externalEvents = mappedEvents)

            // Get updated stats
            val finalStats = XPManager.getDailyStats(context, diaryDate.toString())
                ?: throw IllegalStateException("Failed to retrieve stats after calculation")

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
                    put("distractionXp", finalStats.distractionXP)
                    put("penaltyXp", finalStats.penaltyXP)
                    put("totalXp", finalStats.totalDailyXP)
                    put("level", finalStats.level)
                    put("streak", finalStats.streak)
                })
            }.toString()

            val details = "XP: +${finalStats.totalDailyXP} | Level ${finalStats.level} | ${finalStats.streak} Day Streak"
            listener.onStepCompleted(NightlySteps.STEP_FINALIZE_XP, "Day Complete", details)
            saveStepState(NightlySteps.STEP_FINALIZE_XP, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_FINALIZE_XP, "XP Finalization Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_FINALIZE_XP, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== HELPER FUNCTIONS ==========

    private suspend fun loadStepData(step: Int): com.neubofy.reality.data.repository.StepData {
        return NightlyRepository.loadStepData(context, diaryDate, step)
    }

    private suspend fun saveStepState(
        step: Int,
        status: Int,
        details: String?,
        resultJson: String? = null,
        linkUrl: String? = null
    ) {
        NightlyRepository.saveStepState(context, diaryDate, step, status, details, resultJson, linkUrl)
    }

    /**
     * Get Diary Doc ID from Step 5 result (stored in DB).
     * Falls back to SharedPreferences and Drive search if needed.
     */
    private suspend fun getDiaryDocIdFromDB(): String? {
        // 1. Try Step 5 result
        val step5Data = loadStepData(NightlySteps.STEP_CREATE_DIARY)
        if (step5Data.resultJson != null) {
            try {
                val json = JSONObject(step5Data.resultJson)
                val output = json.optJSONObject("output") ?: json
                val docId = output.optString("docId").ifEmpty { json.optString("docId") }
                if (docId.isNotEmpty()) return docId
            } catch (_: Exception) {}
        }

        // 2. Try Repository
        val repoDocId = NightlyRepository.getDiaryDocId(context, diaryDate)
        if (repoDocId != null) return repoDocId

        // 3. Try SharedPreferences
        val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
        val prefsDocId = prefs.getString(NightlySteps.getDiaryDocIdKey(diaryDate), null)
        if (prefsDocId != null) return prefsDocId

        // 4. Search in Drive (last resort)
        val diaryFolderId = prefs.getString("diary_folder_id", null) ?: return null
        val diaryTitle = "Diary of ${diaryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))}"
        val searchResult = withContext(Dispatchers.IO) {
            GoogleDriveManager.searchFile(context, diaryTitle, diaryFolderId)
        }
        if (searchResult != null) {
            // Save for future use
            prefs.edit().putString(NightlySteps.getDiaryDocIdKey(diaryDate), searchResult).apply()
            NightlyRepository.saveDiaryDocId(context, diaryDate, searchResult)
        }
        return searchResult
    }

    /**
     * Read diary content from Google Docs.
     * Uses cached diaryDocId if available.
     */
    suspend fun readDiaryContent(): String {
        listener.onStepStarted(NightlySteps.STEP_ANALYZE_REFLECTION, "Reading Diary")

        if (diaryDocId == null) {
            diaryDocId = getDiaryDocIdFromDB()
        }

        if (diaryDocId == null) {
            throw IllegalStateException("No diary ID found")
        }

        return withContext(Dispatchers.IO) {
            GoogleDocsManager.getDocumentContent(context, diaryDocId!!)
                ?: throw IllegalStateException("Failed to read diary document")
        }
    }

    /**
     * Collect day summary silently (for use if not already cached).
     */
    suspend fun ensureDaySummary() {
        if (daySummary != null) return

        daySummary = withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
            val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)
            val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
            val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)

            // Get tasks from Step 1 DB
            val step1Data = NightlyRepository.loadStepData(context, diaryDate, NightlySteps.STEP_FETCH_TASKS)
            val dueTasks = mutableListOf<String>()
            val completedTasks = mutableListOf<String>()
            if (step1Data.resultJson != null) {
                try {
                    val json = JSONObject(step1Data.resultJson)
                    val output = json.optJSONObject("output") ?: json
                    output.optJSONArray("dueTasks")?.let { arr ->
                        for (i in 0 until arr.length()) dueTasks.add(arr.getString(i))
                    }
                    output.optJSONArray("completedTasks")?.let { arr ->
                        for (i in 0 until arr.length()) completedTasks.add(arr.getString(i))
                    }
                } catch (_: Exception) {}
            }

            val totalPlannedMinutes = calendarEvents.sumOf { (it.endTime - it.startTime) / 60000 }
            val totalEffectiveMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }

            DaySummary(
                date = diaryDate,
                calendarEvents = calendarEvents,
                completedSessions = sessions,
                tasksDue = dueTasks,
                tasksCompleted = completedTasks,
                plannedEvents = plannedEvents,
                totalPlannedMinutes = totalPlannedMinutes,
                totalEffectiveMinutes = totalEffectiveMinutes
            )
        }
    }
}
