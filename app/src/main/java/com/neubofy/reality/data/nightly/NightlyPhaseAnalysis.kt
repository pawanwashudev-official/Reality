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
import org.json.JSONArray
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

    // ========== STEP 3: Save Today Analytics ==========
    suspend fun step3_saveAnalytics() {
        val stepData = loadStepData(NightlySteps.STEP_SAVE_ANALYTICS)

        // If completed, restore from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                reflectionXp = output.optInt("reflectionXp", 0)
                TerminalLogger.log("Nightly Phase Analysis: Step 3 restored XP: $reflectionXp")
                listener.onStepCompleted(NightlySteps.STEP_SAVE_ANALYTICS, "Analytics Saved", stepData.details)
                return
            } catch (e: Exception) {
                TerminalLogger.log("Nightly Phase Analysis: Step 3 JSON parse failed")
            }
        }

        listener.onStepStarted(NightlySteps.STEP_SAVE_ANALYTICS, "Saving Analytics")
        saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_RUNNING, "Reading diary reflection...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Reading reflection from Google Doc...", listener)

        try {
            diaryDocId = getDiaryDocIdFromDB()
            if (diaryDocId == null) {
                throw IllegalStateException("Diary document not found. Please run Step 2 first.")
            }

            val diaryContent = withContext(Dispatchers.IO) {
                GoogleDocsManager.getDocumentContent(context, diaryDocId!!)
                    ?: throw IllegalStateException("Failed to read diary document content")
            }

            if (diaryContent.length < 50) {
                throw IllegalStateException("Diary content is too short. Please write your reflection first.")
            }

            saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_RUNNING, "AI Grading Reflection...")
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Analyzing reflection via AI...", listener)

            val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
            val nightlyModel = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs").getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"

            if (nightlyModel.isEmpty()) {
                throw IllegalStateException("No AI Model configured for grading.")
            }

            val result = NightlyAIHelper.analyzeReflection(
                context = context,
                modelString = nightlyModel,
                userIntroduction = userIntro,
                diaryContent = diaryContent
            )

            if (!result.satisfied) {
                val errorDetails = "Rejected: ${result.feedback}"
                saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_ERROR, errorDetails)
                listener.onAnalysisFeedback(result.feedback)
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "AI reflection check failed: ${result.feedback}", listener)
                return
            }

            reflectionXp = result.xp
            XPManager.setReflectionXP(context, result.xp, diaryDate.toString())
            NightlyRepository.setReflectionXp(context, diaryDate, result.xp)
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "AI Grading complete: Earned $reflectionXp XP. Feedback: ${result.feedback}", listener)

            // Recalculate daily stats
            saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_RUNNING, "Finalizing XP & Streak...")
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Fetching cloud calendar events for finalized XP...", listener)

            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val cloudEvents = com.neubofy.reality.google.GoogleCalendarManager.getEvents(context, startOfDay, endOfDay)
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

                // Get Step 1 output
                val step1Data = loadStepData(NightlySteps.STEP_FETCH_ANALYTICS)
                val j1 = try {
                    if (step1Data.resultJson != null) JSONObject(step1Data.resultJson).optJSONObject("output") ?: JSONObject() else JSONObject()
                } catch (e: Exception) {
                    JSONObject()
                }
                
                // Reconstruct TaskStats to avoid fetching from network again
                val dueArr = j1.optJSONArray("dueTasks") ?: JSONArray()
                val compArr = j1.optJSONArray("completedTasks") ?: JSONArray()
                val dueList = (0 until dueArr.length()).map { dueArr.getString(it) }
                val compList = (0 until compArr.length()).map { compArr.getString(it) }
                
                val fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(
                    dueTasks = dueList,
                    completedTasks = compList,
                    pendingCount = j1.optInt("pendingCount", 0),
                    completedCount = j1.optInt("completedCount", 0)
                )

                XPManager.recalculateDailyStats(context, diaryDate.toString(), externalEvents = mappedEvents, fetchedTasks = fetchedTasks)
                val finalStats = XPManager.getDailyStats(context, diaryDate.toString())
                    ?: throw IllegalStateException("Failed to calculate stats")

                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "XP calculation complete. Total XP: ${finalStats.totalDailyXP}, Level: ${finalStats.level}, Streak: ${finalStats.streak} days.", listener)

                // Sheet backup sub-permission
                val saveToSheet = NightlyRepository.isSubFeatureEnabled(context, "save_to_reality_sheet")
                var sheetSuccess = false
                val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
                val sheetId = prefs.getString("reality_sheet_id", null)

                if (saveToSheet && !sheetId.isNullOrEmpty()) {
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Backing up today's stats to Reality Sheet in Drive...", listener)

                    val totalDue = j1.optInt("pendingCount", 0) + j1.optInt("completedCount", 0)
                    val totalComp = j1.optInt("completedCount", 0)

                val rowDataMap = mapOf(
                    "Date" to diaryDate.toString(),
                    "Tasks Completed" to totalComp.toString(),
                    "Total Tasks" to totalDue.toString(),
                    "Planned Sessions" to j1.optInt("plannedEventCount", 0).toString(),
                    "Tapasya Sessions" to j1.optInt("sessionCount", 0).toString(),
                    "Steps" to j1.optLong("steps", 0L).toString(),
                    "Sleep Info" to j1.optString("sleepInfo", "N/A"),
                    "XP Tapasya" to finalStats.tapasyaXP.toString(),
                    "XP Task" to finalStats.taskXP.toString(),
                    "XP Session" to finalStats.sessionXP.toString(),
                    "XP Distraction" to finalStats.distractionXP.toString(),
                    "XP Reflection" to finalStats.reflectionXP.toString(),
                    "XP Total" to finalStats.totalDailyXP.toString(),
                    "Level" to finalStats.level.toString(),
                    "Streak" to finalStats.streak.toString(),
                    "Diary Feedback" to result.feedback
                )

                val currentHeaders = com.neubofy.reality.google.GoogleSheetsManager.getHeaders(context, sheetId)
                val requiredHeaders = com.neubofy.reality.google.GoogleSheetsManager.REQUIRED_HEADERS
                val missingHeaders = requiredHeaders.filter { it !in currentHeaders }

                if (missingHeaders.isNotEmpty()) {
                    val errorMsg = "Error: Sheet columns are missing or invalid. Please disconnect and reconnect your sheet in Profile Settings, or delete it and create a new one."
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, errorMsg, listener)
                    sheetSuccess = false
                } else {
                    val rowValues = MutableList<Any>(currentHeaders.size) { "" }
                    for ((index, header) in currentHeaders.withIndex()) {
                        rowValues[index] = rowDataMap[header] ?: ""
                    }

                    sheetSuccess = withContext(Dispatchers.IO) {
                        try {
                            val credential = com.neubofy.reality.google.GoogleAuthManager.getGoogleCredential(context)
                            val service = credential?.let {
                                com.google.api.services.sheets.v4.Sheets.Builder(
                                    com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                                    it
                                ).setApplicationName("Reality").build()
                            }
                            if (service != null) {
                                val body = com.google.api.services.sheets.v4.model.ValueRange().setValues(listOf(rowValues as List<Any>))
                                service.spreadsheets().values()
                                    .append(sheetId, "Sheet1", body)
                                    .setValueInputOption("USER_ENTERED")
                                    .execute()
                                true
                            } else false
                        } catch (e: Exception) {
                            TerminalLogger.log("Sheets backup failed: ${e.message}")
                            false
                        }
                    }
                }
                
                if (sheetSuccess) {
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Successfully appended row to Reality Sheet.", listener)
                } else {
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Warning: Failed to append row to Reality Sheet.", listener)
                }
            } else if (saveToSheet) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Sheet backup skipped: Reality Sheet ID not configured.", listener)
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Sheet backup skipped by user setting.", listener)
            }

            val details = "XP: +${finalStats.totalDailyXP} | Level ${finalStats.level} | ${finalStats.streak} Day Streak"

            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("diaryDocId", diaryDocId)
                    put("model", nightlyModel)
                })
                put("output", JSONObject().apply {
                    put("accepted", true)
                    put("reflectionXp", reflectionXp)
                    put("totalXp", finalStats.totalDailyXP)
                    put("level", finalStats.level)
                    put("streak", finalStats.streak)
                    put("feedback", result.feedback)
                    put("sheetBackup", sheetSuccess)
                })
            }.toString()

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Analytics saved successfully.", listener)
            listener.onStepCompleted(NightlySteps.STEP_SAVE_ANALYTICS, "Analytics Saved", details)
            saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_SAVE_ANALYTICS, "Error saving analytics: ${e.message}", listener)
            listener.onError(NightlySteps.STEP_SAVE_ANALYTICS, "Analysis Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_SAVE_ANALYTICS, StepProgress.STATUS_ERROR, e.message)
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
