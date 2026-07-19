package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.repository.CalendarRepository
import com.neubofy.reality.utils.XPManager
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.health.HealthManager
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.SecurePreferences
import com.neubofy.reality.utils.ToolRegistry
import com.neubofy.reality.utils.UsageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class UniversalQueryTool : AgentTool {
    override val id = "universal_query"
    override val name = "Smart Data Query"
    override val shortDesc = "Advanced cross-source querying with filters"
    override val category = ToolCategory.UTILITY

    override fun getSchema(): JSONObject {
        return createSchema(
            "universal_query",
            "Powerful universal data query tool. Fetch targeted data from any source with natural language or structured queries. Use this when you need specific, filtered data from multiple sources.",
            mapOf(
                "sources" to "Required: Comma-separated list: 'tasks,tapasya,calendar,usage,xp,reminders,health'",
                "query" to "Optional: Natural language query (AI will parse)",
                "date_range" to "Optional: 'today', 'yesterday', 'week', 'month', 'custom'",
                "start_date" to "For custom range: YYYY-MM-DD",
                "end_date" to "For custom range: YYYY-MM-DD",
                "filters" to "Optional: JSON object with source-specific filters",
                "format" to "Optional: 'summary', 'detailed', 'stats' (default: summary)",
                "limit" to "Max items per source (default: 5)"
            ),
            required = listOf("sources")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
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

        for (source in sources) {
            try {
                when (source) {
                    "tapasya" -> {
                        val startMs = startDate.atStartOfDay(istZone).toInstant().toEpochMilli()
                        val endMs = endDate.plusDays(1).atStartOfDay(istZone).toInstant().toEpochMilli()
                        val sessions = db.tapasyaSessionDao().getSessionsForDay(startMs, endMs)

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
                        var totalPending = 0
                        var totalCompleted = 0
                        val allTasks = JSONArray()

                        var currentDate = startDate
                        var dateCount = 0
                        while (currentDate <= endDate && dateCount < 7) {
                            val stats = GoogleTasksManager.getTasksForDate(context, currentDate.toString())
                            totalPending += stats.pendingCount
                            totalCompleted += stats.completedCount

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

                    "health" -> {
                        val prefs = SecurePreferences.get(context, "ai_prefs")
                        if (!prefs.getBoolean("health_access_enabled", false)) {
                            results.put("health", JSONObject().put("error", "Health access disabled"))
                        } else if (!HealthManager.isHealthConnectAvailable(context)) {
                            results.put("health", JSONObject().put("error", "Health Connect not available"))
                        } else {
                            val manager = HealthManager(context)
                            if (!manager.hasPermissions()) {
                                results.put("health", JSONObject().put("error", "Health permissions not granted"))
                            } else {
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

        return results.toString(2)
    }
}
