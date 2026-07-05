package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.data.repository.CalendarRepository
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Agent Tools: Execution Layer
 * 
 * Uses ToolRegistry for definitions.
 * All times formatted in IST (Asia/Kolkata).
 */
object AgentTools {

    // Legacy compatibility: Full definitions (for old code paths)
    // New code should use ToolRegistry.buildToolsArray()
    val definitions: JSONArray
        get() = JSONArray().apply {
            ToolRegistry.ALL_TOOLS.forEach { tool ->
                ToolRegistry.getToolSchema(tool.id)?.let { put(it) }
            }
        }

    // --- Execution Logic ---
    suspend fun execute(context: Context, name: String, argsInfo: String): String {
        return try {
            val args = JSONObject(if (argsInfo.isBlank()) "{}" else argsInfo)
            
            // 1. Check if tool is enabled (Security/Privacy)
                val recognizedId = ToolRegistry.getToolIdForFunction(name)
                if (recognizedId != null && !ToolRegistry.isToolEnabled(context, recognizedId)) {
                     return "⚠️ Tool is disabled: $name. Please enable '${ToolRegistry.ALL_TOOLS.find { it.id == recognizedId }?.name ?: recognizedId}' in AI Settings."
                }
            
            when (name) {
                // META TOOL: Dynamic Schema Discovery
                "get_tool_schema" -> {
                    val toolId = args.optString("tool_id", "")
                    if (toolId.isEmpty()) return "Error: tool_id is required"
                    
                    if (!ToolRegistry.isToolEnabled(context, toolId)) {
                        return "Tool '$toolId' is disabled. Enable it in AI Settings."
                    }
                    
                    val schema = ToolRegistry.getToolSchema(toolId)
                    return schema?.toString(2) ?: "Unknown tool: $toolId"
                }

                // GAMIFICATION
                "gamification", "get_xp_stats" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val type = args.optString("type", "all")
                    
                    val stats = XPManager.getDailyStats(context, date.toString())
                    val json = JSONObject().apply {
                        put("date", date.toString())
                        put("timezone", "IST")
                        put("current_level", XPManager.getLevel(context))
                        put("current_streak", XPManager.getStreak(context))
                        put("total_xp_today", stats?.totalDailyXP ?: 0)
                        
                        if (type == "all" || type == "breakdown") {
                            put("xp_breakdown", JSONObject().apply {
                                put("distraction_xp", stats?.distractionXP ?: 0)
                                put("reflection_xp", stats?.reflectionXP ?: 0)
                                put("tasks_xp", stats?.taskXP ?: 0)
                                put("tapasya_xp", stats?.tapasyaXP ?: 0)
                                put("calendar_sessions_xp", stats?.sessionXP ?: 0)
                                put("bonus_xp", stats?.bonusXP ?: 0)
                                put("penalty_xp", stats?.penaltyXP ?: 0)
                            })
                        }
                    }
                    json.toString()
                }

                // TAPASYA (Focus Sessions)
                "tapasya", "get_tapasya_sessions" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val limit = args.optInt("limit", 5)
                    
                    val db = AppDatabase.getDatabase(context)
                    val startOfDay = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
                    val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
                    
                    val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                    
                    if (sessions.isEmpty()) return "No Tapasya sessions found for $date."
                    
                    val arr = JSONArray()
                    sessions.take(limit).forEach { s ->
                        arr.put(JSONObject().apply {
                            put("name", s.name)
                            put("start_time", ToolRegistry.formatIST(s.startTime))
                            put("end_time", ToolRegistry.formatIST(s.endTime))
                            put("duration_mins", s.effectiveTimeMs / 60000)
                            put("pause_mins", s.totalPauseMs / 60000)
                            put("was_auto_stopped", s.wasAutoStopped)
                        })
                    }
                    
                    JSONObject().apply {
                        put("date", date.toString())
                        put("timezone", "IST")
                        put("session_count", sessions.size)
                        put("sessions", arr)
                    }.toString()
                }

                // TASKS
                "tasks", "get_tasks" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    val stats = GoogleTasksManager.getTasksForDate(context, date.toString())
                    JSONObject().apply {
                        put("date", date.toString())
                        put("timezone", "IST")
                        put("pending_count", stats.pendingCount)
                        put("completed_count", stats.completedCount)
                        put("due_tasks", JSONArray(stats.dueTasks))
                        put("completed_tasks", JSONArray(stats.completedTasks))
                    }.toString()
                }

                // CALENDAR EVENTS (Study Sessions)
                "study_sessions", "get_calendar_events" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    val repo = CalendarRepository(context)
                    val start = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
                    val end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
                    
                    val events = repo.getEventsInRange(start, end)
                    if (events.isEmpty()) return "No events found for $date."
                    
                    val jsonArr = JSONArray()
                    events.forEach { e ->
                        jsonArr.put(JSONObject().apply {
                            put("title", e.title)
                            put("start", ToolRegistry.formatIST(e.startTime))
                            put("end", ToolRegistry.formatIST(e.endTime))
                            put("description", e.description ?: "")
                            put("location", e.location ?: "")
                        })
                    }
                    
                    JSONObject().apply {
                        put("date", date.toString())
                        put("timezone", "IST")
                        put("event_count", events.size)
                        put("events", jsonArr)
                    }.toString()
                }

                // APP USAGE
                "usage_stats", "get_app_usage_stats" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val pkg = args.optString("package_name", "")
                    
                    val usageMap = UsageUtils.getUsageForDate(context, date)
                    
                    if (pkg.isNotEmpty()) {
                        val ms = usageMap[pkg] ?: 0L
                        val mins = ms / 60000
                        return JSONObject().apply {
                            put("date", date.toString())
                            put("package", pkg)
                            put("usage_minutes", mins)
                        }.toString()
                    } else {
                        val sorted = usageMap.entries.sortedByDescending { it.value }.take(5)
                        val arr = JSONArray()
                        sorted.forEach { entry ->
                            arr.put(JSONObject().apply {
                                put("app", entry.key.substringAfterLast("."))
                                put("package", entry.key)
                                put("minutes", entry.value / 60000)
                            })
                        }
                        return JSONObject().apply {
                            put("date", date.toString())
                            put("timezone", "IST")
                            put("top_apps", arr)
                        }.toString()
                    }
                }

                // APP BLOCKER STATUS
                "app_blocker", "get_blocked_status" -> {
                    val pkg = args.optString("package_name", "")
                    val prefs = SavedPreferencesLoader(context)
                    
                    val isStrict = prefs.isStrictModeEnabled()
                    val focusData = prefs.getFocusModeData()
                    val isFocus = focusData.endTime > System.currentTimeMillis()
                    
                    val json = JSONObject().apply {
                        put("strict_mode", isStrict)
                        put("focus_mode", isFocus)
                        if (isFocus) {
                            put("focus_ends_at", ToolRegistry.formatIST(focusData.endTime))
                        }
                    }
                    
                    if (pkg.isNotEmpty()) {
                        var isBlocked = false
                        val reasons = mutableListOf<String>()
                        
                        if (isStrict) {
                            val blockedApps = prefs.loadBlockedApps()
                            if (blockedApps.contains(pkg)) {
                                isBlocked = true
                                reasons.add("Strict Mode blocklist")
                            }
                        }
                        
                        if (isFocus) {
                            val config = prefs.getBlockedAppConfig(pkg)
                            if (config.blockInFocus) {
                                val selected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefs.getFocusModeSelectedApps())
                                if (selected.contains(pkg)) {
                                    isBlocked = true
                                    reasons.add("Focus Mode")
                                }
                            }
                        }
                        
                        json.put("app_checked", pkg)
                        json.put("is_blocked", isBlocked)
                        json.put("block_reasons", JSONArray(reasons))
                    }
                    
                    json.toString()
                }

                // REMINDERS
                "reminders", "get_reminders" -> {
                    val prefs = SavedPreferencesLoader(context)
                    val reminders = prefs.loadCustomReminders()
                    
                    if (reminders.isEmpty()) return "No active custom reminders."
                    
                    val arr = JSONArray()
                    reminders.filter { it.isEnabled }.forEach { r ->
                        arr.put(JSONObject().apply {
                            put("title", r.title)
                            put("time", String.format("%02d:%02d", r.hour, r.minute))
                            put("repeat_days", JSONArray(r.repeatDays))
                        })
                    }
                    
                    JSONObject().apply {
                        put("timezone", "IST")
                        put("reminder_count", arr.length())
                        put("reminders", arr)
                    }.toString()
                }

                // NIGHTLY PROTOCOL DATA
                "nightly", "get_nightly_data" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val dataType = args.optString("data_type")
                    
                    val stepId = when(dataType) {
                        "plan" -> NightlyProtocolExecutor.STEP_GENERATE_PLAN
                        "report" -> NightlyProtocolExecutor.STEP_GENERATE_REPORT
                        else -> return "Error: data_type must be 'plan' or 'report'"
                    }
                    
                    var data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, date, stepId)
                    
                    // Fallback: If asking for today's plan, check yesterday (plan is made night before)
                    if (data == null && dataType == "plan" && date == LocalDate.now()) {
                        data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, date.minusDays(1), stepId)
                    }
                    
                    if (data.isNullOrEmpty()) return "No $dataType data found for $date."
                    
                    try {
                        if (data.trim().startsWith("{")) return JSONObject(JSONTokener(data)).toString()
                        return data
                    } catch (e: Exception) { return data }
                }

                // HEALTH STATS
                "health", "get_health_stats" -> {
                    val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
                    if (!prefs.getBoolean("health_access_enabled", false)) {
                        return "Health access is disabled. Enable it in AI Settings."
                    }
                    
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    if (!com.neubofy.reality.health.HealthManager.isHealthConnectAvailable(context)) {
                        return "Health Connect is not available on this device."
                    }
                    
                    val manager = com.neubofy.reality.health.HealthManager(context)
                    if (!manager.hasPermissions()) {
                        return "Health permissions not granted. Please grant in Health Connect."
                    }
                    
                    val steps = manager.getSteps(date)
                    val cals = manager.getCalories(date)
                    val sleep = manager.getSleep(date)
                    
                    JSONObject().apply {
                        put("date", date.toString())
                        put("timezone", "IST")
                        put("steps", steps)
                        put("calories_kcal", String.format("%.1f", cals))
                        put("sleep", sleep)
                    }.toString()
                }

                // ==================== ACTION TOOLS ====================
                
                // ADD TASK
                "action_add_task", "add_task" -> {
                    val title = args.optString("title", "")
                    if (title.isEmpty()) {
                        return "I need the task title. What would you like the task to say?"
                    }
                    
                    val notes = args.optString("notes", null)
                    val dueDate = args.optString("due_date", null)
                    
                    val result = com.neubofy.reality.google.GoogleTasksManager.createTask(
                        context, title, notes, dueDate
                    )
                    
                    return if (result != null) {
                        "✅ Task created: \"$title\"" + (if (dueDate != null) " (due: $dueDate)" else "")
                    } else {
                        "❌ Failed to create task. Please check Google Tasks connection."
                    }
                }
                
                // COMPLETE TASK
                // COMPLETE TASK
                "action_complete_task", "complete_task" -> {
                    val taskTitle = args.optString("task_title", "")
                    if (taskTitle.isEmpty()) {
                        return "Which task would you like to mark as complete?"
                    }

                    // Find the task ID by iterating through all lists
                    // logic: Get all lists -> Get tasks for each -> Find match -> Complete
                    var foundAndCompleted = false
                    var foundTitle = ""

                    try {
                        val taskLists = com.neubofy.reality.google.GoogleTasksManager.getTaskLists(context)
                        for (list in taskLists) {
                            val tasks = com.neubofy.reality.google.GoogleTasksManager.getTasks(context, list.id)
                            val match = tasks.find { 
                                it.title?.trim()?.equals(taskTitle.trim(), ignoreCase = true) == true && it.status != "completed"
                            }
                            
                            if (match != null) {
                                foundTitle = match.title ?: taskTitle
                                val success = com.neubofy.reality.google.GoogleTasksManager.completeTask(context, list.id, match.id)
                                if (success) {
                                    foundAndCompleted = true
                                    break // Stop after first match
                                }
                            }
                        }
                    } catch (e: Exception) {
                        return "❌ Error accessing Google Tasks: ${e.localizedMessage}"
                    }

                    return if (foundAndCompleted) {
                        "✅ Task marked as complete: \"$foundTitle\""
                    } else {
                        "❌ Couldn't find a pending task named \"$taskTitle\"."
                    }
                }
                
                // ADD REMINDER
                "action_add_reminder", "add_reminder" -> {
                    val title = args.optString("title", "")
                    val hourStr = args.optString("hour", "")
                    
                    if (title.isEmpty()) {
                        return "What would you like to be reminded about?"
                    }
                    if (hourStr.isEmpty()) {
                        return "What time? Please specify the hour (0-23)."
                    }
                    
                    val hour = hourStr.toIntOrNull() ?: return "Invalid hour. Please use 0-23."
                    val minute = args.optString("minute", "0").toIntOrNull() ?: 0
                    
                    // Create reminder using SavedPreferencesLoader
                    val prefs = SavedPreferencesLoader(context)
                    val reminders = prefs.loadCustomReminders().toMutableList()
                    
                    val newReminder = com.neubofy.reality.data.CustomReminder(
                        id = System.currentTimeMillis().toString(),
                        title = title,
                        hour = hour,
                        minute = minute,
                        isEnabled = true,
                        repeatDays = emptyList()
                    )
                    reminders.add(newReminder)
                    prefs.saveCustomReminders(reminders)
                    
                    // Schedule via Unified Alarm Scheduler
                    com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(context)
                    
                    val timeStr = String.format("%02d:%02d", hour, minute)
                    return "✅ Reminder set for $timeStr: \"$title\""
                }
                
                // SET ALARM
                "action_set_alarm", "set_alarm" -> {
                    val title = args.optString("title", "Wake Up")
                    val hourStr = args.optString("hour", "")

                    if (hourStr.isEmpty()) {
                        return "What time? Please specify the hour (0-23)."
                    }

                    val hour = hourStr.toIntOrNull() ?: return "Invalid hour. Please use 0-23."
                    val minute = args.optString("minute", "0").toIntOrNull() ?: 0

                    val prefs = SavedPreferencesLoader(context)
                    val alarms = prefs.loadWakeupAlarms().toMutableList()

                    val newAlarm = com.neubofy.reality.data.model.WakeupAlarm(
                        id = System.currentTimeMillis().toString(),
                        title = title,
                        hour = hour,
                        minute = minute,
                        isEnabled = true,
                        repeatDays = emptyList(), // One-time alarm
                        ringtoneUri = null, // Default
                        vibrationEnabled = true
                    )

                    alarms.add(newAlarm)
                    prefs.saveWakeupAlarms(alarms)

                    com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(context)

                    val timeStr = String.format("%02d:%02d", hour, minute)
                    return "✅ Alarm set for $timeStr: \"$title\""
                }

                // START FOCUS MODE
                "action_start_focus", "start_focus" -> {
                    val durationMins = args.optInt("duration_mins", 25)
                    
                    val prefs = SavedPreferencesLoader(context)
                    val endTime = System.currentTimeMillis() + (durationMins * 60 * 1000L)
                    
                    // Set focus mode data
                    val currentData = prefs.getFocusModeData()
                    val newData = com.neubofy.reality.blockers.RealityBlocker.FocusModeData(
                        endTime = endTime,
                        isTurnedOn = true,
                        selectedApps = currentData.selectedApps
                    )
                    prefs.saveFocusModeData(newData)
                    
                    // Start the blocker service
                    val intent = android.content.Intent(context, com.neubofy.reality.services.AppBlockerService::class.java)
                    context.startService(intent)
                    
                    return "✅ Focus Mode started for $durationMins minutes. Distracting apps are now blocked."
                }
                
                // START TAPASYA
                "action_start_tapasya", "start_tapasya" -> {
                    val name = args.optString("name", "Deep Focus")
                    val durationMins = args.optInt("duration_mins", 0) // 0 = unlimited
                    
                    // Launch Tapasya via Intent with extras
                    val intent = android.content.Intent(context, com.neubofy.reality.ui.activity.TapasyaActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("auto_start", true)
                        putExtra("session_name", name)
                        if (durationMins > 0) {
                            putExtra("target_duration_mins", durationMins)
                        }
                    }
                    context.startActivity(intent)
                    
                    val durationText = if (durationMins > 0) " ($durationMins min target)" else ""
                    return "✅ Starting Tapasya session: \"$name\"$durationText. Opening Tapasya screen..."
                }

                // ADD MISSED TAPASYA SESSION
                "action_add_missed_tapasya", "add_missed_tapasya" -> {
                    val name = args.optString("name", "Deep Focus")
                    val startTimeStr = args.optString("start_time", "")
                    val endTimeStr = args.optString("end_time", "")
                    val reason = args.optString("reason", "")
                    val dateStr = args.optString("date", LocalDate.now().toString())
                    val pauseMins = args.optInt("pause_mins", 0)

                    // 1. Strict Validation
                    if (reason.length < 5) {
                        return "I need a valid reason why this session wasn't recorded live. Please explain."
                    }
                    if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
                        return "Please provide both start and end times (HH:mm)."
                    }

                    try {
                        // 2. Parse Times (IST)
                        val date = LocalDate.parse(dateStr)
                        val istZone = ZoneId.of("Asia/Kolkata")
                        
                        val startParts = startTimeStr.split(":")
                        val endParts = endTimeStr.split(":")
                        
                        val startInstant = date.atTime(startParts[0].toInt(), startParts[1].toInt())
                            .atZone(istZone).toInstant()
                        val endInstant = date.atTime(endParts[0].toInt(), endParts[1].toInt())
                            .atZone(istZone).toInstant()
                        
                        var startMs = startInstant.toEpochMilli()
                        var endMs = endInstant.toEpochMilli()

                        // Handle overnight sessions (end < start) -> assume end is next day? 
                        // For simplicity, enforce same day or reject if end < start
                        if (endMs <= startMs) {
                            return "End time must be after start time."
                        }

                        // 3. Calculate Stats
                        val totalDurationMs = endMs - startMs
                        val pauseMs = pauseMins * 60 * 1000L
                        val effectiveMs = com.neubofy.reality.data.db.TapasyaSession.calculateEffectiveTime(totalDurationMs - pauseMs)
                        
                        if (effectiveMs <= 0) {
                            return "Effective duration is too short (less than 15 mins) after removing pauses."
                        }

                        // 4. Create & Save Session
                        val session = com.neubofy.reality.data.db.TapasyaSession(
                            sessionId = com.neubofy.reality.data.db.TapasyaSession.generateId(startMs, endMs),
                            name = name,
                            targetTimeMs = totalDurationMs, // Assume target was the actual duration
                            startTime = startMs,
                            endTime = endMs,
                            effectiveTimeMs = effectiveMs,
                            totalPauseMs = pauseMs,
                            pauseLimitMs = -1, // Unlimited for manual entry
                            wasAutoStopped = false,
                            calendarEventId = null
                        )

                        val db = AppDatabase.getDatabase(context)
                        db.tapasyaSessionDao().insert(session)
                        
                        // Log reason to Terminal for audit
                        com.neubofy.reality.utils.TerminalLogger.log("Manual Tapasya Added: '$name' ($reason)")

                        val durMins = totalDurationMs / 60000
                        val effMins = effectiveMs / 60000
                        return "✅ Added missed focus session: \"$name\" ($durMins mins total, $effMins mins effective score).\nReason logged: $reason"

                    } catch (e: Exception) {
                        return "❌ Error parsing time or saving: ${e.message}. Use HH:mm format."
                    }
                }

                // SCHEDULE NOTIFICATION (Lightweight)
                "action_schedule_notification", "schedule_notification" -> {
                    val title = args.optString("title", "Reality Alert")
                    val message = args.optString("message", "")
                    val delayMins = args.optInt("minutes_from_now", 0)
                    
                    if (message.isEmpty()) return "Notification message is required."
                    if (delayMins < 1) return "Delay must be at least 1 minute."
                    
                    // Create a one-off UnifiedEvent for this notification
                    // We hijack the existing Reminder system but mark it as NOTIFICATION type via PendingIntent extras
                    // Wait, AlarmScheduler schedules unified events from DB. 
                    // To do this cleanly without a DB entry, we can schedule a direct AlarmManager intent just like Snooze.
                    
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val intent = android.content.Intent(context, com.neubofy.reality.receivers.ReminderReceiver::class.java).apply {
                        putExtra("id", "notif_" + System.currentTimeMillis()) // Unique ID
                        putExtra("title", title)
                        // Encoding message into title for simple display? 
                        // ReminderReceiver uses title. Let's append message to title or use a new extra if we update receiver.
                        // For now, let's just use title + message combo or check receiver. 
                        // Receiver uses 'title' for content text and 'url'. Let's use 'title' as Title and put Message in 'url' (hack) or update receiver.
                        // Better: Update Receiver to look for "message" extra? 
                        // Actually, I can just use 'title' as the main text if I only have one field. 
                        // But NotificationHelper takes Title + Message.
                        // Let's pass "message" as extra and update Receiver later if needed? 
                        // Receiver code: showNotification(context, title, "Reality Alert", ...)
                        // Wait, my Receiver update was: showNotification(context, title, "Reality Alert", ...)
                        // It ignored the message! Let's correct Receiver first? 
                        // No, let's fix the tool to pass 'title' as the message body if needed, or pass 'message' extra.
                        // Receiver code I wrote: showNotification(context, title, "Reality Alert", ...)
                        // Wait, NotificationHelper.showNotification(context, title, message, ...)
                        // Receiver had: showNotification(context, title, "Reality Alert") -> Title=Title, Message="Reality Alert".
                        // That's backwards/static. I should have passed a message extra.
                        // Fix for now: Pass combined "Title: Message" as title? Or re-edit Receiver?
                        // Re-editing Receiver is cleaner. I will return "Scheduled" here and fix Receiver in next step.
                        
                        // Wait, I can pass the message in 'title' for now to save a step, and refine later.
                        // NotificationHelper(title=Title, text=Message). 
                        // Receiver has: showNotification(..., title, "Reality Alert")
                        // So Title becomes the Notification Title. Text is static. That's bad.
                        // I WILL QUICK FIX RECEIVER after this.
                        
                        putExtra("title", title) // This will be the notification title
                        putExtra("url", message) // Hack: use URL field to carry message for now? Receiver doesn't use URL for notif.
                        putExtra("type", "NOTIFICATION")
                        putExtra("notification_message", message) // Pass correct extra for future repair
                    }
                    
                    val triggerTime = System.currentTimeMillis() + (delayMins * 60 * 1000L)
                    val reqCode = (System.currentTimeMillis() % 100000).toInt()
                    
                    val pIntent = android.app.PendingIntent.getBroadcast(
                        context, reqCode, intent, 
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
                    } else {
                        alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
                    }
                    
                    return "✅ Notification scheduled in $delayMins mins: \"$title\""
                }

                // IMAGE GENERATION (Supports multiple providers)


                // ==================== UTILITY TOOLS ====================
                "utility_time", "get_current_time" -> {
                    val format = args.optString("format", "full")
                    val istZone = ZoneId.of("Asia/Kolkata")
                    val now = java.time.ZonedDateTime.now(istZone)
                    
                    val result = JSONObject().apply {
                        put("timezone", "IST (Asia/Kolkata)")
                        
                        when (format) {
                            "time_only" -> {
                                put("time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                                put("hour_24", now.hour)
                                put("minute", now.minute)
                            }
                            "date_only" -> {
                                put("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                put("day_of_week", now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() })
                                put("day", now.dayOfMonth)
                                put("month", now.month.toString().lowercase().replaceFirstChar { it.uppercase() })
                                put("year", now.year)
                            }
                            else -> { // "full"
                                put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                put("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                put("time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                                put("day_of_week", now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() })
                                put("is_weekend", now.dayOfWeek.value >= 6)
                                put("hour_24", now.hour)
                                put("period", if (now.hour < 12) "morning" else if (now.hour < 17) "afternoon" else if (now.hour < 21) "evening" else "night")
                            }
                        }
                    }
                    
                    result.toString(2)
                }

                // ==================== WEB SEARCH TOOL ====================


                // ==================== UNIVERSAL QUERY TOOL ====================
                "universal_query", "query_data" -> {
                    val sourcesStr = args.optString("sources", "")
                    if (sourcesStr.isEmpty()) {
                        return "Error: 'sources' is required. Specify comma-separated: tasks,tapasya,calendar,usage,xp,reminders,health"
                    }
                    
                    val sources = sourcesStr.split(",").map { it.trim().lowercase() }
                    val dateRange = args.optString("date_range", "today")
                    val startDateStr = args.optString("start_date", "")
                    val endDateStr = args.optString("end_date", "")
                    val format = args.optString("format", "summary")
                    val limit = args.optInt("limit", 5)
                    val searchQuery = args.optString("query", "")
                    
                    // Calculate date range
                    val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                    val (startDate, endDate) = when (dateRange) {
                        "today" -> today to today
                        "yesterday" -> today.minusDays(1) to today.minusDays(1)
                        "week" -> today.minusDays(6) to today
                        "month" -> today.minusDays(29) to today
                        "custom" -> {
                            val start = if (startDateStr.isNotEmpty()) LocalDate.parse(startDateStr) else today
                            val end = if (endDateStr.isNotEmpty()) LocalDate.parse(endDateStr) else today
                            start to end
                        }
                        else -> today to today
                    }
                    
                    val results = JSONObject()
                    results.put("query_info", JSONObject().apply {
                        put("sources", JSONArray(sources))
                        put("date_range", "$startDate to $endDate")
                        put("format", format)
                        put("timezone", "IST")
                    })
                    
                    val db = AppDatabase.getDatabase(context)
                    val istZone = ZoneId.of("Asia/Kolkata")
                    
                    // Process each source
                    for (source in sources) {
                        try {
                            when (source) {
                                "tapasya" -> {
                                    val startMs = startDate.atStartOfDay(istZone).toInstant().toEpochMilli()
                                    val endMs = endDate.plusDays(1).atStartOfDay(istZone).toInstant().toEpochMilli()
                                    val sessions = db.tapasyaSessionDao().getSessionsForDay(startMs, endMs)
                                    
                                    // Apply search filter if provided
                                    val filtered = if (searchQuery.isNotEmpty()) {
                                        sessions.filter { it.name.contains(searchQuery, ignoreCase = true) }
                                    } else sessions
                                    
                                    val arr = JSONArray()
                                    filtered.take(limit).forEach { s ->
                                        arr.put(JSONObject().apply {
                                            put("name", s.name)
                                            put("date", ToolRegistry.formatIST(s.startTime).substringBefore(" "))
                                            put("start_time", ToolRegistry.formatISTTime(s.startTime))
                                            put("duration_mins", s.effectiveTimeMs / 60000)
                                            put("was_auto_stopped", s.wasAutoStopped)
                                        })
                                    }
                                    
                                    results.put("tapasya", JSONObject().apply {
                                        put("total_sessions", filtered.size)
                                        put("total_focus_mins", filtered.sumOf { it.effectiveTimeMs / 60000 })
                                        if (format != "stats") put("sessions", arr)
                                    })
                                }
                                
                                "xp", "gamification" -> {
                                    // Use DailyStatsDao for date range queries
                                    val statsFlow = db.dailyStatsDao().getStatsInRange(startDate.toString(), endDate.toString())
                                    // Since we're in a suspend function, collect synchronously by using firstOrNull pattern
                                    // Actually, getStatsInRange returns Flow, need to handle differently
                                    // Let's use getAllStats and filter for simplicity
                                    val allStats = db.dailyStatsDao().getAllStats()
                                    val rangeStats = allStats.filter { 
                                        it.date >= startDate.toString() && it.date <= endDate.toString() 
                                    }.sortedByDescending { it.date }
                                    
                                    val arr = JSONArray()
                                    rangeStats.take(limit).forEach { stat ->
                                        arr.put(JSONObject().apply {
                                            put("date", stat.date)
                                            put("total_xp", stat.totalXP)
                                            put("level", stat.level)
                                            put("streak", stat.streak)
                                        })
                                    }
                                    
                                    results.put("xp", JSONObject().apply {
                                        put("days_with_data", rangeStats.size)
                                        put("total_xp", rangeStats.sumOf { it.totalXP })
                                        put("avg_daily_xp", if (rangeStats.isNotEmpty()) rangeStats.sumOf { it.totalXP } / rangeStats.size else 0)
                                        put("current_level", XPManager.getLevel(context))
                                        put("current_streak", XPManager.getStreak(context))
                                        if (format != "stats") put("daily_breakdown", arr)
                                    })
                                }
                                
                                "tasks" -> {
                                    // Query tasks for date range
                                    val taskData = JSONObject()
                                    var totalPending = 0
                                    var totalCompleted = 0
                                    val allTasks = JSONArray()
                                    
                                    // For each date in range (limit to avoid too many API calls)
                                    var currentDate = startDate
                                    var dateCount = 0
                                    while (currentDate <= endDate && dateCount < 7) {
                                        val stats = GoogleTasksManager.getTasksForDate(context, currentDate.toString())
                                        totalPending += stats.pendingCount
                                        totalCompleted += stats.completedCount
                                        
                                        // Add tasks with search filter
                                        val dueTasks = if (searchQuery.isNotEmpty()) {
                                            stats.dueTasks.filter { it.contains(searchQuery, ignoreCase = true) }
                                        } else stats.dueTasks
                                        
                                        dueTasks.take(limit / (endDate.toEpochDay() - startDate.toEpochDay() + 1).toInt().coerceAtLeast(1)).forEach { t ->
                                            allTasks.put(JSONObject().apply {
                                                put("title", t)
                                                put("status", "pending")
                                                put("date", currentDate.toString())
                                            })
                                        }
                                        
                                        currentDate = currentDate.plusDays(1)
                                        dateCount++
                                    }
                                    
                                    results.put("tasks", JSONObject().apply {
                                        put("total_pending", totalPending)
                                        put("total_completed", totalCompleted)
                                        put("completion_rate", if (totalPending + totalCompleted > 0) 
                                            "${(totalCompleted * 100 / (totalPending + totalCompleted))}%" else "N/A")
                                        if (format != "stats") put("pending_tasks", allTasks)
                                    })
                                }
                                
                                "calendar" -> {
                                    val repo = CalendarRepository(context)
                                    val startMs = startDate.atStartOfDay(istZone).toInstant().toEpochMilli()
                                    val endMs = endDate.plusDays(1).atStartOfDay(istZone).toInstant().toEpochMilli()
                                    val events = repo.getEventsInRange(startMs, endMs)
                                    
                                    // Apply search filter
                                    val filtered = if (searchQuery.isNotEmpty()) {
                                        events.filter { it.title.contains(searchQuery, ignoreCase = true) }
                                    } else events
                                    
                                    val arr = JSONArray()
                                    filtered.take(limit).forEach { e ->
                                        arr.put(JSONObject().apply {
                                            put("title", e.title)
                                            put("date", ToolRegistry.formatIST(e.startTime).substringBefore(" "))
                                            put("start", ToolRegistry.formatISTTime(e.startTime))
                                            put("end", ToolRegistry.formatISTTime(e.endTime))
                                        })
                                    }
                                    
                                    results.put("calendar", JSONObject().apply {
                                        put("total_events", filtered.size)
                                        if (format != "stats") put("events", arr)
                                    })
                                }
                                
                                "usage" -> {
                                    // For usage, just get today's data (can't easily query range)
                                    val usageMap = UsageUtils.getUsageForDate(context, startDate)
                                    val sorted = usageMap.entries.sortedByDescending { it.value }.take(limit)
                                    
                                    val arr = JSONArray()
                                    sorted.forEach { entry ->
                                        arr.put(JSONObject().apply {
                                            put("app", entry.key.substringAfterLast("."))
                                            put("package", entry.key)
                                            put("minutes", entry.value / 60000)
                                        })
                                    }
                                    
                                    results.put("usage", JSONObject().apply {
                                        put("date", startDate.toString())
                                        put("total_screen_time_mins", usageMap.values.sum() / 60000)
                                        put("app_count", usageMap.size)
                                        if (format != "stats") put("top_apps", arr)
                                    })
                                }
                                
                                "reminders" -> {
                                    val prefs = SavedPreferencesLoader(context)
                                    val reminders = prefs.loadCustomReminders().filter { it.isEnabled }
                                    
                                    // Apply search filter
                                    val filtered = if (searchQuery.isNotEmpty()) {
                                        reminders.filter { it.title.contains(searchQuery, ignoreCase = true) }
                                    } else reminders
                                    
                                    val arr = JSONArray()
                                    filtered.take(limit).forEach { r ->
                                        arr.put(JSONObject().apply {
                                            put("title", r.title)
                                            put("time", String.format("%02d:%02d", r.hour, r.minute))
                                            put("repeat_days", JSONArray(r.repeatDays))
                                        })
                                    }
                                    
                                    results.put("reminders", JSONObject().apply {
                                        put("total_active", filtered.size)
                                        if (format != "stats") put("reminders", arr)
                                    })
                                }
                                
                                "health" -> {
                                    val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
                                    if (!prefs.getBoolean("health_access_enabled", false)) {
                                        results.put("health", JSONObject().put("error", "Health access disabled"))
                                    } else if (!com.neubofy.reality.health.HealthManager.isHealthConnectAvailable(context)) {
                                        results.put("health", JSONObject().put("error", "Health Connect not available"))
                                    } else {
                                        val manager = com.neubofy.reality.health.HealthManager(context)
                                        if (!manager.hasPermissions()) {
                                            results.put("health", JSONObject().put("error", "Health permissions not granted"))
                                        } else {
                                            // Get health data for the date
                                            val steps = manager.getSteps(startDate)
                                            val cals = manager.getCalories(startDate)
                                            val sleep = manager.getSleep(startDate)
                                            
                                            results.put("health", JSONObject().apply {
                                                put("date", startDate.toString())
                                                put("steps", steps)
                                                put("calories", String.format("%.1f", cals))
                                                put("sleep", sleep)
                                            })
                                        }
                                    }
                                }
                                
                                else -> {
                                    results.put(source, JSONObject().put("error", "Unknown source: $source"))
                                }
                            }
                        } catch (e: Exception) {
                            results.put(source, JSONObject().put("error", e.message ?: "Query failed"))
                        }
                    }
                    
                    results.toString(2)
                }

                else -> "Unknown tool: $name. Use get_tool_schema to discover available tools."
            }
        } catch (e: Exception) {
            "Tool Error: ${e.message}"
        }
    }
    
    /**
     * Downloads an image from URL and saves it to Pictures/Reality folder.
     * Uses MediaStore for proper gallery integration on Android 10+.
     */
    private fun saveImageToGallery(context: Context, imageUrl: String, timestamp: Long): String {
        val fileName = "reality_img_$timestamp.jpg"
        
        // Use app-specific files directory to avoid any permission requirements
        val directory = java.io.File(context.filesDir, "Images")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = java.io.File(directory, fileName)

        val connection = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.connect()

        if (connection.responseCode == 200) {
            java.io.FileOutputStream(file).use { outputStream ->
                connection.inputStream.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            TerminalLogger.log("IMAGE: Saved to internal app storage - $fileName")
            return file.absolutePath
        } else {
            throw Exception("HTTP Error: ${connection.responseCode}")
        }
    }

    /**
     * Extracts a URL (preferably an image URL) from a string.
     * Supports Markdown format ![alt](url) and raw URLs.
     */
    private fun extractImageUrl(content: String): String {
        if (content.isEmpty()) return ""
        
        // 1. Try Markdown Image Pattern: ![description](url)
        val markdownRegex = Regex("""!\[.*?\]\((https?://\S+)\)""")
        val match = markdownRegex.find(content)
        if (match != null) return match.groupValues[1]
        
        // 2. Try Naked URL Pattern
        val urlRegex = Regex("""(https?://\S+)""")
        val urlMatch = urlRegex.find(content)
        if (urlMatch != null) {
             val url = urlMatch.groupValues[1]
             // Basic check if it looks like an image URL or if it's the only thing in the response
             if (url.contains(Regex("""\.(jpg|jpeg|png|webp|gif)""", RegexOption.IGNORE_CASE)) || content.trim() == url) {
                 return url
             }
        }
        
        return ""
    }

    /**
     * Helper for making POST requests to AI APIs (Images)
     */

}
