package com.neubofy.reality.data.nightly

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.google.GoogleDocsManager
import com.neubofy.reality.google.GoogleDriveManager
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.ui.activity.AISettingsActivity
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.json.JSONArray
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.client.http.FileContent
import java.io.File

/**
 * Handles Steps 8-13 of the Nightly Protocol:
 * - Step 8: Create Plan Document
 * - Step 9: AI Parse Plan to JSON
 * - Step 10: Create Google Tasks & Calendar Events
 * - Step 11: Generate AI Report
 * - Step 12: Create PDF Report
 * - Step 13: Set Wake-up Alarm
 *
 * Key Principle: Uses data from previous steps via database.
 * Steps are designed to be resumable - each checks DB for existing completion.
 */
class NightlyPhasePlanning(
    private val context: Context,
    private val diaryDate: LocalDate,
    private val listener: NightlyProgressListener
) {
    // Helpers
    private val reporter = NightlyReporter(context)

    // In-memory state
    var planDocId: String? = null
        private set
    var daySummary: DaySummary? = null
        private set

    // ========== STEP 8: Create Plan Document ==========
    suspend fun step8_createPlanDoc() {
        val stepData = loadStepData(NightlySteps.STEP_CREATE_PLAN_DOC)

        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                val savedDocId = output.optString("docId").ifEmpty { json.optString("docId") }
                val savedUrl = output.optString("docUrl").ifEmpty { json.optString("docUrl") }

                if (savedDocId.isNotEmpty()) {
                    planDocId = savedDocId
                    TerminalLogger.log("Nightly Phase Planning: Step 8 restored planDocId: $savedDocId")
                    if (savedUrl.isNotEmpty()) {
                        listener.onStepCompleted(NightlySteps.STEP_CREATE_PLAN_DOC, "Plan Doc Ready", "Restored", savedUrl)
                    }
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly Phase Planning: Step 8 JSON parse failed")
            }
        }

        listener.onStepStarted(NightlySteps.STEP_CREATE_PLAN_DOC, "Creating Plan Document")
        saveStepState(NightlySteps.STEP_CREATE_PLAN_DOC, StepProgress.STATUS_RUNNING, "Creating...")

        try {
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            var planFolderId = prefs.getString("plan_folder_id", null)
            val diaryFolderId = prefs.getString("diary_folder_id", null)
            val realityFolderId = prefs.getString("reality_folder_id", null)

            // Dynamic folder resolution
            if (planFolderId.isNullOrEmpty() && !realityFolderId.isNullOrEmpty()) {
                val credential = GoogleAuthManager.getGoogleAccountCredential(context)
                if (credential != null) {
                    val driveService = Drive.Builder(GoogleAuthManager.getHttpTransport(), GoogleAuthManager.getJsonFactory(), credential)
                        .setApplicationName("Reality").build()
                    val folders = withContext(Dispatchers.IO) {
                        val result = driveService.files().list()
                            .setQ("'$realityFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                            .setOrderBy("name")
                            .setFields("files(id, name)")
                            .execute()
                        result.files ?: emptyList()
                    }
                    if (folders.size >= 2) {
                        planFolderId = folders[1].id
                        TerminalLogger.log("Nightly Phase Planning: Dynamic Plan folder resolved: ${folders[1].name}")
                    }
                }
            }

            if (planFolderId.isNullOrEmpty()) planFolderId = diaryFolderId

            val nextDay = diaryDate.plusDays(1)
            val title = "Plan - ${nextDay.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"
            val template = prefs.getString("template_plan", NightlySteps.DEFAULT_PLAN_TEMPLATE) ?: NightlySteps.DEFAULT_PLAN_TEMPLATE
            val content = template
                .replace("{date}", nextDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
                .replace("{data}", "[Plan Details]")

            // Search or create
            var docId = prefs.getString(NightlySteps.getPlanDocIdKey(diaryDate), null)

            if (docId == null && planFolderId != null) {
                docId = withContext(Dispatchers.IO) {
                    GoogleDriveManager.searchFile(context, title, planFolderId)
                }
                if (docId != null) {
                    TerminalLogger.log("Nightly Phase Planning: Found existing plan '$title' (ID: $docId)")
                    prefs.edit().putString(NightlySteps.getPlanDocIdKey(diaryDate), docId).apply()
                    NightlyRepository.savePlanDocId(context, diaryDate, docId)
                }
            }

            val docUrl: String

            if (docId != null) {
                val existingDocId = docId  // Local immutable copy for closures
                val currentContent = withContext(Dispatchers.IO) {
                    GoogleDocsManager.getDocumentContent(context, existingDocId) ?: ""
                }
                val hasRawTemplate = currentContent.contains("{date}") || currentContent.contains("{data}")

                if (currentContent.length < 50 || hasRawTemplate) {
                    withContext(Dispatchers.IO) {
                        GoogleDocsManager.appendText(context, existingDocId, "\n" + content)
                    }
                    TerminalLogger.log("Nightly Phase Planning: Injected template into existing empty plan")
                }
                docUrl = "https://docs.google.com/document/d/$existingDocId"
            } else {
                val newDocId = withContext(Dispatchers.IO) {
                    GoogleDocsManager.createDocument(context, title)
                }
                if (newDocId != null) {
                    docId = newDocId
                    if (planFolderId != null) {
                        withContext(Dispatchers.IO) {
                            GoogleDriveManager.moveFileToFolder(context, newDocId, planFolderId)
                        }
                    }
                    withContext(Dispatchers.IO) {
                        GoogleDocsManager.appendText(context, newDocId, content)
                    }
                    docUrl = "https://docs.google.com/document/d/$newDocId"

                    prefs.edit().putString(NightlySteps.getPlanDocIdKey(diaryDate), newDocId).apply()
                    NightlyRepository.savePlanDocId(context, diaryDate, newDocId)
                } else {
                    throw IllegalStateException("Failed to create Plan Doc ID")
                }
            }

            planDocId = docId

            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("title", title)
                    put("folderId", planFolderId)
                })
                put("output", JSONObject().apply {
                    put("docId", docId)
                    put("docUrl", docUrl)
                })
            }.toString()

            listener.onStepCompleted(NightlySteps.STEP_CREATE_PLAN_DOC, "Plan Doc Ready", "Ready to Edit", docUrl)
            saveStepState(NightlySteps.STEP_CREATE_PLAN_DOC, StepProgress.STATUS_COMPLETED, "Ready to Edit", resultJson, docUrl)
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_CREATE_PLAN_DOC, "Failed to create plan: ${e.message}")
            saveStepState(NightlySteps.STEP_CREATE_PLAN_DOC, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 9: AI Parse Plan to JSON ==========
    suspend fun step9_generatePlan() {
        val stepData = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_PLAN, "Plan Parsed", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_GENERATE_PLAN, "AI Parsing Plan")
        saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Reading plan...")

        try {
            val nightlyModel = AISettingsActivity.getNightlyModel(context)

            if (nightlyModel.isNullOrEmpty()) {
                listener.onStepSkipped(NightlySteps.STEP_GENERATE_PLAN, "Plan AI", "No AI Model Configured")
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_SKIPPED, "No Model")
                return
            }

            // Get Plan Doc ID from Step 8 (from DB)
            val planId = getPlanDocIdFromDB()

            if (planId == null) {
                listener.onError(NightlySteps.STEP_GENERATE_PLAN, "Plan Document missing. Run Step 8 first.")
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, "No Plan Doc")
                return
            }

            val planContent = withContext(Dispatchers.IO) {
                GoogleDocsManager.getDocumentContent(context, planId) ?: ""
            }

            if (planContent.length < 20) {
                val errorDetails = "Plan too short. Please write your tasks and schedule in the plan document."
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                listener.onAnalysisFeedback(errorDetails)
                return
            }

            saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "AI parsing...")

            // Fetch Task List Configs for AI context
            val taskListConfigs = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            }
            val aiResponse = NightlyAIHelper.analyzePlan(context, nightlyModel, planContent, taskListConfigs)

            // Validate JSON with robust extraction
            try {
                val start = aiResponse.indexOf('{')
                val end = aiResponse.lastIndexOf('}')

                val jsonStr = if (start != -1 && end != -1 && end > start) {
                    aiResponse.substring(start, end + 1)
                } else {
                    aiResponse
                }

                val json = JSONObject(jsonStr.trim())

                val tasks = json.optJSONArray("tasks") ?: JSONArray()
                val events = json.optJSONArray("events") ?: JSONArray()

                if (tasks.length() == 0 && events.length() == 0) {
                    val errorDetails = "Could not extract tasks or events from your plan. Please write clearer items with times."
                    saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                    listener.onAnalysisFeedback(errorDetails)
                    return
                }

                val details = "${tasks.length()} tasks, ${events.length()} events extracted"
                val mentorship = json.optString("mentorship", "No advice generated.")
                val wakeupTime = json.optString("wakeupTime", "")
                val sleepStartTime = json.optString("sleepStartTime", "")
                val distractionTimeMinutes = json.optInt("distractionTimeMinutes", 60)

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
                        put("distractionTimeMinutes", distractionTimeMinutes)
                    })
                    // Legacy compatibility
                    put("tasks", tasks)
                    put("events", events)
                    put("mentorship", mentorship)
                    put("wakeupTime", wakeupTime)
                    put("sleepStartTime", sleepStartTime)
                    put("distractionTimeMinutes", distractionTimeMinutes)
                    put("rawResponse", aiResponse)
                    put("sanitizedJson", jsonStr)
                }.toString()

                listener.onStepCompleted(NightlySteps.STEP_GENERATE_PLAN, "Plan Parsed", details)
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)
            } catch (e: Exception) {
                val errorDetails = "AI response was not valid JSON. Please try again or simplify your plan."
                TerminalLogger.log("Step 9 JSON parse error: ${e.message}, Raw: $aiResponse")

                val failureJson = JSONObject().apply {
                    put("rawResponse", aiResponse)
                    put("error", e.message)
                }.toString()

                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails, failureJson)
                listener.onAnalysisFeedback(errorDetails)
            }
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_GENERATE_PLAN, "AI Plan Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 10: Create Google Tasks & Calendar Events ==========
    suspend fun step10_processPlan() {
        val stepData = loadStepData(NightlySteps.STEP_PROCESS_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            listener.onStepCompleted(NightlySteps.STEP_PROCESS_PLAN, "Plan Processed", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_PROCESS_PLAN, "Processing Plan to Tasks & Events")
        saveStepState(NightlySteps.STEP_PROCESS_PLAN, StepProgress.STATUS_RUNNING, "Processing...")

        try {
            // Get JSON from Step 9 (from DB)
            val step9Data = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
            if (step9Data.resultJson == null) {
                throw IllegalStateException("Step 9 not completed. Run AI Plan first.")
            }

            val step9Json = JSONObject(step9Data.resultJson)
            val tasks = step9Json.optJSONArray("tasks") ?: JSONArray()
            val events = step9Json.optJSONArray("events") ?: JSONArray()
            val wakeupTime = step9Json.optString("wakeupTime")
            val sleepStartTime = step9Json.optString("sleepStartTime")

            // Persist sleep/wake times
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("planner_wakeup_time", wakeupTime)
                putString("planner_sleep_time", sleepStartTime)
                if (sleepStartTime.isNotEmpty()) {
                    TerminalLogger.log("Nightly Phase Planning: Saved planned sleep time: $sleepStartTime")
                }
            }.apply()

            val createdItems = JSONArray()
            val nextDay = diaryDate.plusDays(1)

            var tasksCreated = 0
            var eventsCreated = 0

            // Load Task List Configs
            val listConfigs = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            }

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
                        // Resolve List ID
                        var finalTaskListId = "@default"
                        val matchedConfig = listConfigs.find {
                            it.googleListId == rawListId || it.displayName.equals(rawListId, ignoreCase = true)
                        }

                        if (matchedConfig != null) {
                            finalTaskListId = matchedConfig.googleListId
                        } else if (rawListId != "@default" && rawListId.isNotEmpty()) {
                            taskLog.put("warning", "List '$rawListId' not found. Used Default.")
                            TerminalLogger.log("Nightly Phase Planning: List '$rawListId' not found. Fallback to @default.")
                        }

                        val finalTitle = if (!startTime.isNullOrEmpty()) "$startTime|$title" else title
                        val dueDate = nextDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T09:00:00.000Z"

                        val createdTask = withContext(Dispatchers.IO) {
                            com.neubofy.reality.google.GoogleTasksManager.createTask(context, finalTitle, notes, dueDate, finalTaskListId)
                        }

                        if (createdTask != null) {
                            tasksCreated++
                            taskLog.put("status", "SUCCESS")
                            taskLog.put("finalTitle", finalTitle)
                            taskLog.put("finalList", finalTaskListId)
                            TerminalLogger.log("Nightly Phase Planning: Created task - $finalTitle in list $finalTaskListId")
                        } else {
                            taskLog.put("status", "FAILED (API Error)")
                        }
                    } else {
                        taskLog.put("status", "FAILED (Empty Title)")
                    }
                } catch (e: Exception) {
                    taskLog.put("status", "ERROR: ${e.message}")
                    TerminalLogger.log("Nightly Phase Planning: Failed to process task at index $i - ${e.message}")
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
                    val startTimeStr = eventObj.optString("startTime", "")
                    val endTimeStr = eventObj.optString("endTime", "")
                    val description = eventObj.optString("description")

                    eventLog.put("inputTitle", title)
                    eventLog.put("inputTime", "$startTimeStr - $endTimeStr")

                    if (title.isNotEmpty() && startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty()) {
                        val startParts = startTimeStr.split(":")
                        val endParts = endTimeStr.split(":")

                        if (startParts.size >= 2 && endParts.size >= 2) {
                            val startMs = nextDay.atTime(startParts[0].toInt(), startParts[1].toInt())
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endMs = nextDay.atTime(endParts[0].toInt(), endParts[1].toInt())
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                            val eventId = withContext(Dispatchers.IO) {
                                com.neubofy.reality.google.GoogleCalendarManager.createEvent(
                                    context, title, startMs, endMs, description
                                )
                            }

                            if (eventId != null) {
                                eventsCreated++
                                eventLog.put("status", "SUCCESS")
                                TerminalLogger.log("Nightly Phase Planning: Created Cloud Calendar event - $title (ID: $eventId)")
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
                    TerminalLogger.log("Nightly Phase Planning: Failed to create calendar event at index $i - ${e.message}")
                }
                eventLog.put("type", "EVENT")
                createdItems.put(eventLog)
            }

            val details = "$tasksCreated tasks, $eventsCreated events created"

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
            }.toString()

            listener.onStepCompleted(NightlySteps.STEP_PROCESS_PLAN, "Plan Processed", details)
            saveStepState(NightlySteps.STEP_PROCESS_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)

            // Sync Bedtime
            syncBedtime(wakeupTime, sleepStartTime)
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_PROCESS_PLAN, "Process Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_PROCESS_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 11: Generate AI Report ==========
    suspend fun step11_generateReport() {
        val stepData = loadStepData(NightlySteps.STEP_GENERATE_REPORT)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            TerminalLogger.log("Nightly Phase Planning: Step 11 already completed for $diaryDate")
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_REPORT, "Report Generated", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_GENERATE_REPORT, "Generating Report")
        saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Collecting day summary...")

        try {
            // Recover Day Summary from Phase Data steps
            ensureDaySummary()
            val summary = daySummary ?: throw IllegalStateException("Day data not available")
            TerminalLogger.log("Nightly Phase Planning: Step 11 Summary recovered (${summary.tasksCompleted.size} tasks done)")

            // Recover Doc IDs from previous steps
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Retrieving document IDs...")
            val dId = getDiaryDocIdFromDB()
            val pId = getPlanDocIdFromDB()

            if (dId == null) TerminalLogger.log("Nightly Phase Planning: Step 11 WARNING - Diary Doc ID is missing")
            if (pId == null) TerminalLogger.log("Nightly Phase Planning: Step 11 WARNING - Plan Doc ID is missing")

            // Fetch Diary Content
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Fetching Diary content...")
            val diaryContent = withContext(Dispatchers.IO) {
                if (dId != null) {
                    GoogleDocsManager.getDocumentContent(context, dId) ?: ""
                } else ""
            }

            // Fetch Plan Content
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Fetching Plan content...")
            val planContent = withContext(Dispatchers.IO) {
                if (pId != null) {
                    GoogleDocsManager.getDocumentContent(context, pId) ?: ""
                } else ""
            }

            if (diaryContent.isEmpty() && planContent.isEmpty()) {
                throw Exception("Both Diary and Plan are empty. Please ensure documents have content (Diary ID: $dId, Plan ID: $pId).")
            }

            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "AI Generating report...")
            TerminalLogger.log("Nightly Phase Planning: Step 11 Prompting AI with ${diaryContent.length} chars diary and ${planContent.length} chars plan")

            // Generate report using AI
            val report = reporter.generateReport(diaryDate, summary, diaryContent, planContent)

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
                put("diaryDocId", dId ?: "N/A")
                put("planDocId", pId ?: "N/A")
            }.toString()

            val details = "Report generated (${report.length} chars)"
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_REPORT, "Report Generated", details)
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_COMPLETED, details, resultJson)

            // Notification
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: Report Ready", "Analysis complete. Tap to view.", NightlySteps.STEP_GENERATE_REPORT)
        } catch (e: Exception) {
            val err = "Step 11 Error: ${e.message}"
            TerminalLogger.log("Nightly Phase Planning: ERROR (Step 11): $err")
            listener.onError(NightlySteps.STEP_GENERATE_REPORT, err)
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_ERROR, err)

            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: Failed", "Report generation failed.", NightlySteps.STEP_GENERATE_REPORT)
        }
    }

    // ========== STEP 12: Generate PDF Report ==========
    suspend fun step12_generatePdf() {
        val stepData = loadStepData(NightlySteps.STEP_GENERATE_PDF)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_PDF, "PDF Created", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_GENERATE_PDF, "Creating PDF Report")
        saveStepState(NightlySteps.STEP_GENERATE_PDF, StepProgress.STATUS_RUNNING, "Creating PDF...")

        try {
            // Get report content from Step 11
            val reportContent = NightlyRepository.getReportContent(context, diaryDate)

            if (reportContent.isNullOrEmpty()) {
                val err = "No report content available (Step 11 may have failed)"
                listener.onStepSkipped(NightlySteps.STEP_GENERATE_PDF, "PDF Skipped", err)
                saveStepState(NightlySteps.STEP_GENERATE_PDF, StepProgress.STATUS_SKIPPED, err)
                return
            }

            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            val reportFolderId = prefs.getString("report_folder_id", null)

            if (reportFolderId.isNullOrEmpty()) {
                val err = "Report folder not configured"
                listener.onError(NightlySteps.STEP_GENERATE_PDF, err)
                saveStepState(NightlySteps.STEP_GENERATE_PDF, StepProgress.STATUS_ERROR, err)
                return
            }

            val dateFormatted = diaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val title = "Reality Report - $dateFormatted"
            val fileName = "Reality_Report_$dateFormatted"

            // Generate PDF
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

            // Save PDF ID
            NightlyRepository.saveReportPdfId(context, diaryDate, pdfFileId)

            val details = "PDF uploaded: $pdfFileId"
            val pdfUrl = "https://drive.google.com/file/d/$pdfFileId/view"
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_PDF, "PDF Created", details, pdfUrl)

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
            saveStepState(NightlySteps.STEP_GENERATE_PDF, StepProgress.STATUS_COMPLETED, details, resultJson)

            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: PDF Saved", "Report uploaded to Drive.", NightlySteps.STEP_GENERATE_PDF)
        } catch (e: Exception) {
            val err = "PDF generation failed: ${e.message}"
            TerminalLogger.log("Nightly Phase Planning: $err")
            e.printStackTrace()
            listener.onError(NightlySteps.STEP_GENERATE_PDF, err)
            saveStepState(NightlySteps.STEP_GENERATE_PDF, StepProgress.STATUS_ERROR, err)

            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: PDF Error", "Failed to create/upload PDF.", NightlySteps.STEP_GENERATE_PDF)
        }
    }

    // ========== STEP 13: Set Wake-up Alarm ==========
    suspend fun step13_setAlarm() {
        val stepData = loadStepData(NightlySteps.STEP_SET_ALARM)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_SET_ALARM, "Alarm Scheduled", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_SET_ALARM, "Setting Wake-up Alarm")
        saveStepState(NightlySteps.STEP_SET_ALARM, StepProgress.STATUS_RUNNING, "Configuring...")

        try {
            // Use data from Step 9
            val step9Data = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
            if (step9Data.resultJson == null) {
                throw IllegalStateException("Step 9 (AI Plan) not completed.")
            }

            val step9Json = JSONObject(step9Data.resultJson)
            val wakeupTime = step9Json.optString("wakeupTime")

            if (wakeupTime.isEmpty()) {
                val details = "No wakeup time in AI plan"
                listener.onStepCompleted(NightlySteps.STEP_SET_ALARM, "Alarm Skipped", details)
                saveStepState(NightlySteps.STEP_SET_ALARM, StepProgress.STATUS_COMPLETED, details)
                return
            }

            val parts = wakeupTime.split(":")
            if (parts.size != 2) throw IllegalArgumentException("Invalid wakeup time format: $wakeupTime")

            val hour = parts[0].toInt()
            val min = parts[1].toInt()
            val nextDay = diaryDate.plusDays(1)

            val alarmTime = nextDay.atTime(hour, min)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                ?: throw IllegalStateException("AlarmManager not available")

            // Check permission for Android 12+
            val canSchedule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (!canSchedule) {
                throw IllegalStateException("Alarm permission missing")
            }

            val alarmIntent = android.content.Intent(context, com.neubofy.reality.receivers.ReminderReceiver::class.java).apply {
                putExtra("id", "nightly_wakeup")
                putExtra("title", "Wake Up (Reality Plan)")
                putExtra("mins", 0)
                putExtra("source", "NIGHTLY")
                putExtra("url", "reality://smart_sleep")
                putExtra("snoozeEnabled", true)
                putExtra("autoSnoozeEnabled", true)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                777,
                alarmIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            }

            // Sync to CustomReminders for UI
            val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
            val reminders = prefsLoader.loadCustomReminders()
            reminders.removeAll { it.id == "nightly_wakeup" }
            reminders.add(com.neubofy.reality.data.CustomReminder(
                id = "nightly_wakeup",
                title = "Wake Up (AI Plan)",
                hour = hour,
                minute = min,
                isEnabled = true,
                repeatDays = emptyList(),
                offsetMins = 0,
                retryIntervalMins = 5,
                snoozeEnabled = true,
                autoSnoozeEnabled = true,
                redirectUrl = "reality://smart_sleep",
                urlSource = 2
            ))
            prefsLoader.saveCustomReminders(reminders)

            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("date", diaryDate.toString())
                    put("wakeupTime", wakeupTime)
                })
                put("output", JSONObject().apply {
                    put("hour", hour)
                    put("minute", min)
                    put("scheduledAt", System.currentTimeMillis())
                    put("url", "reality://smart_sleep")
                })
            }.toString()

            val details = "Scheduled for $wakeupTime"
            listener.onStepCompleted(NightlySteps.STEP_SET_ALARM, "Alarm Scheduled", details)
            saveStepState(NightlySteps.STEP_SET_ALARM, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            TerminalLogger.log("Nightly Step 13 Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_SET_ALARM, StepProgress.STATUS_ERROR, e.message)
            listener.onError(NightlySteps.STEP_SET_ALARM, "Alarm Setup Failed: ${e.message}")
        }
    }

    // ========== STEP 14: AI Task Cleanup ==========
    suspend fun step14_normalizeTasks() {
        // Load target date for "tomorrow" (the day we are planning for)
        // Diary is for 'diaryDate', so plan is for 'diaryDate + 1'
        val planDate = diaryDate.plusDays(1)
        val targetDateStr = planDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // YYYY-MM-DD
        
        val stepData = loadStepData(NightlySteps.STEP_NORMALIZE_TASKS)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
             TerminalLogger.log("Nightly Phase Planning: Step 14 already completed.")
             listener.onStepCompleted(NightlySteps.STEP_NORMALIZE_TASKS, "Tasks Normalized", "Already done")
             return
        }
        
        listener.onStepStarted(NightlySteps.STEP_NORMALIZE_TASKS, "AI Task Cleanup")
        saveStepState(NightlySteps.STEP_NORMALIZE_TASKS, StepProgress.STATUS_RUNNING, "Fetching tasks...")
        
        try {
            // 1. Fetch Task List Configs for context
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(context)
            val taskListConfigs = db.taskListConfigDao().getAll()

            // 2. Fetch ALL non-completed tasks
            val tasksManager = com.neubofy.reality.google.GoogleTasksManager
            val taskLists = tasksManager.getTaskLists(context)
            val allPendingTasks = mutableListOf<JSONObject>()
            
            for (list in taskLists) {
                val tasks = tasksManager.getTasks(context, list.id)
                tasks.forEach { task ->
                    if (task.status != "completed") {
                        val json = JSONObject()
                        json.put("id", task.id)
                        json.put("list_id", list.id)
                        json.put("title", task.title)
                        json.put("due", task.due ?: "null")
                        json.put("notes", task.notes ?: "")
                        allPendingTasks.add(json)
                    }
                }
            }
            
            if (allPendingTasks.isEmpty()) {
                saveStepState(NightlySteps.STEP_NORMALIZE_TASKS, StepProgress.STATUS_COMPLETED, "No tasks to clean")
                listener.onStepCompleted(NightlySteps.STEP_NORMALIZE_TASKS, "Task Cleanup", "No pending tasks found")
                return
            }
            
            // 3. Prepare JSON for AI
            val tasksJsonStr = JSONArray(allPendingTasks).toString()
            
            saveStepState(NightlySteps.STEP_NORMALIZE_TASKS, StepProgress.STATUS_RUNNING, "AI analyzing ${allPendingTasks.size} tasks...")
            
            // 4. Call AI (Using Standardized Model)
            val model = com.neubofy.reality.ui.activity.AISettingsActivity.getNightlyModel(context)
            if (model.isNullOrEmpty()) {
                throw IllegalStateException("No AI Model configured for Nightly Protocol.")
            }
             
            val aiResponse = com.neubofy.reality.utils.NightlyAIHelper.normalizeTasks(
                context = context, 
                modelString = model, 
                tasksJson = tasksJsonStr, 
                targetDate = targetDateStr + "T00:00:00.000Z",
                taskListConfigs = taskListConfigs
            )
            
            // 5. Parse AI Response
            val responseJson = try {
                 JSONObject(aiResponse.substringAfter("```json").substringBeforeLast("```").trim())
            } catch (e: Exception) {
                 JSONObject(aiResponse) // Try direct parse
            }
            
            val deleteIds = responseJson.optJSONArray("delete_ids")
            val readdTasks = responseJson.optJSONArray("readd_tasks")
            
            var deletedCount = 0
            var addedCount = 0
            
            // 6. Execute Deletions
            if (deleteIds != null) {
                for (i in 0 until deleteIds.length()) {
                     val taskId = deleteIds.getString(i)
                     // Find list ID for this task from our initial fetch
                     val listId = allPendingTasks.find { it.optString("id") == taskId }?.optString("list_id")
                     if (listId != null) {
                         tasksManager.deleteTask(context, listId, taskId)
                         deletedCount++
                     }
                }
            }
            
            // 7. Execute Re-adds (New Tasks)
            if (readdTasks != null) {
                for (i in 0 until readdTasks.length()) {
                    val taskData = readdTasks.getJSONObject(i)
                    val title = taskData.optString("title")
                    val startTime = taskData.optString("startTime")
                    val listId = taskData.optString("taskListId")
                    val notes = taskData.optString("notes")
                    
                    if (title.isNotEmpty() && listId.isNotEmpty()) {
                        // Build title with time always expected as HH:mm|Title
                        val finalTitle = if (startTime.isNotEmpty() && !startTime.equals("null", true)) {
                            "$startTime|$title"
                        } else {
                            "00:00|$title" // Default if AI fails to provide
                        }

                        val dueDate = targetDateStr + "T09:00:00.000Z"
                        tasksManager.createTask(context, finalTitle, notes, dueDate, listId)
                        addedCount++
                    }
                }
            }
            
            // 8. Complete
            val resultDetails = "Deleted $deletedCount duplicates/moved tasks, Re-added $addedCount corrected tasks"
            TerminalLogger.log("Nightly Step 14: $resultDetails")
            saveStepState(NightlySteps.STEP_NORMALIZE_TASKS, StepProgress.STATUS_COMPLETED, resultDetails, aiResponse)
            listener.onStepCompleted(NightlySteps.STEP_NORMALIZE_TASKS, "Task Cleanup Complete", resultDetails)
            
        } catch (e: Exception) {
            TerminalLogger.log("Nightly Step 14 Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_NORMALIZE_TASKS, StepProgress.STATUS_ERROR, e.message)
            listener.onError(NightlySteps.STEP_NORMALIZE_TASKS, "Cleanup Failed: ${e.message}")
        }
    }

    // ========== STEP 15: Update Distraction Limit ==========
    suspend fun step15_updateDistraction() {
        val stepData = loadStepData(NightlySteps.STEP_UPDATE_DISTRACTION)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_UPDATE_DISTRACTION, "Limit Updated", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_UPDATE_DISTRACTION, "Updating Distraction Limit")
        saveStepState(NightlySteps.STEP_UPDATE_DISTRACTION, StepProgress.STATUS_RUNNING, "Reading Step 9...")

        try {
            // Read Step 9 output
            val step9Data = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
            if (step9Data.resultJson == null) {
                val skipDetails = "Step 9 not completed - using default"
                listener.onStepCompleted(NightlySteps.STEP_UPDATE_DISTRACTION, "Skipped", skipDetails)
                saveStepState(NightlySteps.STEP_UPDATE_DISTRACTION, StepProgress.STATUS_COMPLETED, skipDetails)
                return
            }

            val step9Json = JSONObject(step9Data.resultJson)
            val newLimit = step9Json.optInt("distractionTimeMinutes", 60)

            // Get current limit from the CORRECT prefs (nightly_prefs - same as ReflectionSettingsActivity)
            val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
            val oldLimit = prefs.getInt("screen_time_limit_minutes", 60)

            // Update preference (bypasses Strict Mode as this is authorized Nightly Protocol update)
            prefs.edit().putInt("screen_time_limit_minutes", newLimit).apply()

            val resultJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("step9DistractionTime", step9Json.optInt("distractionTimeMinutes", -1))
                    put("diaryDate", diaryDate.toString())
                })
                put("output", JSONObject().apply {
                    put("oldLimit", oldLimit)
                    put("newLimit", newLimit)
                    put("updatedAt", System.currentTimeMillis())
                })
            }.toString()

            val details = "${oldLimit}min → ${newLimit}min"
            listener.onStepCompleted(NightlySteps.STEP_UPDATE_DISTRACTION, "Limit Updated", details)
            saveStepState(NightlySteps.STEP_UPDATE_DISTRACTION, StepProgress.STATUS_COMPLETED, details, resultJson)
            
            TerminalLogger.log("Nightly Step 15: Distraction limit updated from $oldLimit to $newLimit min")
        } catch (e: Exception) {
            TerminalLogger.log("Nightly Step 15 Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_UPDATE_DISTRACTION, StepProgress.STATUS_ERROR, e.message)
            listener.onError(NightlySteps.STEP_UPDATE_DISTRACTION, "Update Failed: ${e.message}")
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

    private suspend fun getDiaryDocIdFromDB(): String? {
        val step5Data = loadStepData(NightlySteps.STEP_CREATE_DIARY)
        if (step5Data.resultJson != null) {
            try {
                val json = JSONObject(step5Data.resultJson)
                val output = json.optJSONObject("output") ?: json
                val docId = output.optString("docId").ifEmpty { json.optString("docId") }
                if (docId.isNotEmpty()) return docId
            } catch (_: Exception) {}
        }
        return NightlyRepository.getDiaryDocId(context, diaryDate)
    }

    private suspend fun getPlanDocIdFromDB(): String? {
        val step8Data = loadStepData(NightlySteps.STEP_CREATE_PLAN_DOC)
        if (step8Data.resultJson != null) {
            try {
                val json = JSONObject(step8Data.resultJson)
                val output = json.optJSONObject("output") ?: json
                val docId = output.optString("docId").ifEmpty { json.optString("docId") }
                if (docId.isNotEmpty()) return docId
            } catch (_: Exception) {}
        }
        return NightlyRepository.getPlanDocId(context, diaryDate)
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
                bedtime.isEnabled = true
                prefs.saveBedtimeData(bedtime)
                TerminalLogger.log("Nightly Phase Planning: Bedtime Synced -> Start: $sleepTimeStr, End: $wakeupTimeStr")
            }
        } catch (e: Exception) {
            TerminalLogger.log("Nightly Phase Planning: Bedtime sync error - ${e.message}")
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
        } catch (_: Exception) { -1 }
    }

    private suspend fun ensureDaySummary() {
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
