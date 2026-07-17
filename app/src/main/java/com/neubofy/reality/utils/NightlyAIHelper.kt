package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.ui.activity.AISettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter

/**
 * AI helper for generating personalized nightly reflection questions.
 */
object NightlyAIHelper {
    
    /**
     * Generate personalized reflection questions based on user's day.
     * 
     * @param context Android context
     * @param modelString Full model string in format "Provider: model-name"
     * @param userIntroduction User's self-introduction for personalization
     * @param daySummary Summary of the day's activities
     * @param healthData Health and screen time data
     * @param previousReport Previous day's AI-generated report for context
     * @return List of 5 reflection questions
     */
    suspend fun generateQuestions(
        context: Context,
        modelString: String,
        userIntroduction: String,
        daySummary: DaySummary,
        healthData: String,
        previousReport: String = "No previous day report available."
    ): List<String> = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Generating questions with model: $modelString")
        
        val (systemPrompt, userPrompt) = buildSeparatedPrompts(context, userIntroduction, daySummary, healthData, previousReport)
        TerminalLogger.log("Nightly AI: Prompts built (system + user), calling AI Worker...")

        val response = callAIWorker(context, userPrompt, systemPrompt, modelString)
        
        TerminalLogger.log("Nightly AI: Response received, parsing questions...")
        parseQuestions(response)
    }
    
    /**
     * Build separated system prompt and user prompt for question generation.
     * System prompt = role + instructions (editable by user)
     * User prompt = actual day data (dynamic, injected at runtime)
     */
    /**
     * Build separated system prompt and user prompt for question generation.
     * System prompt = template with placeholder tags replaced by references to the user message
     * User prompt = actual dynamic day data under labeled sections
     */
    private fun buildSeparatedPrompts(context: Context, userIntro: String, summary: DaySummary, healthData: String, previousReport: String): Pair<String, String> {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        
        // Calendar events from device
        val calendarList = if (summary.calendarEvents.isEmpty()) {
            "No calendar events scheduled"
        } else {
            summary.calendarEvents.joinToString("\n") { event ->
                val startTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(event.startTime))
                val duration = (event.endTime - event.startTime) / 60000
                "- $startTime: ${event.title} (${duration} min)"
            }
        }
        
        // Google Tasks
        val tasksDueList = if (summary.tasksDue.isEmpty()) {
            "No tasks due"
        } else {
            summary.tasksDue.joinToString("\n") { "- $it" }
        }
        
        val tasksCompletedList = if (summary.tasksCompleted.isEmpty()) {
            "No tasks completed"
        } else {
            summary.tasksCompleted.joinToString("\n") { "- ✓ $it" }
        }
        
        // Tapasya Sessions
        val sessionsList = if (summary.completedSessions.isEmpty()) {
            "No focused work sessions completed"
        } else {
            summary.completedSessions.joinToString("\n") { session ->
                val effectiveMins = session.effectiveTimeMs / 60000
                "- ${session.name}: ${effectiveMins} minutes of effective work"
            }
        }
        
        val efficiency = if (summary.totalPlannedMinutes > 0) {
            (summary.totalEffectiveMinutes * 100 / summary.totalPlannedMinutes)
        } else if (summary.totalEffectiveMinutes > 0) {
            100 // Did work without a plan
        } else {
            0
        }
        
        // Build stats string
        val statsStr = """- Total Planned Calendar Time: ${summary.totalPlannedMinutes} minutes
- Total Effective Study Time: ${summary.totalEffectiveMinutes} minutes  
- Tasks: ${summary.tasksCompleted.size} completed, ${summary.tasksDue.size} pending
- Efficiency: $efficiency%"""
        
        // --- Build SYSTEM PROMPT (instructions) ---
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_ai_prompt", null)
        val template = customPrompt ?: getDefaultQuestionsPromptTemplate()
        
        val systemPrompt = template
            .replace("{user_intro}", "[See USER INTRODUCTION in user message]")
            .replace("{date}", "[See CURRENT DATE in user message]")
            .replace("{calendar}", "[See SCHEDULED CALENDAR EVENTS in user message]")
            .replace("{tasks_due}", "[See TASKS DUE TODAY in user message]")
            .replace("{tasks_completed}", "[See TASKS COMPLETED TODAY in user message]")
            .replace("{sessions}", "[See STUDY/WORK SESSIONS in user message]")
            .replace("{health}", "[See DIGITAL WELLBEING in user message]")
            .replace("{stats}", "[See PERFORMANCE STATISTICS in user message]")
            .replace("{report}", "[See PREVIOUS DAY AI REPORT in user message]")

        // --- Build USER PROMPT (actual dynamic day data) ---
        val userPrompt = """Here is my complete day data for today. Analyze it and generate my 5 reflection questions.

[USER INTRODUCTION]
$userIntro

[CURRENT DATE]
${summary.date.format(dateFormatter)}

[SCHEDULED CALENDAR EVENTS]
$calendarList

[TASKS DUE TODAY]
$tasksDueList

[TASKS COMPLETED TODAY]
$tasksCompletedList

[STUDY/WORK SESSIONS]
$sessionsList

[DIGITAL WELLBEING]
$healthData

[PERFORMANCE STATISTICS]
$statsStr

[PREVIOUS DAY AI REPORT]
$previousReport"""

        return Pair(systemPrompt, userPrompt)
    }
    
    /**
     * Analyze a planning document to extract tasks and events.
     */
    suspend fun analyzePlan(
        context: Context,
        modelString: String,
        planContent: String,
        taskListConfigs: List<com.neubofy.reality.data.db.TaskListConfig> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Analyzing plan with model: $modelString")
        
        val cleanPlanContent = planContent.trim()

        // Load custom or default prompt template
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_plan_prompt", null)
        val template = customPrompt ?: getDefaultPlanPromptTemplate()
        
        val listsContext = if (taskListConfigs.isNotEmpty()) {
            taskListConfigs.joinToString("\n") { 
                "Task List Goal: \"${it.description}\" -> USE ID: ${it.googleListId}" 
            }
        } else {
            "No task lists available. Use default list ID: @default"
        }

        var systemPrompt = template
        if (systemPrompt.contains("{list_context}")) {
            systemPrompt = systemPrompt.replace("{list_context}", listsContext)
        }
        if (systemPrompt.contains("{plan_content}")) {
            systemPrompt = systemPrompt.replace("{plan_content}", cleanPlanContent)
        }

        val userMessage = """Please extract the tasks and events from my plan document.

[AVAILABLE TASK LISTS]
$listsContext

[PLAN CONTENT]
$cleanPlanContent"""

        TerminalLogger.log("Nightly AI: Plan prompt built, calling AI Worker...")
        val response = callAIWorker(context, userMessage, systemPrompt, modelString)
        TerminalLogger.log("Nightly AI Response: ${response.take(100)}...")
        
        response
    }
    


    fun getDefaultQuestionsPromptTemplate(): String {
        return """You are a supportive but honest personal productivity coach.
Analyze the user's day data provided in the user message, and generate EXACTLY 5 personalized reflection questions to help them reflect on their day.

Guidelines:
1. If there's a gap between planned and actual, ask about what happened (gently but directly).
2. If they completed many tasks, acknowledge and ask what helped them succeed.
3. If tasks are pending, ask about priorities and blockers.
4. Reference patterns from the previous day's report if available.
5. Help them plan improvements for tomorrow.

Be warm and supportive, but also honest. Don't sugarcoat if they underperformed.
If no plan was set, ask about setting intentions.
If no work was done, be compassionate but encourage reflection on barriers.

Return ONLY the 5 questions, numbered 1-5, one per line. No other text."""
    }

    fun getDefaultAnalyzerPromptTemplate(): String {
        return """You are a wise and strict mentor reviewing a student's nightly reflection diary.
Your goal is to ensure they are taking the process seriously and actually reflecting, not just going through the motions.

EVALUATION CRITERIA:
1. DEPTH: Did they answer the questions with thought? (One word answers = Fail)
2. HONESTY: Does it seem genuine?
3. COMPLETENESS: Did they complete the reflection?

OUTPUT REQUIREMENTS:
You must output a single JSON object. Do not include markdown formatting like ```json.
{
  "xp": (integer 0-50, score for quality of reflection),
  "satisfied": (boolean, true if reflection is good enough to accept, false if lazy/incomplete),
  "feedback": (string, 1-2 sentence feedback. If satisfied, praise insight. If false, explain why and ask them to add more.)
}"""
    }

    fun getDefaultReportPromptTemplate(): String {
        return """You are a professional executive coach generating a daily progress report based on the user's statistics, reflection diary, and plan for tomorrow.

INSTRUCTIONS:
1. Summarize the day's achievements.
2. Provide feedback on their reflection.
3. Critique their plan for tomorrow.
4. Give a final "Coach's Directive".

OUTPUT FORMAT:
- Clean, professional Markdown.
- Concise and actionable."""
    }

    /**
     * Get the default prompt template for plan extraction.
     * Use {plan_content} and {list_context} as placeholders.
     */
    fun getDefaultPlanPromptTemplate(): String {
        return """You are an advanced productivity extraction AI. 
Analyze the user's "Plan for Tomorrow" and extract actionable items with extreme precision.

[STRICT EXTRACTION RULES]
1. TASKS:
   - Extract ONLY actionable task mentions that are explicitly listed.
   - NEGATIVE CONSTRAINT: Do NOT infer or "hallucinate" tasks that are not clearly written.
   - TITLE: Clean title only.
   - CATEGORIZATION: Map each task to the most relevant "taskListId" from the [AVAILABLE TASK LISTS].
   - CRITICAL: You MUST use one of the "ID"s provided (e.g. "MTIzNDU..."). Do NOT use names or labels.
   - SELECTION LOGIC: If a task matches a specific list description, use that ID. Only use "@default" if NO other list matches.
   - DISTRIBUTE tasks wisely across specialized lists.
   - DUE TIME: If a specific time is mentioned (e.g., "14:00 Finish report"), extract it as "startTime" in STRICT 24-hour HH:mm format. 

2. CALENDAR EVENTS: 
   - ONLY extract productive/focused study or work sessions.
   - NEGATIVE CONSTRAINT: DO NOT extract Sleep, Travel, Commute, Relax, Eating, Gym, or Leisure activities as events.
   - TIME: Must have both "startTime" and "endTime" in STRICT 24-hour HH:mm format.

4. WAKE UP TIME: (Constraint: AI Decision)
   - Based on the plan's first activity, determine the optimal "wakeupTime" (STRICT 24-hour HH:mm).
   - If the plan starts at 06:00, wake up might be 05:30.
   - IMPORTANT: If no clear start time is found, return empty string "".
 
 5. SLEEP START TIME: (Constraint: AI Decision)
    - Determine the planned "sleepStartTime" (STRICT 24-hour HH:mm).
    - Look for "Sleep", "Bed", "Wind down" at the end of the plan.
    - If not explicitly stated, infer a reasonable time (e.g. 23:00) based on the day's intensity.
 
 6. MENTORSHIP (Short Advice):
   - Provide a 2-3 sentence punchy piece of advice for the user to succeed tomorrow.
   - Focus on mindset, energy, or specific focus from the plan.
 
 7. DISTRACTION TIME: (AI Decision based on day intensity)
   - Determine a reasonable "distractionTimeMinutes" (integer, 0-120) for allowed non-productive app usage.
   - LOGIC: Heavy work days with many tasks = lower distraction time (30-45 min).
   - Light days or rest days = higher allowed distraction (60-90 min).
   - If explicitly mentioned in plan (e.g. "1 hour break for social media"), use that value.
   - Default to 60 if unclear.

[JSON OUTPUT FORMAT]
(CRITICAL: Output EXACTLY this JSON structure. NO MARKDOWN. NO PREAMBLE. NO OTHER TEXT)
{
  "wakeupTime": "HH:mm or empty",
  "sleepStartTime": "HH:mm or empty",
  "distractionTimeMinutes": 60,
  "mentorship": "Your advice string here",
  "tasks": [
    {
      "title": "Clean task title",
      "startTime": "HH:mm or null",
      "taskListId": "EXACT_ID_FROM_CONTEXT",
      "notes": "Details"
    }
  ],
  "events": [
    {
      "title": "Productive Session Title",
      "startTime": "HH:mm",
      "endTime": "HH:mm",
      "description": "Details"
    }
  ]
}"""
    }

    fun getDefaultPlanPrompt(taskListConfigs: List<com.neubofy.reality.data.db.TaskListConfig> = emptyList()): String {
        val listsContext = if (taskListConfigs.isNotEmpty()) {
            val details = taskListConfigs.joinToString("\n") { 
                "- List Name: \"${it.displayName}\" (ID: ${it.googleListId})\n  Description: ${it.description}" 
            }
            "\n[AVAILABLE TASK LISTS]\n$details\n"
        } else {
            ""
        }

        return getDefaultPlanPromptTemplate()
            .replace("{list_context}", listsContext)
    }

    private fun callAIWorker(context: Context, prompt: String, sysPrompt: String? = null, modelString: String? = null): String {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
        val modelToUse = modelString ?: prefs.getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"
        val meshKey = prefs.getString("mesh_api_key", "") ?: ""

        val isMeshModel = !modelToUse.startsWith("@cf/")
        if (isMeshModel && meshKey.isEmpty()) {
            throw Exception("Mesh API Key is missing. Please add it in settings to use $modelToUse.")
        }

        val apiUrl = if (isMeshModel) {
            "https://api.meshapi.ai/v1/chat/completions"
        } else {
            com.neubofy.reality.BuildConfig.AI_URL.removeSuffix("/")
        }

        if (apiUrl.isBlank()) throw Exception("AI endpoint is not configured (AI_URL is missing in build).")

        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        val connectionSecret = com.neubofy.reality.utils.IdentityManager.getConnectionSecret(context)

        val url = java.net.URL(apiUrl)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (isMeshModel) {
            conn.setRequestProperty("Authorization", "Bearer $meshKey")
        }
        conn.doOutput = true
        
                val requestBody = JSONObject().apply {
            if (!isMeshModel) {
                put("userId", userId)
                put("connectionSecret", connectionSecret)
                put("activeExpiry", com.neubofy.reality.utils.IdentityManager.getActiveExpiry(context))
                put("activeDuration", com.neubofy.reality.utils.IdentityManager.getActiveDuration(context))
                put("activeStatus", com.neubofy.reality.utils.IdentityManager.getActiveStatus(context))
                put("planType", com.neubofy.reality.utils.IdentityManager.getActivePlanType(context))
                put("requestCount", com.neubofy.reality.utils.IdentityManager.getAndIncrementDailyAICount(context))
            }
            put("model", modelToUse)
            put("messages", JSONArray().apply {
                if (sysPrompt != null) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", sysPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 4096)
        }
        
        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        
        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("API error ${conn.responseCode}: $error")
        }
        
        val response = conn.inputStream.bufferedReader().readText()
        var jsonResponse = JSONObject(response)
        
        if (jsonResponse.has("result")) {
            val resultObj = jsonResponse.optJSONObject("result")
            if (resultObj != null) {
                jsonResponse = resultObj
            }
        }
        
        return if (jsonResponse.has("response")) {
            jsonResponse.getString("response")
        } else if (jsonResponse.has("choices")) {
            jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } else {
            val rawResponse = jsonResponse.toString()
            if (rawResponse.startsWith("\"") && rawResponse.endsWith("\"")) {
                try {
                    org.json.JSONTokener(rawResponse).nextValue() as? String ?: rawResponse
                } catch (_: Exception) { rawResponse }
            } else {
                rawResponse
            }
        }
    }
    
    data class AnalysisResult(
        val xp: Int,
        val satisfied: Boolean,
        val feedback: String,
        val rawJson: String? = null
    )

    /**
     * Analyze the user's reflection diary.
     */
    suspend fun analyzeReflection(
        context: Context,
        modelString: String,
        userIntroduction: String,
        diaryContent: String
    ): AnalysisResult = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Analyzing reflection with model: $modelString")
        
        val systemPromptStr = buildAnalysisPrompt(context)
        val userMessage = """Please evaluate my reflection.

[USER INTRODUCTION]
$userIntroduction

[DIARY CONTENT]
$diaryContent"""
        val response = callAIWorker(context, userMessage, systemPromptStr, modelString)
        
        parseAnalysisResponse(response)
    }

    private fun buildAnalysisPrompt(context: Context): String {
        // Try to get custom analyzer prompt
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_analyzer_prompt", null)
        val template = customPrompt ?: getDefaultAnalyzerPromptTemplate()
        
        return template
            .replace("{user_intro}", "[See USER INTRODUCTION in user message]")
            .replace("{diary_content}", "[See DIARY CONTENT in user message]")
    }

    private fun parseAnalysisResponse(response: String): AnalysisResult {
        try {
            // Clean markdown if present
            val jsonStr = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val json = JSONObject(jsonStr)
            return AnalysisResult(
                xp = json.optInt("xp", 10),
                satisfied = json.optBoolean("satisfied", true),
                feedback = json.optString("feedback", "Good reflection."),
                rawJson = jsonStr
            )
        } catch (e: Exception) {
            TerminalLogger.log("Nightly AI: Error parsing analysis JSON - ${e.message}")
            // Fallback to accepted if parsing fails, to avoid blocking user
            return AnalysisResult(20, true, "Reflection recorded.", null)
        }
    }

    private fun parseQuestions(aiResponse: String): List<String> {
        val questions = mutableListOf<String>()
        
        // Try to extract numbered questions (1. question, 2. question, etc.)
        val lines = aiResponse.trim().lines()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // Match patterns like "1.", "1)", "1:", or just starts with number
            val questionMatch = Regex("^\\d+[.):]?\\s*(.+)").find(trimmed)
            if (questionMatch != null) {
                val question = questionMatch.groupValues[1].trim()
                if (question.isNotEmpty()) {
                    questions.add(question)
                }
            } else if (trimmed.contains("?") && questions.size < 5) {
                // Fallback: if line contains a question mark
                questions.add(trimmed)
            }
        }
        
        // Ensure we return at most 5 questions
        return questions.take(5)
    }

    suspend fun generatePlanSuggestions(
        context: Context,
        modelString: String,
        userIntro: String,
        summary: DaySummary
    ): String = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Generating plan suggestions...")
        
        val systemPromptStr = buildPlanPrompt(context, userIntro, summary)
        
        // Separate user data into user message
        val pendingTasks = if (summary.tasksDue.isEmpty()) {
            "No overdue tasks."
        } else {
            summary.tasksDue.joinToString("\n") { "- $it" }
        }
        val userMessage = """Based on my current situation, suggest a high-level plan for tomorrow. Output clean markdown.

My pending/overdue tasks:
$pendingTasks

Today's efficiency: ${if (summary.totalPlannedMinutes > 0) (summary.totalEffectiveMinutes * 100 / summary.totalPlannedMinutes) else 0}%
Effective study time: ${summary.totalEffectiveMinutes} minutes
Tasks completed today: ${summary.tasksCompleted.size}"""
        
        val response = callAIWorker(context, userMessage, systemPromptStr, modelString)
        
        // Return raw response (Markdown expected)
        response.trim()
    }
    
    private fun buildPlanPrompt(context: Context, userIntro: String, summary: DaySummary): String {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        val tomorrowDate = summary.date.plusDays(1).format(dateFormatter)
        
        // Pending Tasks
        val pendingTasks = if (summary.tasksDue.isEmpty()) {
            "No overdue tasks."
        } else {
            summary.tasksDue.joinToString("\n") { "- $it" }
        }
        
        val userIntroStr = if (userIntro.isNotEmpty()) "About the user:\n$userIntro" else ""
        
        return """
            You are an expert productivity planner.
            $userIntroStr
            
            Based on the user's pending tasks and context, suggest a realistic High-Level Plan for TOMORROW ($tomorrowDate).
            
            PENDING TASKS (Carry over?):
            $pendingTasks
            
            INSTRUCTIONS:
            1. Suggest 3 Top Priorities for tomorrow.
            2. Suggest a rough time-blocked schedule (Morning/Afternoon/Evening).
            3. Include any specific advice for maintaining momentum or recovering if today was slow.
            
            FORMAT:
            Use clean Markdown.
            
            ### Top Priorities
            1. ...
            2. ...
            3. ...
            
            ### Suggested Schedule
            - **Morning**: ...
            - **Afternoon**: ...
            
            ### Advice
            ...
        """.trimIndent()
    }
    
    suspend fun generateReportSummary(
        context: Context,
        modelString: String,
        userIntro: String,
        summary: DaySummary,
        xpStats: com.neubofy.reality.utils.XPManager.XPBreakdown?,
        reflectionContent: String,
        planContent: String
    ): String = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Generating report summary...")
        
        // Load custom or default prompt template
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_report_prompt", null)
        val template = customPrompt ?: getDefaultReportPromptTemplate()
        
        val systemPrompt = template
            .replace("{user_intro}", "[See USER INTRODUCTION in user message]")
            .replace("{date}", "[See REPORT DATE in user message]")
            .replace("{efficiency}", "[See EFFICIENCY in user message]")
            .replace("{total_effective}", "[See TOTAL EFFECTIVE MINUTES in user message]")
            .replace("{total_planned}", "[See TOTAL PLANNED MINUTES in user message]")
            .replace("{tasks_done}", "[See TASKS COMPLETED COUNT in user message]")
            .replace("{xp_earned}", "[See XP EARNED in user message]")
            .replace("{level}", "[See USER LEVEL in user message]")
            .replace("{reflection_content}", "[See USER DIARY REFLECTION in user message]")
            .replace("{plan_content}", "[See TOMORROW'S PLAN CONTENT in user message]")

        // User prompt contains all dynamic day data
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        val totalPlanned = summary.totalPlannedMinutes
        val totalEffective = summary.totalEffectiveMinutes
        val efficiency = if (totalPlanned > 0) (totalEffective * 100 / totalPlanned) else 0
        
        val userMessage = """Please generate my daily report summary based on the following dynamic data:

[USER INTRODUCTION]
$userIntro

[REPORT DATE]
${summary.date.format(dateFormatter)}

[EFFICIENCY]
$efficiency%

[TOTAL EFFECTIVE MINUTES]
$totalEffective mins

[TOTAL PLANNED MINUTES]
$totalPlanned mins

[TASKS COMPLETED COUNT]
${summary.tasksCompleted.size}

[XP EARNED]
${xpStats?.totalDailyXP ?: 0}

[USER LEVEL]
${xpStats?.level ?: 1}

[USER DIARY REFLECTION]
$reflectionContent

[TOMORROW'S PLAN CONTENT]
$planContent"""

        val response = callAIWorker(context, userMessage, systemPrompt, modelString)
        response.trim()
    }
}

