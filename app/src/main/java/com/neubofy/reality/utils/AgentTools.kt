package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.utils.XPManager
import com.neubofy.reality.utils.UsageUtils
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.data.repository.CalendarRepository
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AgentTools {

    // --- Tool Definitions (OpenAI/Groq Format) ---
    val definitions = JSONArray().apply {
        
        // 1. Get XP Stats (Granular)
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_xp_stats")
                put("description", "Get gamification stats (XP, Level, Streak) for a specific date.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format. Default is today.")
                        })
                        put("type", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional filter: 'total', 'screen_time', 'reflection', 'tasks', 'tapasya'.")
                        })
                    })
                })
            })
        })

        // 2. Get Nightly Data (History)
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_nightly_data")
                put("description", "Get HISTORICAL data associated with the Nightly Protocol (Plan, Generated Reports). Use get_tasks or get_calendar_events for live data.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format.")
                        })
                        put("data_type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().put("plan").put("report"))
                            put("description", "Type: 'plan' (AI Plan), 'report' (Daily Summary).")
                        })
                    })
                    put("required", JSONArray().put("data_type"))
                })
            })
        })

        // 3. Get Live Tasks (API)
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_tasks")
                put("description", "Fetch LIVE tasks from Google Tasks API for a specific date.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format. Default is today.")
                        })
                    })
                })
            })
        })

        // 4. Get Live Calendar Events (Device)
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_calendar_events")
                put("description", "Fetch LIVE events from the Device Calendar (e.g., Study Sessions).")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format. Default is today.")
                        })
                    })
                })
            })
        })

        // 5. Get App Usage (Granular)
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_app_usage_stats")
                put("description", "Get app usage statistics.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format.")
                        })
                        put("package_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional: Specific package name.")
                        })
                    })
                })
            })
        })

        // 6. Get Blocked Status
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_blocked_status")
                put("description", "Check if an app is blocked.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("package_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional: Specific app.")
                        })
                    })
                })
            })
        })
        
        // 7. Get Reminders
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_reminders")
                put("description", "Get active reminders.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })
        
        // 8. Get Health Stats
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_health_stats")
                put("description", "Get device health data (Steps, Calories, Sleep) from Health Connect.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Date in YYYY-MM-DD format.")
                        })
                    })
                })
            })
        })
    }

    // --- Execution Logic ---
    suspend fun execute(context: Context, name: String, argsInfo: String): String {
        return try {
            val args = JSONObject(if (argsInfo.isBlank()) "{}" else argsInfo)
            
            when (name) {
                // 8. Health Stats
                "get_health_stats" -> {
                    // 1. Check Setting (Default OFF)
                    val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
                    if (!prefs.getBoolean("health_access_enabled", false)) {
                        return "Health access is disabled in Settings. Please enable 'AI Health Access' to use this feature."
                    }
                    
                    // 2. Check Permissions & Fetch
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    if (!com.neubofy.reality.health.HealthManager.isHealthConnectAvailable(context)) {
                        return "Health Connect is not available on this device."
                    }
                    
                    val manager = com.neubofy.reality.health.HealthManager(context)
                    if (!manager.hasPermissions()) {
                        return "Health Permissions Denied. Please grant Step/Sleep permissions in Health Connect."
                    }
                    
                    val steps = manager.getSteps(date)
                    val cals = manager.getCalories(date)
                    val sleep = manager.getSleep(date)
                    
                    return JSONObject().apply {
                        put("date", date.toString())
                        put("steps", steps)
                        put("calories_kcal", String.format("%.1f", cals))
                        put("sleep_session", sleep)
                    }.toString()
                }

                "get_xp_stats" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val type = args.optString("type", "all")
                    
                    val stats = XPManager.getDailyStats(context, date.toString())
                    val json = JSONObject().apply {
                        put("date", date.toString())
                        put("current_level", XPManager.getLevel(context))
                        put("current_streak", XPManager.getStreak(context))
                        put("total_xp_today", stats?.totalDailyXP ?: 0)
                        
                        if (type == "all" || type == "breakdown") {
                            put("xp_breakdown", JSONObject().apply {
                                put("screen_time_xp", stats?.screenTimeXP ?: 0)
                                put("reflection_xp", stats?.reflectionXP ?: 0)
                                put("tasks_xp", stats?.taskXP ?: 0)
                                put("focus_mode_xp", stats?.tapasyaXP ?: 0) // Was 'sessions_xp' -> Tapasya
                                put("calendar_sessions_xp", stats?.sessionXP ?: 0) // NEW: Calendar Sessions
                                put("bonus_xp", stats?.bonusXP ?: 0) // NEW: Bonus XP
                                put("penalty_xp", stats?.penaltyXP ?: 0)
                            })
                        }
                    }
                    json.toString()
                }

                "get_nightly_data" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val dataType = args.optString("data_type") // plan, report only
                    
                    val stepId = when(dataType) {
                        "plan" -> NightlyProtocolExecutor.STEP_GENERATE_PLAN
                        "report" -> NightlyProtocolExecutor.STEP_GENERATE_REPORT
                        else -> return "Use get_tasks or get_calendar_events for live data."
                    }
                    
                    var data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, date, stepId)
                     // Fallback check
                    if (data == null && dataType == "plan" && date == LocalDate.now()) {
                         val yesterday = date.minusDays(1)
                         data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, yesterday, stepId)
                    }
                    
                    if (data.isNullOrEmpty()) return "No historical data found for $dataType on $date."
                    
                    // Normalize JSON
                    try {
                         if (data.trim().startsWith("{")) return JSONObject(JSONTokener(data)).toString()
                         return data
                    } catch (e: Exception) { return data }
                }
                
                "get_tasks" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    val stats = GoogleTasksManager.getTasksForDate(context, date.toString())
                    val json = JSONObject().apply {
                        put("date", date.toString())
                        put("pending_count", stats.pendingCount)
                        put("completed_count", stats.completedCount)
                        put("due_tasks", JSONArray(stats.dueTasks))
                        put("completed_tasks", JSONArray(stats.completedTasks))
                    }
                    json.toString()
                }
                
                "get_calendar_events" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    
                    val repo = CalendarRepository(context)
                    val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    
                    val events = repo.getEventsInRange(start, end)
                    val jsonArr = JSONArray()
                    events.forEach { e ->
                        jsonArr.put(JSONObject().apply {
                            put("title", e.title)
                            put("start", java.time.Instant.ofEpochMilli(e.startTime).toString())
                            put("end", java.time.Instant.ofEpochMilli(e.endTime).toString())
                            put("description", e.description ?: "")
                            put("location", e.location ?: "")
                        })
                    }
                    if (events.isEmpty()) "No events found for $date." else jsonArr.toString()
                }

                "get_app_usage_stats" -> {
                    val dateStr = args.optString("date", "")
                    val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
                    val pkg = args.optString("package_name", "")
                    
                    val usageMap = UsageUtils.getUsageForDate(context, date)
                    
                    if (pkg.isNotEmpty()) {
                        val ms = usageMap[pkg] ?: 0L
                        val mins = ms / 60000
                        return "Usage for $pkg on $date: $mins minutes."
                    } else {
                        // Top 5 apps
                        val sorted = usageMap.entries.sortedByDescending { it.value }.take(5)
                        val sb = StringBuilder("Top apps on $date:\n")
                        sorted.forEach { entry ->
                            val name = entry.key.substringAfterLast(".") // Simple name
                            val mins = entry.value / 60000
                            sb.append("- $name: $mins mins\n")
                        }
                        return sb.toString()
                    }
                }

                "get_blocked_status" -> {
                    val pkg = args.optString("package_name", "")
                    val prefs = SavedPreferencesLoader(context)
                    
                    val isStrict = prefs.isStrictModeEnabled()
                    val focusData = prefs.getFocusModeData()
                    val isFocus = focusData.endTime > System.currentTimeMillis()
                    
                    val sb = StringBuilder()
                    sb.append("System Status:\n")
                    sb.append("- Strict Mode: ${if (isStrict) "ON" else "OFF"}\n")
                    sb.append("- Focus Mode: ${if (isFocus) "ON (until ${java.time.Instant.ofEpochMilli(focusData.endTime)})" else "OFF"}\n")
                    
                    if (pkg.isNotEmpty()) {
                        // Check if specific app is blocked
                        var isBlocked = false
                        var reason = ""
                        
                        // 1. Strict Mode Global
                        if (isStrict) { 
                             // Assuming blocklist for now
                             val blockedApps = prefs.loadBlockedApps()
                             if (blockedApps.contains(pkg)) {
                                 isBlocked = true; reason += "Strict Blocklist, "
                             }
                        }
                        
                        // 2. Focus Mode
                        if (isFocus) {
                             val config = prefs.getBlockedAppConfig(pkg)
                             if (config.blockInFocus) {
                                  val selected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefs.getFocusModeSelectedApps())
                                  if (selected.contains(pkg)) {
                                      isBlocked = true; reason += "Focus Mode, "
                                  }
                             }
                        }
                        
                        sb.append("\nApp '$pkg' Status: ${if (isBlocked) "BLOCKED ($reason)" else "ALLOWED"}")
                    }
                    
                    return sb.toString()
                }
                
                "get_reminders" -> {
                    val prefs = SavedPreferencesLoader(context)
                    val reminders = prefs.loadCustomReminders()
                    if (reminders.isEmpty()) return "No active custom reminders."
                    
                    val sb = StringBuilder("Active Reminders:\n")
                    reminders.forEach { r ->
                        if (r.isEnabled) {
                            val timeStr = String.format("%02d:%02d", r.hour, r.minute)
                            sb.append("- ${r.title} @ $timeStr\n")
                        }
                    }
                    return sb.toString()
                }

                else -> "Tool not implemented: $name"
            }
        } catch (e: Exception) {
            "Tool Error: ${e.message}"
        }
    }
}
