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
     * @return List of 5 reflection questions
     */
    suspend fun generateQuestions(
        context: Context,
        modelString: String,
        userIntroduction: String,
        daySummary: DaySummary
    ): List<String> = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Generating questions with model: $modelString")
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration: $modelString")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        val prompt = buildPrompt(context, userIntroduction, daySummary)
        TerminalLogger.log("Nightly AI: Prompt built, calling $provider API...")
        
        val response = when (provider) {
            "OpenAI" -> callOpenAI(apiKey, modelName, prompt)
            "Gemini" -> callGemini(apiKey, modelName, prompt)
            "Groq" -> callGroq(apiKey, modelName, prompt)
            "OpenRouter" -> callOpenRouter(apiKey, modelName, prompt)
            "Perplexity" -> callPerplexity(apiKey, modelName, prompt)
            else -> throw IllegalStateException("Unsupported provider: $provider")
        }
        
        TerminalLogger.log("Nightly AI: Response received, parsing questions...")
        parseQuestions(response)
    }
    
    private fun buildPrompt(context: Context, userIntro: String, summary: DaySummary): String {
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
        
        // Try to get custom prompt from SharedPreferences
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_ai_prompt", null)
        
        val userIntroStr = if (userIntro.isNotEmpty()) "About the user:\n$userIntro" else ""
        
        return if (customPrompt != null) {
            // Use custom prompt with placeholder replacement
            customPrompt
                .replace("{user_intro}", userIntroStr)
                .replace("{date}", summary.date.format(dateFormatter))
                .replace("{calendar}", calendarList)
                .replace("{tasks_due}", tasksDueList)
                .replace("{tasks_completed}", tasksCompletedList)
                .replace("{sessions}", sessionsList)
                .replace("{stats}", statsStr)
        } else {
            // Use default prompt
            """You are a supportive but honest personal productivity coach.

$userIntroStr

Today is ${summary.date.format(dateFormatter)}.

📅 SCHEDULED CALENDAR EVENTS:
$calendarList

📋 TASKS DUE TODAY:
$tasksDueList

✅ TASKS COMPLETED TODAY:
$tasksCompletedList

⏱️ STUDY/WORK SESSIONS (Tapasya):
$sessionsList

📊 STATISTICS:
$statsStr

Based on this comprehensive data, generate EXACTLY 5 personalized reflection questions.

Guidelines:
1. If there's a gap between planned and actual, ask about what happened (gently but directly)
2. If they completed many tasks, acknowledge and ask what helped them succeed
3. If tasks are pending, ask about priorities and blockers
4. Ask about their emotional/mental state during work
5. Help them plan improvements for tomorrow

Be warm and supportive, but also honest. Don't sugarcoat if they underperformed.
If no plan was set, ask about setting intentions.
If no work was done, be compassionate but encourage reflection on barriers.

Return ONLY the 5 questions, numbered 1-5, one per line. No other text."""
        }
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
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration: $modelString")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        // Load custom or default prompt
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_plan_prompt", null)
        
        // Build List Context
        val listsContext = if (taskListConfigs.isNotEmpty()) {
            val details = taskListConfigs.joinToString("\n") { 
                "- List Name: \"${it.displayName}\" (ID: ${it.googleListId})\n  Description: ${it.description}" 
            }
            "\n[AVAILABLE TASK LISTS]\n$details\n"
        } else {
            ""
        }

        val systemPrompt = customPrompt?.replace("{plan_content}", planContent)
            ?.replace("{list_context}", listsContext)
            ?: getDefaultPlanPrompt(planContent, taskListConfigs)
            
        TerminalLogger.log("Nightly AI: Plan prompt built, calling $provider API...")
        
        val response = when (provider) {
            "OpenAI" -> callOpenAI(apiKey, modelName, systemPrompt)
            "Gemini" -> callGemini(apiKey, modelName, systemPrompt)
            "Groq" -> callGroq(apiKey, modelName, systemPrompt)
            "OpenRouter" -> callOpenRouter(apiKey, modelName, systemPrompt)
            "Perplexity" -> callPerplexity(apiKey, modelName, systemPrompt)
            else -> throw IllegalStateException("Unsupported provider: $provider")
        }
        
        response
    }
    
    fun getDefaultQuestionsPromptTemplate(): String {
        return """You are a supportive but honest personal productivity coach.

{user_intro}

Today is {date}.

📅 SCHEDULED CALENDAR EVENTS:
{calendar}

📋 TASKS DUE TODAY:
{tasks_due}

✅ TASKS COMPLETED TODAY:
{tasks_completed}

⏱️ STUDY/WORK SESSIONS (Tapasya):
{sessions}

📊 STATISTICS:
{stats}

Based on this comprehensive data, generate EXACTLY 5 personalized reflection questions.

Guidelines:
1. If there's a gap between planned and actual, ask about what happened (gently but directly)
2. If they completed many tasks, acknowledge and ask what helped them succeed
3. If tasks are pending, ask about priorities and blockers
4. Ask about their emotional/mental state during work
5. Help them plan improvements for tomorrow

Be warm and supportive, but also honest. Don't sugarcoat if they underperformed.
If no plan was set, ask about setting intentions.
If no work was done, be compassionate but encourage reflection on barriers.

Return ONLY the 5 questions, numbered 1-5, one per line. No other text."""
    }

    fun getDefaultAnalyzerPromptTemplate(): String {
        return """You are a wise and strict mentor reviewing a student's nightly reflection.
Your goal is to ensure they are taking the process seriously and actually reflecting, not just going through the motions.

{user_intro}

Analyze the following Nightly Reflection Diary:

[[DIARY_START]]
{diary_content}
[[DIARY_END]]

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
        return """You are a professional executive coach generating a daily progress report.

About the user: {user_intro}

DATE: {date}

[DATA SUMMARY]
- Efficiency: {efficiency} ({total_effective} / {total_planned})
- Tasks Completed: {tasks_done}
- XP Earned Today: {xp_earned}
- Current Level: {level}

[USER REFLECTION]
{reflection_content}

[TOMORROW'S Plan]
{plan_content}

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
        return """
            You are an advanced productivity extraction AI. 
            Analyze the following "Plan for Tomorrow" and extract actionable items with extreme precision.
            
            [PLAN CONTENT]
            {plan_content}
            
            {list_context}
            
            [STRICT EXTRACTION RULES]
            1. TASKS:
               - Extract ONLY actionable task mentions that are explicitly listed.
               - NEGATIVE CONSTRAINT: Do NOT infer or "hallucinate" tasks that are not clearly written (e.g. don't turn general advice into tasks).
               - TITLE: Clean title only.
               - CATEGORIZATION: Select the best "taskListId" from the [AVAILABLE TASK LISTS].
               - CRITICAL: You MUST use one of the "ID"s provided in the context. Do NOT use names like "inbox", "work", or "personal" unless they are explicitly listed with that ID.
               - IF NO MATCH: Use "@default".
               - Distribute tasks appropriately based on the list descriptions. 
               - DUE TIME: If a specific time is mentioned (e.g., "14:00 Finish report"), extract it as "startTime" in 24h HH:mm format. 
            
            2. CALENDAR EVENTS: 
               - ONLY extract productive/focused study or work sessions.
               - NEGATIVE CONSTRAINT: DO NOT extract Sleep, Travel, Commute, Relax, Eating, Gym, or Leisure activities as events.
               - TIME: Must have both "startTime" and "endTime" in HH:mm.
            
            4. WAKE UP TIME: (Constraint: AI Decision)
               - Based on the plan's first activity, determine the optimal "wakeupTime" (HH:mm).
               - If the plan starts at 06:00, wake up might be 05:30.
               - IMPORTANT: If no clear start time is found, return empty string "".
            
            5. MENTORSHIP (Short Advice):
               - Provide a 2-3 sentence punchy piece of advice for the user to succeed tomorrow.
               - Focus on mindset, energy, or specific focus from the plan.
            
            [JSON OUTPUT FORMAT]
            (CRITICAL: Output EXACTLY this JSON structure. NO MARKDOWN. NO PREAMBLE. NO OTHER TEXT)
            {
              "wakeupTime": "HH:mm or empty",
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
            }
        """.trimIndent()
    }

    fun getDefaultPlanPrompt(planContent: String, taskListConfigs: List<com.neubofy.reality.data.db.TaskListConfig> = emptyList()): String {
        val listsContext = if (taskListConfigs.isNotEmpty()) {
            val details = taskListConfigs.joinToString("\n") { 
                "- List Name: \"${it.displayName}\" (ID: ${it.googleListId})\n  Description: ${it.description}" 
            }
            "\n[AVAILABLE TASK LISTS]\n$details\n"
        } else {
            ""
        }

        return getDefaultPlanPromptTemplate()
            .replace("{plan_content}", planContent)
            .replace("{list_context}", listsContext)
    }

    private fun callOpenAI(apiKey: String, model: String, prompt: String): String {
        return callOpenAICompatible(
            "https://api.openai.com/v1/chat/completions",
            apiKey,
            model,
            prompt
        )
    }
    
    private fun callGroq(apiKey: String, model: String, prompt: String): String {
        return callOpenAICompatible(
            "https://api.groq.com/openai/v1/chat/completions",
            apiKey,
            model,
            prompt
        )
    }
    
    private fun callOpenRouter(apiKey: String, model: String, prompt: String): String {
        return callOpenAICompatible(
            "https://openrouter.ai/api/v1/chat/completions",
            apiKey,
            model,
            prompt
        )
    }
    
    private fun callPerplexity(apiKey: String, model: String, prompt: String): String {
        return callOpenAICompatible(
            "https://api.perplexity.ai/chat/completions",
            apiKey,
            model,
            prompt
        )
    }
    
    private fun callOpenAICompatible(endpoint: String, apiKey: String, model: String, prompt: String): String {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 8192)
        }
        
        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        
        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("API error ${conn.responseCode}: $error")
        }
        
        val response = conn.inputStream.bufferedReader().readText()
        val jsonResponse = JSONObject(response)
        
        return jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private fun callGemini(apiKey: String, model: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 8192)
            })
        }
        
        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        
        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("Gemini API error ${conn.responseCode}: $error")
        }
        
        val response = conn.inputStream.bufferedReader().readText()
        val jsonResponse = JSONObject(response)
        
        return jsonResponse
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
    
    data class AnalysisResult(
        val xp: Int,
        val satisfied: Boolean,
        val feedback: String
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
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        val prompt = buildAnalysisPrompt(context, userIntroduction, diaryContent)
        
        val response = when (provider) {
            "OpenAI" -> callOpenAI(apiKey, modelName, prompt)
            "Gemini" -> callGemini(apiKey, modelName, prompt)
            "Groq" -> callGroq(apiKey, modelName, prompt)
            "OpenRouter" -> callOpenRouter(apiKey, modelName, prompt)
            "Perplexity" -> callPerplexity(apiKey, modelName, prompt)
            else -> throw IllegalStateException("Unsupported provider: $provider")
        }
        
        parseAnalysisResponse(response)
    }

    private fun buildAnalysisPrompt(context: Context, userIntro: String, diaryContent: String): String {
        // Try to get custom analyzer prompt
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_analyzer_prompt", null)
        
        val userIntroStr = if (userIntro.isNotEmpty()) "About the user:\n$userIntro" else ""
        
        val defaultSystemPrompt = """You are a wise and strict mentor reviewing a student's nightly reflection.
Your goal is to ensure they are taking the process seriously and actually reflecting, not just going through the motions.

$userIntroStr

Analyze the following Nightly Reflection Diary:

[[DIARY_START]]
$diaryContent
[[DIARY_END]]

EVALUATION CRITERIA:
1. DEPTH: Did they answer the questions with thought? (One word answers = Fail)
2. HONESTY: Does it seem genuine?
3. COMPLETENESS: Did they complete the reflection?

OUTPUT REQUIREMENTS:
You must output a single JSON object. Do not include markdown formatting like ```json.
{
  "xp": (integer 0-500, score for quality of reflection),
  "satisfied": (boolean, true if reflection is good enough to accept, false if lazy/incomplete),
  "feedback": (string, 1-2 sentence feedback. If satisfied, praise insight. If false, explain why and ask them to add more.)
}"""

        return if (customPrompt != null) {
            customPrompt.replace("{user_intro}", userIntroStr)
                .replace("{diary_content}", diaryContent)
        } else {
            defaultSystemPrompt
        }
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
                feedback = json.optString("feedback", "Good reflection.")
            )
        } catch (e: Exception) {
            TerminalLogger.log("Nightly AI: Error parsing analysis JSON - ${e.message}")
            // Fallback to accepted if parsing fails, to avoid blocking user
            return AnalysisResult(20, true, "Reflection recorded.")
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
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        val prompt = buildPlanPrompt(context, userIntro, summary)
        
        val response = when (provider) {
            "OpenAI" -> callOpenAI(apiKey, modelName, prompt)
            "Gemini" -> callGemini(apiKey, modelName, prompt)
            "Groq" -> callGroq(apiKey, modelName, prompt)
            "OpenRouter" -> callOpenRouter(apiKey, modelName, prompt)
            "Perplexity" -> callPerplexity(apiKey, modelName, prompt)
            else -> throw IllegalStateException("Unsupported provider: $provider")
        }
        
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
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        // Load custom or default prompt
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_report_prompt", null)
        
        val prompt = if (customPrompt != null) {
            // Replace placeholders in custom prompt
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
            val totalPlanned = summary.totalPlannedMinutes
            val totalEffective = summary.totalEffectiveMinutes
            val efficiency = if (totalPlanned > 0) (totalEffective * 100 / totalPlanned) else 0
            
            customPrompt
                .replace("{user_intro}", userIntro)
                .replace("{date}", summary.date.format(dateFormatter))
                .replace("{efficiency}", "$efficiency%")
                .replace("{total_effective}", "$totalEffective mins")
                .replace("{total_planned}", "$totalPlanned mins")
                .replace("{tasks_done}", "${summary.tasksCompleted.size}")
                .replace("{xp_earned}", "${xpStats?.totalDailyXP ?: 0}")
                .replace("{level}", "${xpStats?.level ?: 1}")
                .replace("{reflection_content}", reflectionContent.take(8000))
                .replace("{plan_content}", planContent.take(8000))
        } else {
            buildReportPrompt(context, userIntro, summary, xpStats, reflectionContent, planContent)
        }
        
        val response = when (provider) {
            "OpenAI" -> callOpenAI(apiKey, modelName, prompt)
            "Gemini" -> callGemini(apiKey, modelName, prompt)
            "Groq" -> callGroq(apiKey, modelName, prompt)
            "OpenRouter" -> callOpenRouter(apiKey, modelName, prompt)
            "Perplexity" -> callPerplexity(apiKey, modelName, prompt)
            else -> throw IllegalStateException("Unsupported provider: $provider")
        }
        
        response.trim()
    }

    private fun buildReportPrompt(
        context: Context, 
        userIntro: String, 
        summary: DaySummary,
        xpStats: com.neubofy.reality.utils.XPManager.XPBreakdown?,
        reflectionContent: String,
        planContent: String
    ): String {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        
        // Extract stats
        val totalPlanned = summary.totalPlannedMinutes
        val totalEffective = summary.totalEffectiveMinutes
        val efficiency = if (totalPlanned > 0) (totalEffective * 100 / totalPlanned) else 0
        val tasksDone = summary.tasksCompleted.size
        
        val xpEarned = xpStats?.totalDailyXP ?: 0
        val level = xpStats?.level ?: 1
        
        val userIntroStr = if (userIntro.isNotEmpty()) "About the user: $userIntro" else ""
        
        // Structured Metadata as JSON-like block for AI
        val metadata = """
        {
          "date": "${summary.date}",
          "stats": {
            "efficiency_percent": $efficiency,
            "effective_minutes": $totalEffective,
            "planned_minutes": $totalPlanned,
            "tasks_completed": $tasksDone,
            "xp_earned": $xpEarned,
            "current_level": $level
          },
          "user_context": "${userIntro.replace("\"", "\\\"")}"
        }
        """.trimIndent()
        
        return """
            You are a professional executive coach and performance analyst.
            
            [SYSTEM METADATA]
            $metadata
            
            [PRIMARY SOURCE: DIARY DOCUMENT]
            The following is the full text extracted from the user's diary document (including any tables or lists). 
            Analyze this deeply:
            ---
            $reflectionContent
            ---
            
            [PRIMARY SOURCE: PLAN FOR TOMORROW]
            The following is the text extracted from the user's planning document for tomorrow:
            ---
            $planContent
            ---
            
            INSTRUCTIONS:
            Perform a deep analysis of the user's day based on the provided JSON metadata and the full text of their Diary and Plan.
            1. **Daily Briefing**: Provide a concise summary of the day's achievements and challenges.
            2. **Deep Insights**: Analyze the user's reflection. Identify patterns, wins, or recurring blockers.
            3. **Plan Critique**: Analyze the plan for tomorrow. Evaluate its feasibility based on today's metrics ($efficiency% efficiency).
            4. **Level Up**: Provide a motivational comment regarding their current level ($level) and today's XP ($xpEarned).
            5. **Final Verdict**: Assign a "Theme of the Day" and a one-sentence "Coach's Directive".

            OUTPUT FORMAT:
            - Use clean, professional Markdown.
            - Do NOT include any meta-talk like "As an AI..." or "Here is your analysis...".
            - Be direct, insightful, and encouraging.
            - **Length Constraint**: Ensure the entire report is comprehensive but remains under 7,000 characters.
        """.trimIndent()
    }
}
