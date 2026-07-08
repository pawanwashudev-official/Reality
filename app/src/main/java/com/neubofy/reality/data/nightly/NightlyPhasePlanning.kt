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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                val credential = GoogleAuthManager.getGoogleCredential(context)
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
                        GoogleDocsManager.appendText(context, existingDocId, "\n" + content.replace(Regex("""\*\*|##|\[|\]"""), ""))
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
                        GoogleDocsManager.appendText(context, newDocId, content.replace(Regex("""\*\*|##|\[|\]"""), ""))
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

    // ========== STEP 9: AI Parse Plan, Create Tasks & Events, Set Alarm, Update Distraction ==========
    suspend fun step9_generatePlan() {
        val stepData = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_PLAN, "Plan Parsed & Applied", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_GENERATE_PLAN, "AI Parsing & Applying Plan")
        saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Reading plan...")

        try {
            val nightlyModel = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs").getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"

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

            val planContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.neubofy.reality.google.GoogleDocsManager.getDocumentContent(context, planId) ?: ""
            }

            if (planContent.length < 20) {
                val errorDetails = "Plan too short. Please write your tasks and schedule in the plan document."
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                listener.onError(NightlySteps.STEP_GENERATE_PLAN, errorDetails)
                return
            }

            saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "AI parsing...")

            // Fetch Task List Configs for AI context
            val taskListConfigs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.neubofy.reality.data.db.AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            }
            val aiResponse = com.neubofy.reality.utils.NightlyAIHelper.analyzePlan(context, nightlyModel, planContent, taskListConfigs)

            // Validate JSON with robust extraction
            try {
                // First try to extract from markdown block, fallback to basic json extraction
                var jsonStr = if (aiResponse.contains("```json")) {
                    aiResponse.substringAfter("```json").substringBeforeLast("```").trim()
                } else {
                    val match = Regex("(?s)\\{.*\\}").find(aiResponse)
                    match?.value ?: aiResponse
                }

                // If model just replies with string "null" we should catch it
                if (jsonStr.equals("null", ignoreCase = true)) {
                    throw Exception("Model returned null output instead of JSON.")
                }

                val json = org.json.JSONObject(jsonStr.trim())

                val tasks = json.optJSONArray("tasks") ?: org.json.JSONArray()
                val events = json.optJSONArray("events") ?: org.json.JSONArray()

                if (tasks.length() == 0 && events.length() == 0) {
                    val errorDetails = "Could not extract tasks or events from your plan. Please write clearer items with times."
                    saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails)
                    listener.onError(NightlySteps.STEP_GENERATE_PLAN, errorDetails)
                    return
                }

                val mentorship = json.optString("mentorship", "No advice generated.")
                val wakeupTime = json.optString("wakeupTime", "")
                val sleepStartTime = json.optString("sleepStartTime", "")
                val distractionTimeMinutes = json.optInt("distractionTimeMinutes", 60)

                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Creating Tasks and Events...")

                // ==========================
                // TASK AND EVENT CREATION
                // ==========================
                // Persist sleep/wake times
                val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("planner_wakeup_time", wakeupTime)
                    putString("planner_sleep_time", sleepStartTime)
                    if (sleepStartTime.isNotEmpty()) {
                        com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Saved planned sleep time: $sleepStartTime")
                    }
                }.apply()

                val createdItems = org.json.JSONArray()
                val nextDay = diaryDate.plusDays(1)

                var tasksCreated = 0
                var eventsCreated = 0

                // Create Google Tasks
                for (i in 0 until tasks.length()) {
                    val taskLog = org.json.JSONObject()
                    try {
                        val taskObj = tasks.optJSONObject(i) ?: continue
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
                            val matchedConfig = taskListConfigs.find {
                                it.googleListId == rawListId || it.displayName.equals(rawListId, ignoreCase = true)
                            }

                            if (matchedConfig != null) {
                                finalTaskListId = matchedConfig.googleListId
                            } else if (rawListId != "@default" && rawListId.isNotEmpty()) {
                                taskLog.put("warning", "List '$rawListId' not found. Used Default.")
                                com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: List '$rawListId' not found. Fallback to @default.")
                            }

                            val finalTitle = if (!startTime.isNullOrEmpty()) "$startTime|$title" else title
                            val dueDate = nextDay.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) + "T09:00:00.000Z"

                            val createdTask = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.neubofy.reality.google.GoogleTasksManager.createTask(context, finalTitle, notes, dueDate, finalTaskListId)
                            }

                            if (createdTask != null) {
                                tasksCreated++
                                taskLog.put("status", "SUCCESS")
                                taskLog.put("finalTitle", finalTitle)
                                taskLog.put("finalList", finalTaskListId)
                                com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Created task - $finalTitle in list $finalTaskListId")
                            } else {
                                taskLog.put("status", "FAILED (API Error)")
                            }
                        } else {
                            taskLog.put("status", "FAILED (Empty Title)")
                        }
                    } catch (e: Exception) {
                        taskLog.put("status", "ERROR: ${e.message}")
                        com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Failed to process task at index $i - ${e.message}")
                    }
                    taskLog.put("type", "TASK")
                    createdItems.put(taskLog)
                }

                // Create Calendar Events
                for (i in 0 until events.length()) {
                    val eventLog = org.json.JSONObject()
                    try {
                        val eventObj = events.optJSONObject(i) ?: continue
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
                                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val endMs = nextDay.atTime(endParts[0].toInt(), endParts[1].toInt())
                                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                                val eventId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.neubofy.reality.google.GoogleCalendarManager.createEvent(
                                        context, title, startMs, endMs, description
                                    )
                                }

                                if (eventId != null) {
                                    eventsCreated++
                                    eventLog.put("status", "SUCCESS")
                                    com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Created Cloud Calendar event - $title (ID: $eventId)")
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
                        com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Failed to create calendar event at index $i - ${e.message}")
                    }
                    eventLog.put("type", "EVENT")
                    createdItems.put(eventLog)
                }

                // Sync Bedtime
                syncBedtime(wakeupTime, sleepStartTime)

                // ==========================
                // SET ALARM
                // ==========================
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Setting Alarm...")
                var alarmHour = -1
                var alarmMin = -1
                if (wakeupTime.isNotEmpty()) {
                    try {
                        val parts = wakeupTime.split(":")
                        if (parts.size == 2) {
                            alarmHour = parts[0].toInt()
                            alarmMin = parts[1].toInt()

                            val alarmTime = nextDay.atTime(alarmHour, alarmMin)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()

                            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
                            if (alarmManager != null) {
                                val canSchedule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    alarmManager.canScheduleExactAlarms()
                                } else {
                                    true
                                }

                                if (canSchedule) {
                                    val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
                                    val defaults = prefsLoader.getWakeupAlarmDefaults()
                                    val alarmId = "nightly_wakeup"
                                    val wakeupAlarms = prefsLoader.loadWakeupAlarms()
                                    wakeupAlarms.removeAll { it.id == alarmId }
                                    wakeupAlarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                                        id = alarmId,
                                        title = "Wake Up (AI Plan)",
                                        description = "AI generated wakeup alarm based on your daily plan.",
                                        hour = alarmHour,
                                        minute = alarmMin,
                                        isEnabled = true,
                                        repeatDays = emptyList(),
                                        ringtoneUri = defaults.ringtoneUri,
                                        vibrationEnabled = defaults.vibrationEnabled,
                                        snoozeIntervalMins = defaults.snoozeIntervalMins,
                                        maxAttempts = defaults.maxAttempts,
                                        isDeleted = false
                                    ))
                                    prefsLoader.saveWakeupAlarms(wakeupAlarms)
                                    com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(context)
                                } else {
                                    com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Missing alarm permission")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        com.neubofy.reality.utils.TerminalLogger.log("Nightly Phase Planning: Alarm config error - ${e.message}")
                    }
                }

                // ==========================
                // UPDATE DISTRACTION
                // ==========================
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_RUNNING, "Updating Distraction Limit...")
                val oldLimit = prefs.getInt("screen_time_limit_minutes", 60)
                prefs.edit().putInt("screen_time_limit_minutes", distractionTimeMinutes).apply()
                com.neubofy.reality.utils.TerminalLogger.log("Nightly Step 9: Distraction limit updated from $oldLimit to $distractionTimeMinutes min")

                val details = "${tasks.length()} tasks, ${events.length()} events extracted & applied"
                val resultJson = org.json.JSONObject().apply {
                    put("input", org.json.JSONObject().apply {
                        put("planContent", planContent)
                        put("model", nightlyModel)
                        put("planDocId", planId)
                    })
                    put("output", org.json.JSONObject().apply {
                        put("tasks", tasks)
                        put("events", events)
                        put("mentorship", mentorship)
                        put("wakeupTime", wakeupTime)
                        put("sleepStartTime", sleepStartTime)
                        put("distractionTimeMinutes", distractionTimeMinutes)
                        put("tasksCreated", tasksCreated)
                        put("eventsCreated", eventsCreated)
                        put("items", createdItems)
                        if (alarmHour != -1) {
                            put("alarmHour", alarmHour)
                            put("alarmMinute", alarmMin)
                        }
                        put("oldDistractionLimit", oldLimit)
                        put("newDistractionLimit", distractionTimeMinutes)
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

                listener.onStepCompleted(NightlySteps.STEP_GENERATE_PLAN, "Plan Parsed & Applied", details)
                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)
            } catch (e: Exception) {
                val errorDetails = "AI response was not valid JSON or failed applying. Please try again."
                com.neubofy.reality.utils.TerminalLogger.log("Step 9 process error: ${e.message}, Raw: $aiResponse")

                val failureJson = org.json.JSONObject().apply {
                    put("rawResponse", aiResponse)
                    put("error", e.message)
                }.toString()

                saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, errorDetails, failureJson)
                listener.onError(NightlySteps.STEP_GENERATE_PLAN, errorDetails)
            }
        } catch (e: Exception) {
            listener.onError(NightlySteps.STEP_GENERATE_PLAN, "AI Plan Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_GENERATE_PLAN, StepProgress.STATUS_ERROR, e.message)
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
                val credential = GoogleAuthManager.getGoogleCredential(context)
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








    // ========== STEP 16: Backup to Reality Sheet ==========
    suspend fun step16_backupToSheet() {
        val stepData = loadStepData(NightlySteps.STEP_BACKUP_SHEET)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_BACKUP_SHEET, "Backed Up", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_BACKUP_SHEET, "Backing up to Reality Sheet")
        saveStepState(NightlySteps.STEP_BACKUP_SHEET, StepProgress.STATUS_RUNNING, "Preparing data...")

        try {
            val nightlyPrefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
            val sheetId = nightlyPrefs.getString("reality_sheet_id", null)

            if (sheetId.isNullOrEmpty()) {
                val skipDetails = "Reality Sheet not configured."
                listener.onStepCompleted(NightlySteps.STEP_BACKUP_SHEET, "Skipped", skipDetails)
                saveStepState(NightlySteps.STEP_BACKUP_SHEET, StepProgress.STATUS_COMPLETED, skipDetails)
                return
            }

            // Load required step data
            val step1 = loadStepData(NightlySteps.STEP_FETCH_TASKS)
            val step2 = loadStepData(NightlySteps.STEP_FETCH_SESSIONS)
            val step3 = loadStepData(NightlySteps.STEP_CALC_SCREEN_TIME)
            val step6 = loadStepData(NightlySteps.STEP_CREATE_DIARY)
            val step7 = loadStepData(NightlySteps.STEP_ANALYZE_REFLECTION)
            val step9 = loadStepData(NightlySteps.STEP_GENERATE_PLAN)
            val step12 = loadStepData(NightlySteps.STEP_GENERATE_PDF)

            fun extractJson(data: com.neubofy.reality.data.repository.StepData?): org.json.JSONObject {
                if (data?.resultJson == null) return org.json.JSONObject()
                return try { org.json.JSONObject(data.resultJson) } catch (e: Exception) { org.json.JSONObject() }
            }

            val j1 = extractJson(step1).optJSONObject("output") ?: org.json.JSONObject()
            val j2 = extractJson(step2).optJSONObject("output") ?: org.json.JSONObject()
            val j3 = extractJson(step3).optJSONObject("output") ?: org.json.JSONObject()
            val j6 = extractJson(step6).optJSONObject("input") ?: org.json.JSONObject()
            val j7 = extractJson(step7).optJSONObject("output") ?: org.json.JSONObject()

            // Build the row data
            val rowValues = mutableListOf<Any>()

            // "Date"
            rowValues.add(diaryDate.toString())

            // "Step1_Tasks"
            val totalDue = j1.optInt("pendingCount", 0) + j1.optInt("completedCount", 0)
            val totalComp = j1.optInt("completedCount", 0)
            rowValues.add("$totalComp/$totalDue Completed")

            // "Step2_SessionsCount", "Step2_TotalMins"
            rowValues.add(j2.optInt("sessionCount", 0).toString())
            rowValues.add(j2.optInt("effectiveMinutes", 0).toString())

            // "Step3_ScreenTime", "Step3_Limit"
            rowValues.add(j3.optInt("usedMinutes", 0).toString())
            rowValues.add(j3.optInt("limitMinutes", 0).toString())

            // "Step6_Feedback"
            rowValues.add(j7.optString("feedback", "No feedback"))

            // "XP_Tapasya", "XP_Task", "XP_Session", "XP_Distraction", "XP_Reflection", "XP_Total", "Level", "Streak"
            val step8Data = loadStepData(NightlySteps.STEP_FINALIZE_XP)
            val j8 = extractJson(step8Data).optJSONObject("output") ?: org.json.JSONObject()

            rowValues.add(j8.optInt("tapasyaXp", 0).toString())
            rowValues.add(j8.optInt("taskXp", 0).toString())
            rowValues.add(j8.optInt("sessionXp", 0).toString())
            rowValues.add(j8.optInt("distractionXp", 0).toString())
            rowValues.add(j8.optInt("reflectionXp", 0).toString())
            rowValues.add(j8.optInt("totalXp", 0).toString())
            rowValues.add(j8.optInt("level", 0).toString())
            rowValues.add(j8.optInt("streak", 0).toString())

            // "Plan_Doc_Link"
            rowValues.add(step9.linkUrl ?: "")

            // "Report_PDF_Link"
            rowValues.add(step12.linkUrl ?: "")

            // Append to sheet
            val success = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val service = com.neubofy.reality.google.GoogleAuthManager.getGoogleCredential(context)?.let {
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
                    TerminalLogger.log("Sheets Error appending row: ${e.message}")
                    false
                }
            }

            if (success) {
                val details = "Successfully appended to Reality Sheet."
                listener.onStepCompleted(NightlySteps.STEP_BACKUP_SHEET, "Backed Up", details)
                saveStepState(NightlySteps.STEP_BACKUP_SHEET, StepProgress.STATUS_COMPLETED, details, linkUrl = "https://docs.google.com/spreadsheets/d/$sheetId")
            } else {
                val err = "Failed to append to Reality Sheet."
                listener.onError(NightlySteps.STEP_BACKUP_SHEET, err)
                saveStepState(NightlySteps.STEP_BACKUP_SHEET, StepProgress.STATUS_ERROR, err)
            }
        } catch (e: Exception) {
            TerminalLogger.log("Nightly Step 16 Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_BACKUP_SHEET, StepProgress.STATUS_ERROR, e.message)
            listener.onError(NightlySteps.STEP_BACKUP_SHEET, "Backup Failed: ${e.message}")
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
