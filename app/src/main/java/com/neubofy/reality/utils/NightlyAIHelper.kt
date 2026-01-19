package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
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
        daySummary: NightlyProtocolExecutor.DaySummary
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
    
    private fun buildPrompt(context: Context, userIntro: String, summary: NightlyProtocolExecutor.DaySummary): String {
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
        planContent: String
    ): String = withContext(Dispatchers.IO) {
        
        TerminalLogger.log("Nightly AI: Analyzing plan with model: $modelString")
        
        val providerAndKey = AISettingsActivity.getProviderAndKeyFromModel(context, modelString)
            ?: throw IllegalStateException("Invalid model configuration: $modelString")
        
        val (provider, apiKey) = providerAndKey
        val modelName = modelString.substringAfter(": ")
        
        // Load custom or default prompt
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("custom_plan_prompt", null)
        
        val systemPrompt = customPrompt?.replace("{plan_content}", planContent) 
            ?: getDefaultPlanPrompt(planContent)
            
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
    
    fun getDefaultPlanPrompt(planContent: String): String {
        return """
            You are a personal productivity assistant. 
            Analyze the following "Plan for Tomorrow" document and extract actionable items.
            
            PLAN CONTENT:
            $planContent
            
            Return a valid JSON object with two arrays: "tasks" and "events".
            
            "tasks": List of objects { "title": string, "notes": string (optional) }
            "events": List of objects { "title": string, "startTime": string (HH:mm), "endTime": string (HH:mm), "description": string (optional) }
            
            If times are missing for events, exclude them or make a best guess if context implies it (e.g. "Morning workout" -> 07:00-08:00). 
            If no specific items found, return empty arrays.
            OUTPUT JSON ONLY. NO MARKDOWN.
        """.trimIndent()
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
            put("max_tokens", 500)
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
                put("maxOutputTokens", 500)
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
  "xp": (integer 0-50, score for quality of reflection),
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
}
