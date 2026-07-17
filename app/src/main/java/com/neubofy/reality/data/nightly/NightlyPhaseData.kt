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
import com.neubofy.reality.health.HealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.json.JSONArray
import com.google.api.services.drive.Drive

/**
 * Handles Steps 1-5 of the Nightly Protocol:
 * - Step 1: Fetch Google Tasks
 * - Step 2: Fetch Tapasya Sessions + Calendar Events
 * - Step 3: Calculate Screen Time & Health Metrics
 * - Step 4: Generate AI Reflection Questions (ALWAYS uses AI)
 * - Step 5: Create Diary Document in Google Docs
 *
 * Key Principle: Each step READS from database (previous step data) and WRITES to database.
 * This ensures data persistence and enables step resumption.
 */
class NightlyPhaseData(
    private val context: Context,
    private val diaryDate: LocalDate,
    private val listener: NightlyProgressListener
) {
    // Cached data collectors
    private val dataCollector = NightlyDataCollector(context)

    // In-memory state (also persisted to DB)
    var daySummary: DaySummary? = null
        private set
    var fetchedTasks: com.neubofy.reality.google.GoogleTasksManager.TaskStats? = null
        private set
    var generatedQuestions: List<String> = emptyList()
        private set
    var diaryDocId: String? = null
        private set
    var screenTimeMinutes: Int = 0
        private set
    var screenTimeXpDelta: Int = 0
        private set

    // ========== STEP 1: Fetch Analytics ==========
    suspend fun step1_fetchAnalytics() {
        val stepData = loadStepData(NightlySteps.STEP_FETCH_ANALYTICS)

        // If completed, restore from saved resultJson
        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                val dueTasks = mutableListOf<String>()
                val completedTasks = mutableListOf<String>()

                output.optJSONArray("dueTasks")?.let { arr ->
                    for (i in 0 until arr.length()) dueTasks.add(arr.getString(i))
                }
                output.optJSONArray("completedTasks")?.let { arr ->
                    for (i in 0 until arr.length()) completedTasks.add(arr.getString(i))
                }

                fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(
                    dueTasks, completedTasks,
                    output.optInt("pendingCount", dueTasks.size),
                    output.optInt("completedCount", completedTasks.size)
                )
                
                screenTimeMinutes = output.optInt("usedMinutes", 0)
                screenTimeXpDelta = output.optInt("xpDelta", 0)
                
                daySummary = collectDayDataSilently()
                
                TerminalLogger.log("Nightly Phase Data: Step 1 restored successfully")
                listener.onStepCompleted(NightlySteps.STEP_FETCH_ANALYTICS, "Analytics Fetched", stepData.details)
                return
            } catch (e: Exception) {
                TerminalLogger.log("Nightly Phase Data: Step 1 restore failed, re-fetching: ${e.message}")
            }
        }

        // Fetch fresh from API
        listener.onStepStarted(NightlySteps.STEP_FETCH_ANALYTICS, "Fetching Analytics")
        saveStepState(NightlySteps.STEP_FETCH_ANALYTICS, StepProgress.STATUS_RUNNING, "Starting collection...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Initializing data collection...", listener)

        try {
            // 1. Fetch Google Tasks
            val tasksEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_tasks")
            if (tasksEnabled) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetching Google Tasks...", listener)
                try {
                    fetchedTasks = dataCollector.fetchTasks(diaryDate)
                    val completedSize = fetchedTasks?.completedTasks?.size ?: 0
                    val pendingSize = fetchedTasks?.dueTasks?.size ?: 0
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetched $completedSize completed tasks and $pendingSize pending tasks.", listener)
                } catch (e: Exception) {
                    fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Warning: Task fetch failed: ${e.message}", listener)
                }
            } else {
                fetchedTasks = com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Task collection skipped in settings.", listener)
            }

            // 2. Fetch Sessions & Calendar
            val calendarEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_calendar")
            val tapasyaEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_tapasya")
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetching Tapasya study sessions and Calendar events...", listener)
            
            daySummary = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

                val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
                val calendarEvents = if (calendarEnabled) calendarRepo.getEventsInRange(startOfDay, endOfDay) else emptyList()
                val sessions = if (tapasyaEnabled) db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay) else emptyList()
                val plannedEvents = db.calendarEventDao().getEventsInRange(startOfDay, endOfDay)

                val totalPlannedMinutes = calendarEvents.sumOf { (it.endTime - it.startTime) / 60000 }
                val totalEffectiveMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }

                DaySummary(
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
            
            val sessionSize = daySummary?.completedSessions?.size ?: 0
            val eventSize = daySummary?.calendarEvents?.size ?: 0
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetched $sessionSize study sessions and $eventSize calendar events.", listener)

            // 3. Screen Time & Health
            val distractionEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_distraction")
            val healthEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_health")
            val sleepEnabled = NightlyRepository.isSubFeatureEnabled(context, "collect_sleep")

            val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
            val limitMinutes = prefs.getInt("screen_time_limit_minutes", 60)

            if (distractionEnabled) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Calculating distraction screen time XP...", listener)
                
                // Use the centralized XP calculation logic instead of duplicating it
                val xpPair = com.neubofy.reality.utils.XPManager.calculateDistractionXP(context, diaryDate.toString())
                screenTimeXpDelta = xpPair.first
                
                // Calculate just the minutes for logging
                val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
                val blockedApps = prefsLoader.loadBlockedApps()
                val distractingUsageMs = com.neubofy.reality.utils.UsageUtils.getBlockedAppsUsageForDate(context, diaryDate, blockedApps)
                screenTimeMinutes = (distractingUsageMs / 60000).toInt()
                
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Distracting app usage: $screenTimeMinutes minutes. XP Delta: $screenTimeXpDelta", listener)
            } else {
                screenTimeMinutes = 0
                screenTimeXpDelta = 0
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Distraction app usage tracking skipped.", listener)
            }

            // Total screen time
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            var totalPhoneMinutesAcrossApps = 0L
            if (usageStatsManager != null) {
                val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfQuery = if (diaryDate == LocalDate.now()) System.currentTimeMillis() else {
                    diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                }
                val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfDay, endOfQuery)
                val excludedPrefixes = listOf("com.android.", "android", "com.google.android.inputmethod", "com.sec.android.app.launcher", "com.miui.home", "com.huawei.android.launcher")
                for (stat in stats) {
                    if (stat.totalTimeInForeground <= 0) continue
                    if (excludedPrefixes.any { stat.packageName.startsWith(it) }) continue
                    if (stat.packageName.contains("launcher", ignoreCase = true)) continue
                    if (stat.packageName == context.packageName) continue
                    totalPhoneMinutesAcrossApps += stat.totalTimeInForeground
                }
            }
            val totalPhoneMinutes = (totalPhoneMinutesAcrossApps / 60000).toInt()

            var steps = 0L
            var sleepMinutesTotal = 0L
            var sleepInfo = "No data"
            var unlocks = 0
            var streakMins = 0

            val healthManager = HealthManager(context)
            if (healthEnabled) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetching steps & digital wellbeing pickups...", listener)
                steps = try { healthManager.getSteps(diaryDate) } catch (_: Exception) { 0L }
                val usageMetrics = com.neubofy.reality.utils.UsageUtils.getProUsageMetrics(context, diaryDate)
                unlocks = usageMetrics.pickupCount
                streakMins = (usageMetrics.longestStreakMs / 60000).toInt()
            }

            if (sleepEnabled) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Fetching sleep routines...", listener)
                val sleepSessions = try { healthManager.getSleepSessions(diaryDate) } catch (_: Exception) { emptyList() }
                sleepMinutesTotal = sleepSessions.sumOf { java.time.Duration.between(it.first, it.second).toMinutes() }
                sleepInfo = try { healthManager.getSleep(diaryDate) } catch (_: Exception) { "No data" }
            }

            val totalMinutesElapsed = if (diaryDate == LocalDate.now()) {
                val now = java.time.LocalTime.now()
                (now.hour * 60) + now.minute
            } else 1440
            val wakingMinutes = (totalMinutesElapsed - sleepMinutesTotal).coerceAtLeast(1)
            val phonelessMinutes = (wakingMinutes - totalPhoneMinutes).coerceAtLeast(0)
            val realityRatio = if (wakingMinutes > 0) {
                (phonelessMinutes * 100) / wakingMinutes.toInt()
            } else 0

            val details = "${screenTimeMinutes}m Distractions • $totalPhoneMinutes Total • $realityRatio% Reality"
            
            val resultJson = JSONObject().apply {
                put("input", JSONObject().put("date", diaryDate.toString()))
                put("output", JSONObject().apply {
                    put("dueTasks", JSONArray(fetchedTasks?.dueTasks ?: emptyList<String>()))
                    put("completedTasks", JSONArray(fetchedTasks?.completedTasks ?: emptyList<String>()))
                    put("pendingCount", fetchedTasks?.pendingCount ?: 0)
                    put("completedCount", fetchedTasks?.completedCount ?: 0)
                    put("sessionCount", daySummary?.completedSessions?.size ?: 0)
                    put("eventCount", daySummary?.calendarEvents?.size ?: 0)
                    put("plannedEventCount", daySummary?.plannedEvents?.size ?: 0)
                    put("plannedMinutes", daySummary?.totalPlannedMinutes ?: 0)
                    put("effectiveMinutes", daySummary?.totalEffectiveMinutes ?: 0)
                    put("usedMinutes", screenTimeMinutes)
                    put("totalPhoneMinutes", totalPhoneMinutes)
                    put("xpDelta", screenTimeXpDelta)
                    put("unlocks", unlocks)
                    put("streakMinutes", streakMins)
                    put("phonelessMinutes", phonelessMinutes)
                    put("realityRatio", realityRatio)
                    put("steps", steps)
                    put("sleepInfo", sleepInfo)
                    put("sleepMinutes", sleepMinutesTotal)
                    put("wakingMinutes", wakingMinutes)
                })
            }.toString()

            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Successfully collected analytics.", listener)
            listener.onStepCompleted(NightlySteps.STEP_FETCH_ANALYTICS, "Analytics Fetched", details)
            saveStepState(NightlySteps.STEP_FETCH_ANALYTICS, StepProgress.STATUS_COMPLETED, details, resultJson)
        } catch (e: Exception) {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_FETCH_ANALYTICS, "Error collecting analytics: ${e.message}", listener)
            listener.onError(NightlySteps.STEP_FETCH_ANALYTICS, "Analytics Fetch Failed: ${e.message}")
            saveStepState(NightlySteps.STEP_FETCH_ANALYTICS, StepProgress.STATUS_ERROR, e.message)
        }
    }

    // ========== STEP 2: Create Diary Document ==========
    suspend fun step2_createDiary() {
        val stepData = loadStepData(NightlySteps.STEP_CREATE_DIARY)

        if (stepData.status == StepProgress.STATUS_COMPLETED && stepData.resultJson != null) {
            try {
                val json = JSONObject(stepData.resultJson)
                val output = json.optJSONObject("output") ?: json
                val savedDocId = output.optString("docId")
                val savedUrl = output.optString("docUrl")

                if (savedDocId.isNotEmpty()) {
                    diaryDocId = savedDocId
                    TerminalLogger.log("Nightly Phase Data: Step 2 restored diaryDocId: $savedDocId")
                    listener.onStepCompleted(NightlySteps.STEP_CREATE_DIARY, "Diary Ready", getDiaryTitle(), savedUrl)
                    return
                }
            } catch (e: Exception) {
                TerminalLogger.log("Nightly Phase Data: Step 2 JSON parse failed")
            }
        }

        listener.onStepStarted(NightlySteps.STEP_CREATE_DIARY, "Creating Diary Document")
        saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_RUNNING, "Creating...")
        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Initializing diary creation...", listener)

        // Ensure daySummary is populated
        if (daySummary == null) {
            daySummary = collectDayDataSilently()
        }

        val summary = daySummary
        if (summary == null) {
            listener.onError(NightlySteps.STEP_CREATE_DIARY, "Day analytics data not available")
            saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, "No day data")
            return
        }

        val includeQuestions = NightlyRepository.isSubFeatureEnabled(context, "include_ai_questions")
        val questions = mutableListOf<String>()

        if (includeQuestions) {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Generating AI reflection questions...", listener)
            val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
            val nightlyModel = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs").getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"

            if (nightlyModel.isEmpty()) {
                val error = "No AI Model configured for Nightly."
                listener.onError(NightlySteps.STEP_CREATE_DIARY, error)
                saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, error)
                return
            }

            val healthDataStr = buildHealthDataFromStep1()
            val previousDayReport = getPreviousDayReport()

            try {
                val aiQuestions = NightlyAIHelper.generateQuestions(
                    context = context,
                    modelString = nightlyModel,
                    userIntroduction = userIntro,
                    daySummary = summary,
                    healthData = healthDataStr,
                    previousReport = previousDayReport
                )
                questions.addAll(aiQuestions)
                generatedQuestions = questions
                listener.onQuestionsReady(questions)
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Generated ${questions.size} personalized questions.", listener)
            } catch (e: Exception) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Warning: Failed to generate questions: ${e.message}. Using defaults.", listener)
                questions.addAll(listOf(
                    "What went well today?",
                    "What was your biggest blocker?",
                    "How can you improve tomorrow?"
                ))
            }
        } else {
            NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "AI question generation skipped by user setting.", listener)
        }

        val prefs = context.getSharedPreferences(NightlySteps.PREFS_NAME, Context.MODE_PRIVATE)
        var diaryFolderId = prefs.getString("diary_folder_id", null)
        val realityFolderId = prefs.getString("reality_folder_id", null)

        if (diaryFolderId.isNullOrEmpty() && !realityFolderId.isNullOrEmpty()) {
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
                if (folders.isNotEmpty()) {
                    diaryFolderId = folders[0].id
                }
            }
        }

        if (diaryFolderId.isNullOrEmpty()) {
            listener.onError(NightlySteps.STEP_CREATE_DIARY, "Diary folder not configured")
            saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, "No folder configured")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val template = prefs.getString("template_diary", NightlySteps.DEFAULT_DIARY_TEMPLATE) ?: NightlySteps.DEFAULT_DIARY_TEMPLATE
                val content = buildDiaryContent(summary, questions, template)
                val diaryTitle = getDiaryTitle()

                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Searching for existing diary document...", listener)
                var docId = GoogleDriveManager.searchFile(context, diaryTitle, diaryFolderId)

                val processedUrl: String

                if (docId != null) {
                    val currentContent = GoogleDocsManager.getDocumentContent(context, docId) ?: ""
                    val hasRawTemplate = currentContent.contains("{data}") || currentContent.contains("{questions}")

                    if (currentContent.length < 50 || hasRawTemplate) {
                        GoogleDocsManager.appendText(context, docId, "\n\n" + content.replace(Regex("""\*\*|##|\[|\]"""), ""))
                        NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Appended data summary into existing diary document.", listener)
                    }
                    processedUrl = "https://docs.google.com/document/d/$docId"
                } else {
                    NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Creating new diary document in Google Drive...", listener)
                    docId = GoogleDocsManager.createDocument(context, diaryTitle)
                    if (docId != null) {
                        GoogleDriveManager.moveFileToFolder(context, docId, diaryFolderId)
                        GoogleDocsManager.appendText(context, docId, content.replace(Regex("""\*\*|##|\[|\]"""), ""))
                        processedUrl = "https://docs.google.com/document/d/$docId"
                    } else {
                        throw IllegalStateException("Failed to create document ID")
                    }
                }

                diaryDocId = docId
                prefs.edit().putString(NightlySteps.getDiaryDocIdKey(diaryDate), docId).apply()
                NightlyRepository.saveDiaryDocId(context, diaryDate, docId)

                val resultJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("title", diaryTitle)
                        put("folderId", diaryFolderId)
                        put("questionsCount", questions.size)
                    })
                    put("output", JSONObject().apply {
                        put("docId", docId)
                        put("docUrl", processedUrl)
                    })
                }.toString()

                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Diary creation successfully completed.", listener)
                listener.onStepCompleted(NightlySteps.STEP_CREATE_DIARY, "Diary Ready", diaryTitle, processedUrl)
                saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_COMPLETED, diaryTitle, resultJson, processedUrl)
            } catch (e: Exception) {
                NightlyRepository.logSubStep(context, diaryDate, NightlySteps.STEP_CREATE_DIARY, "Diary creation failed: ${e.message}", listener)
                listener.onError(NightlySteps.STEP_CREATE_DIARY, e.message ?: "Failed to create diary")
                saveStepState(NightlySteps.STEP_CREATE_DIARY, StepProgress.STATUS_ERROR, e.message)
            }
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

    suspend fun collectDayDataSilently(): DaySummary {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val startOfDay = diaryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = diaryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(context)
            val calendarEvents = calendarRepo.getEventsInRange(startOfDay, endOfDay)

            // Try to get tasks from Step 1 DB result instead of re-fetching
            val taskStats = if (fetchedTasks != null) {
                fetchedTasks!!
            } else {
                val step1Data = NightlyRepository.loadStepData(context, diaryDate, NightlySteps.STEP_FETCH_TASKS)
                if (step1Data.resultJson != null) {
                    try {
                        val json = JSONObject(step1Data.resultJson)
                        val output = json.optJSONObject("output") ?: json
                        val dueTasks = mutableListOf<String>()
                        val completedTasks = mutableListOf<String>()
                        output.optJSONArray("dueTasks")?.let { arr ->
                            for (i in 0 until arr.length()) dueTasks.add(arr.getString(i))
                        }
                        output.optJSONArray("completedTasks")?.let { arr ->
                            for (i in 0 until arr.length()) completedTasks.add(arr.getString(i))
                        }
                        com.neubofy.reality.google.GoogleTasksManager.TaskStats(dueTasks, completedTasks, dueTasks.size, completedTasks.size)
                    } catch (_: Exception) {
                        com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
                    }
                } else {
                    // Only fetch if not in DB
                    try {
                        val dateString = diaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        com.neubofy.reality.google.GoogleTasksManager.getTasksForDate(context, dateString)
                    } catch (_: Exception) {
                        com.neubofy.reality.google.GoogleTasksManager.TaskStats(emptyList(), emptyList(), 0, 0)
                    }
                }
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

    private suspend fun buildHealthDataFromStep1(): String {
        val stepData = loadStepData(NightlySteps.STEP_FETCH_ANALYTICS)
        if (stepData.resultJson == null) return "No health data collected."

        return try {
            val json = JSONObject(stepData.resultJson)
            val output = json.optJSONObject("output") ?: json

            val used = output.optInt("usedMinutes", 0)
            val totalUsed = output.optInt("totalPhoneMinutes", 0)
            val limit = output.optInt("limitMinutes", 0)
            val unlocks = output.optInt("unlocks", 0)
            val steps = output.optLong("steps", 0)
            val sleep = output.optString("sleepInfo", "No data")
            val sleepMins = output.optLong("sleepMinutes", 0)
            val wakingMins = output.optLong("wakingMinutes", 0)
            val ratio = output.optInt("realityRatio", 0)
            val streak = output.optInt("streakMinutes", 0)
            val xpDelta = output.optInt("xpDelta", 0)

            val overUnder = when {
                limit > 0 && used > limit -> "⚠️ OVER LIMIT by ${used - limit}m"
                limit > 0 -> "✅ ${limit - used}m under limit"
                else -> "No limit set"
            }

            // Fetch previous day comparison
            val db = AppDatabase.getDatabase(context)
            val yesterday = diaryDate.minusDays(1).toString()
            val prevStats = db.dailyStatsDao().getStatsForDate(yesterday)
            val comparisonStr = if (prevStats != null) {
                "Yesterday: ${prevStats.totalXP} XP, Level ${prevStats.level}, ${prevStats.streak} Day Streak."
            } else {
                "No data for previous day."
            }

            """
                ### 📱 Digital Health Analysis
                - **Distraction Apps Usage**: ${used}m (${used / 60}h ${used % 60}m) 
                  [Description: Time spent on apps you labeled as distracting/blocked. Your goal is to keep this low.]
                - **Daily Distraction Limit**: ${if(limit > 0) "${limit}m" else "No limit set"}
                  [Status: $overUnder]
                - **TOTAL Device Screen Time**: ${totalUsed}m (${totalUsed / 60}h ${totalUsed % 60}m) 
                  [Description: Net time spent on phone, excluding system apps, keyboards, and launchers to match Statistics page exactly.]
                - **Reality Ratio**: $ratio% 
                  [Description: Percentage of your waking day (excluding sleep) spent away from the phone. This is the ultimate metric for presence.]
                - **Waking Duration**: ${wakingMins / 60}h ${wakingMins % 60}m
                  [Description: Total minutes available in the day after subtracting recorded sleep time.]
                - **Focus XP Impact**: ${if(xpDelta >= 0) "+$xpDelta" else xpDelta} XP 
                  [Description: Experience points earned or lost based on your distraction control.]
                - **Device Pickups (Unlocks)**: $unlocks times
                - **Longest Offline Streak**: ${streak / 60}h ${streak % 60}m 
                  [Description: Your longest continuous session without touching your phone today.]

                ### 🏃 Physical Health & Context
                - **Steps Traveled**: $steps steps
                - **Sleep Summary**: $sleep
                - **Historical Context**: $comparisonStr
            """.trimIndent()

        } catch (e: Exception) {
            "Error parsing health data: ${e.message}"
        }
    }

    private fun getDiaryTitle(): String {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        return "Diary of ${diaryDate.format(formatter)}"
    }

    private fun buildDiaryContent(summary: DaySummary, questions: List<String>, template: String): String {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val dateStr = summary.date.format(dateFormatter)

        // Build stats block
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

        // Build questions block
        val qSb = StringBuilder()
        qSb.appendLine("## 💡 Personalized Reflection Questions")
        qSb.appendLine("---")
        qSb.appendLine("_Answer the following questions to gain clarity on your progress:_")
        qSb.appendLine()

        questions.forEachIndexed { index, question ->
            qSb.appendLine("### Q${index + 1}: $question")
            qSb.appendLine()
            qSb.appendLine("> ")
            qSb.appendLine()
            qSb.appendLine()
        }
        val questionsBlock = qSb.toString()

        return template
            .replace("{date}", dateStr)
            .replace("{questions}", questionsBlock)
            .replace("{stats}", statsBlock)
            .replace("{data}", statsBlock)
    }

    private suspend fun getPreviousDayReport(): String {
        return NightlyRepository.getReportContent(context, diaryDate.minusDays(1)) ?: ""
    }

    // Public getter for diaryDate
    fun getDiaryDate(): LocalDate = diaryDate
}
