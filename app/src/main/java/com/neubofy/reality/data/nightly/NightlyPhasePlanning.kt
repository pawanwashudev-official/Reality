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

    // ========== STEP 4: Create Plan Document ==========
    suspend fun step4_createPlan() {
        val stepData = loadStepData(NightlySteps.STEP_CREATE_PLAN)

        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                val savedDocId = output.optString("docId")
                val savedUrl = output.optString("docUrl")

                if (savedDocId.isNotEmpty()) {
                    planDocId = savedDocId
                    TerminalLogger.log("Nightly: Step 4 restored planDocId: $savedDocId")
                    listener.onStepCompleted(NightlySteps.STEP_CREATE_PLAN, "Plan Doc Ready", getPlanTitle(), savedUrl)
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly: Step 4 restore failed: ${e.message}")
            }
        }

        listener.onStepStarted(NightlySteps.STEP_CREATE_PLAN, "Creating Plan Document")
        saveStepState(NightlySteps.STEP_CREATE_PLAN, StepProgress.STATUS_RUNNING, "Creating...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Initializing plan document creation...", listener)

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
                    }
                }
            }

            if (planFolderId.isNullOrEmpty()) planFolderId = diaryFolderId

            val nextDay = diaryDate.plusDays(1)
            val title = getPlanTitle()
            val template = prefs.getString("template_plan", NightlySteps.DEFAULT_PLAN_TEMPLATE) ?: NightlySteps.DEFAULT_PLAN_TEMPLATE
            val content = template
                .replace("{date}", nextDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
                .replace("{data}", "[Plan Details]")

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Searching for existing plan document in Google Drive...", listener)
            var docId = GoogleDriveManager.searchFile(context, title, planFolderId)

            val docUrl: String

            if (docId != null) {
                val currentContent = withContext(Dispatchers.IO) {
                    GoogleDocsManager.getDocumentContent(context, docId!!) ?: ""
                }
                val hasRawTemplate = currentContent.contains("{date}") || currentContent.contains("{data}")

                if (currentContent.length < 50 || hasRawTemplate) {
                    withContext(Dispatchers.IO) {
                        GoogleDocsManager.appendText(context, docId!!, "\n" + content.replace(Regex("""\*\*|##|\[|\]"""), ""))
                    }
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Appended template into existing plan document.", listener)
                }
                docUrl = "https://docs.google.com/document/d/$docId"
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Creating new plan document in Google Drive...", listener)
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
                        GoogleDocsManager.appendText(context, newDocId, content.replace(Regex("""\*\*|##"""), ""))
                    }
                    docUrl = "https://docs.google.com/document/d/$newDocId"
                } else {
                    throw IllegalStateException("Failed to create Plan Doc ID")
                }
            }

            planDocId = docId
            prefs.edit().putString(NightlySteps.getPlanDocIdKey(diaryDate), docId).apply()
            NightlyRepository.savePlanDocId(context, diaryDate, docId)

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

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Plan Document ready: $docUrl", listener)
            listener.onStepCompleted(NightlySteps.STEP_CREATE_PLAN, "Plan Doc Ready", title, docUrl)
            saveStepState(NightlySteps.STEP_CREATE_PLAN, StepProgress.STATUS_COMPLETED, "Plan Document Ready", resultJson, docUrl)
        } catch (e: Exception) {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_PLAN, "Failed to create plan document: ${e.message}", listener)
            listener.onError(NightlySteps.STEP_CREATE_PLAN, "Failed to create plan: ${e.message}")
            saveStepState(NightlySteps.STEP_CREATE_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }

    private fun getPlanTitle(): String {
        val nextDay = diaryDate.plusDays(1)
        return "Plan - ${nextDay.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"
    }

    // ========== STEP 5: Apply Plan ==========
    suspend fun step5_applyPlan() {
        val stepData = loadStepData(NightlySteps.STEP_APPLY_PLAN)
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            listener.onStepCompleted(NightlySteps.STEP_APPLY_PLAN, "Plan Applied", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_APPLY_PLAN, "Applying Plan")
        saveStepState(NightlySteps.STEP_APPLY_PLAN, StepProgress.STATUS_RUNNING, "Reading plan document...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Reading plan content from Google Doc...", listener)

        var aiResponseForLog = ""
        try {
            val nightlyModel = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs").getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"
            if (nightlyModel.isEmpty()) {
                throw IllegalStateException("No AI Model configured for parsing.")
            }

            planDocId = getPlanDocIdFromDB()
            if (planDocId == null) {
                throw IllegalStateException("Plan document not found. Run Step 4 first.")
            }

            val planContent = withContext(Dispatchers.IO) {
                GoogleDocsManager.getDocumentContent(context, planDocId!!) ?: ""
            }

            if (planContent.length < 20) {
                throw IllegalStateException("Plan document is too short. Please edit it first.")
            }

            saveStepState(NightlySteps.STEP_APPLY_PLAN, StepProgress.STATUS_RUNNING, "AI Extracting plan info...")
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "AI parsing plan document content...", listener)

            val taskListConfigs = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).taskListConfigDao().getAll()
            }
            val aiResponse = NightlyAIHelper.analyzePlan(context, nightlyModel, planContent, taskListConfigs)
            aiResponseForLog = aiResponse

            val cleanResponse = aiResponse.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
            var jsonStr = if (cleanResponse.contains("```json")) {
                cleanResponse.substringAfter("```json").substringBeforeLast("```").trim()
            } else {
                val startIndex = cleanResponse.indexOf('{')
                val endIndex = cleanResponse.lastIndexOf('}')
                if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                    cleanResponse.substring(startIndex, endIndex + 1)
                } else {
                    cleanResponse
                }
            }

            val json = JSONObject(jsonStr.trim())
            val tasks = json.optJSONArray("tasks") ?: JSONArray()
            val events = json.optJSONArray("events") ?: JSONArray()
            val mentorship = json.optString("mentorship", "No advice.")
            val wakeupTime = json.optString("wakeupTime", "")
            val sleepStartTime = json.optString("sleepStartTime", "")
            val distractionTimeMinutes = json.optInt("distractionTimeMinutes", 60)

            val nextDay = diaryDate.plusDays(1)
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)

            var tasksCreated = 0
            var eventsCreated = 0
            val createdItems = JSONArray()

            // 1. Google Tasks Sub-permission
            val applyTasks = NightlyRepository.isSubFeatureEnabled(context, "apply_tasks")
            if (applyTasks && tasks.length() > 0) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Creating Google Tasks...", listener)
                for (i in 0 until tasks.length()) {
                    val taskLog = JSONObject()
                    try {
                        val taskObj = tasks.optJSONObject(i) ?: continue
                        val title = taskObj.optString("title", "").trim()
                        val notes = taskObj.optString("notes")
                        val rawListId = taskObj.optString("taskListId", "@default")
                        val startTime = taskObj.optString("startTime")

                        if (title.isNotEmpty()) {
                            var finalTaskListId = "@default"
                            val matchedConfig = taskListConfigs.find {
                                it.googleListId == rawListId || it.displayName.equals(rawListId, ignoreCase = true)
                            }
                            if (matchedConfig != null) {
                                finalTaskListId = matchedConfig.googleListId
                            }

                            val finalTitle = if (!startTime.isNullOrEmpty()) "$startTime|$title" else title
                            val dueDate = nextDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T09:00:00.000Z"

                            val created = withContext(Dispatchers.IO) {
                                com.neubofy.reality.google.GoogleTasksManager.createTask(context, finalTitle, notes, dueDate, finalTaskListId)
                            }
                            if (created != null) {
                                tasksCreated++
                                taskLog.put("title", finalTitle)
                                taskLog.put("status", "SUCCESS")
                            }
                        }
                    } catch (e: Exception) {
                        taskLog.put("status", "ERROR: ${e.message}")
                    }
                    createdItems.put(taskLog)
                }
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Successfully created $tasksCreated tasks.", listener)
            } else if (applyTasks) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "No tasks found to create.", listener)
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Task creation skipped by user setting.", listener)
            }

            // 2. Google Calendar Events Sub-permission
            val applyEvents = NightlyRepository.isSubFeatureEnabled(context, "apply_calendar_events")
            if (applyEvents && events.length() > 0) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Creating Google Calendar events...", listener)
                for (i in 0 until events.length()) {
                    val eventLog = JSONObject()
                    try {
                        val eventObj = events.optJSONObject(i) ?: continue
                        val title = eventObj.optString("title", "").trim()
                        val startTimeStr = eventObj.optString("startTime", "")
                        val endTimeStr = eventObj.optString("endTime", "")
                        val description = eventObj.optString("description")

                        if (title.isNotEmpty() && startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty()) {
                            val startParts = startTimeStr.split(":")
                            val endParts = endTimeStr.split(":")
                            if (startParts.size >= 2 && endParts.size >= 2) {
                                val startMs = nextDay.atTime(startParts[0].toInt(), startParts[1].toInt())
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val endMs = nextDay.atTime(endParts[0].toInt(), endParts[1].toInt())
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                                val eventId = withContext(Dispatchers.IO) {
                                    com.neubofy.reality.google.GoogleCalendarManager.createEvent(context, title, startMs, endMs, description)
                                }
                                if (eventId != null) {
                                    eventsCreated++
                                    eventLog.put("title", title)
                                    eventLog.put("status", "SUCCESS")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        eventLog.put("status", "ERROR: ${e.message}")
                    }
                    createdItems.put(eventLog)
                }
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Successfully created $eventsCreated events.", listener)
            } else if (applyEvents) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "No calendar events found to create.", listener)
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Calendar event creation skipped by user setting.", listener)
            }

            // 3. Update Sleep Time Bedtime Sub-permission
            val applySleep = NightlyRepository.isSubFeatureEnabled(context, "apply_sleep_time")
            if (applySleep) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Syncing planned bedtime sleep schedule...", listener)
                syncBedtime(wakeupTime, sleepStartTime)
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Bedtime sync skipped by user setting.", listener)
            }

            // 4. Wakeup Alarm Sub-permission
            val applyAlarm = NightlyRepository.isSubFeatureEnabled(context, "apply_alarm")
            var alarmSet = false
            if (applyAlarm && wakeupTime.isNotEmpty()) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Scheduling wakeup alarm for $wakeupTime...", listener)
                try {
                    val parts = wakeupTime.split(":")
                    if (parts.size == 2) {
                        val alarmHour = parts[0].toInt()
                        val alarmMin = parts[1].toInt()

                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                        if (alarmManager != null) {
                            val canSchedule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                alarmManager.canScheduleExactAlarms()
                            } else true

                            if (canSchedule) {
                                val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
                                val defaults = prefsLoader.getWakeupAlarmDefaults()
                                val wakeupAlarms = prefsLoader.loadWakeupAlarms()
                                wakeupAlarms.removeAll { it.id == "nightly_wakeup" }
                                wakeupAlarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                                    id = "nightly_wakeup",
                                    title = "Wake Up (AI Plan)",
                                    description = "AI generated wakeup alarm.",
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
                                alarmSet = true
                                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Wakeup alarm set successfully.", listener)
                            }
                        }
                    }
                } catch (e: Exception) {
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Warning: Failed to set wakeup alarm: ${e.message}", listener)
                }
            } else if (applyAlarm) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "No wakeup time found to set alarm.", listener)
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Wakeup alarm scheduling skipped by user setting.", listener)
            }

            // 5. Distraction Limit Sub-permission
            val applyDistraction = NightlyRepository.isSubFeatureEnabled(context, "apply_distraction_limit")
            if (applyDistraction) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Updating distraction screen time limit to $distractionTimeMinutes min...", listener)
                prefs.edit().putInt("screen_time_limit_minutes", distractionTimeMinutes).apply()
            } else {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Distraction limit update skipped by user setting.", listener)
            }

            val details = "$tasksCreated tasks, $eventsCreated events applied."
            val resultJson = JSONObject().apply {
                put("output", JSONObject().apply {
                    put("tasksCount", tasksCreated)
                    put("eventsCount", eventsCreated)
                    put("wakeupTime", wakeupTime)
                    put("sleepStartTime", sleepStartTime)
                    put("alarmSet", alarmSet)
                    put("distractionLimit", distractionTimeMinutes)
                    put("mentorship", mentorship)
                })
            }.toString()

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, "Plan applied successfully.", listener)
            listener.onStepCompleted(NightlySteps.STEP_APPLY_PLAN, "Plan Applied", details)
            saveStepState(NightlySteps.STEP_APPLY_PLAN, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            val errMsg = "Failed to apply plan: ${e.message}."
            val rawLog = if (aiResponseForLog.isNotEmpty()) " Raw AI response was: $aiResponseForLog" else ""
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_APPLY_PLAN, errMsg + rawLog, listener)
            listener.onError(NightlySteps.STEP_APPLY_PLAN, errMsg)
            saveStepState(NightlySteps.STEP_APPLY_PLAN, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 6: Report ==========
    suspend fun step6_generateReport() {
        val stepData = loadStepData(NightlySteps.STEP_GENERATE_REPORT)
        if (stepData.status == StepProgress.STATUS_COMPLETED) {
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_REPORT, "Report Completed", stepData.details)
            return
        }

        listener.onStepStarted(NightlySteps.STEP_GENERATE_REPORT, "Generating Report")
        saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_RUNNING, "Generating summary report...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_GENERATE_REPORT, "Generating AI report content...", listener)

        try {
            ensureDaySummary()
            val summary = daySummary ?: throw IllegalStateException("Day stats summary not available.")

            val dId = getDiaryDocIdFromDB()
            val pId = getPlanDocIdFromDB()

            val diaryContent = if (dId != null) {
                withContext(Dispatchers.IO) { GoogleDocsManager.getDocumentContent(context, dId) ?: "" }
            } else ""

            val planContent = if (pId != null) {
                withContext(Dispatchers.IO) { GoogleDocsManager.getDocumentContent(context, pId) ?: "" }
            } else ""

            val report = reporter.generateReport(diaryDate, summary, diaryContent, planContent)
            NightlyRepository.saveReportContent(context, diaryDate, report)

            // PDF creation
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_GENERATE_REPORT, "Creating report PDF file...", listener)
            
            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            val reportFolderId = prefs.getString("report_folder_id", null)

            if (reportFolderId.isNullOrEmpty()) {
                throw IllegalStateException("Report folder not configured.")
            }

            val dateFormatted = diaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val title = "Reality Report - $dateFormatted"
            val fileName = "Reality_Report_$dateFormatted"

            val pdfFile = withContext(Dispatchers.IO) {
                com.neubofy.reality.utils.PdfGenerator.generatePdfFile(context, report, title, fileName)
            }

            // Search Drive first
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_GENERATE_REPORT, "Uploading report PDF to Drive...", listener)
            var pdfFileId = GoogleDriveManager.searchFile(context, "$fileName.pdf", reportFolderId)

            if (pdfFileId == null) {
                pdfFileId = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleCredential(context) ?: throw Exception("Not signed in")
                    val driveService = Drive.Builder(GoogleAuthManager.getHttpTransport(), GoogleAuthManager.getJsonFactory(), credential)
                        .setApplicationName("Reality").build()

                    val fileMetadata = DriveFile().apply {
                        setName("$fileName.pdf")
                        setParents(listOf(reportFolderId))
                        setMimeType("application/pdf")
                    }

                    val mediaContent = FileContent("application/pdf", pdfFile)
                    val uploaded = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                    
                    pdfFile.delete()
                    uploaded.id
                }
            } else {
                pdfFile.delete()
            }

            NightlyRepository.saveReportPdfId(context, diaryDate, pdfFileId!!)
            val pdfUrl = "https://drive.google.com/file/d/$pdfFileId/view"

            val resultJson = JSONObject().apply {
                put("output", JSONObject().apply {
                    put("reportLength", report.length)
                    put("pdfId", pdfFileId)
                    put("pdfUrl", pdfUrl)
                })
            }.toString()

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_GENERATE_REPORT, "Report PDF ready: $pdfUrl", listener)
            listener.onStepCompleted(NightlySteps.STEP_GENERATE_REPORT, "Report Completed", title, pdfUrl)
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_COMPLETED, "Report Ready", resultJson, pdfUrl)
            
            com.neubofy.reality.utils.NotificationHelper.showNotification(context, "Nightly: Report Ready", "Analysis complete. Tap to view.", NightlySteps.STEP_GENERATE_REPORT)
        } catch (e: Exception) {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_GENERATE_REPORT, "Report generation failed: ${e.message}", listener)
            listener.onError(NightlySteps.STEP_GENERATE_REPORT, "Report generation failed: ${e.message}")
            saveStepState(NightlySteps.STEP_GENERATE_REPORT, StepProgress.STATUS_ERROR, e.message)
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
        val step2Data = loadStepData(NightlySteps.STEP_CREATE_DIARY)
        if (step2Data.resultJson != null) {
            try {
                val json = JSONObject(step2Data.resultJson)
                val output = json.optJSONObject("output") ?: json
                val docId = output.optString("docId").ifEmpty { json.optString("docId") }
                if (docId.isNotEmpty()) return docId
            } catch (_: Exception) {}
        }
        return NightlyRepository.getDiaryDocId(context, diaryDate)
    }

    private suspend fun getPlanDocIdFromDB(): String? {
        val step4Data = loadStepData(NightlySteps.STEP_CREATE_PLAN)
        if (step4Data.resultJson != null) {
            try {
                val json = JSONObject(step4Data.resultJson)
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

            val step1Data = NightlyRepository.loadStepData(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS)
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
