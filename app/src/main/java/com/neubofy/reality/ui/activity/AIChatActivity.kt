package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.ChatDao
import com.neubofy.reality.data.db.ChatMessageEntity
import com.neubofy.reality.data.db.ChatSession
import com.neubofy.reality.data.model.ChatMessage
import com.neubofy.reality.databinding.ActivityAiChatBinding
import com.neubofy.reality.ui.adapter.ChatAdapter
import com.neubofy.reality.ui.adapter.ChatSessionAdapter
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import com.neubofy.reality.utils.ConversationMemoryManager

open class AIChatActivity : BaseActivity() {

    protected lateinit var binding: ActivityAiChatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var sessionAdapter: ChatSessionAdapter
    
    // Database
    private lateinit var db: AppDatabase
    private lateinit var chatDao: ChatDao
    private var currentSessionId: Long? = null
    
    // State
    private var isProMode = false
    private var hasTriggeredVoiceAuto = false
    private var isGenerating = false
    private var currentGenerationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            binding.toolbar.setPadding(0, bars.top, 0, 0)
            
            val padding8dp = (8 * resources.displayMetrics.density).toInt()
            binding.inputArea.setPadding(padding8dp, padding8dp, padding8dp, bars.bottom + padding8dp)
            insets
        }
        
        db = AppDatabase.getDatabase(this)
        chatDao = db.chatDao()

        setupUI()
        setupDrawer()
        
        // Load configurations
        refreshModels()
        loadSessions()
        
        // Check for Pro Mode intent (e.g. from Widget)
        if (intent.getStringExtra("extra_mode") == "pro") {
            isProMode = true
            val colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)
            binding.btnMode.imageTintList = ColorStateList.valueOf(colorPrimary)
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshModels()
        
        // Check for voice auto-trigger (from Widget with setting enabled)
        if (!hasTriggeredVoiceAuto && intent.getBooleanExtra("voice_auto", false)) {
            hasTriggeredVoiceAuto = true
            // Delay slightly to let UI initialize, then trigger voice input
            binding.root.postDelayed({
                binding.btnVoice.performClick()
            }, 300)
        }

        // Check for Keyboard Focus (Search Widget)
        if (intent.getBooleanExtra("extra_focus_keyboard", false)) {
            // Clear the extra so it doesn't trigger on rotation
            intent.removeExtra("extra_focus_keyboard")
            
            binding.root.postDelayed({
                binding.etMessage.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    private fun setupUI() {
        // Fetch User Name
        val mainPrefs = getSharedPreferences("MainPrefs", Context.MODE_PRIVATE)
        val userName = mainPrefs.getString("user_name", "User") ?: "User"

        // Chat Adapter
        adapter = ChatAdapter(messages, userName)
        binding.recyclerChat.adapter = adapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        // Toolbar Actions
        binding.btnMenu.setOnClickListener {
             binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        
        binding.btnNewChat.setOnClickListener {
             createNewSession()
        }
        
        // Settings Button -> Open AI Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, AISettingsActivity::class.java))
        }
        
        // Voice Input
        val voiceLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val matches = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    binding.etMessage.setText(text)
                    // Optional: Auto-send if desired. User asked for "Voice Mode support", manual send allows correction.
                    // But usually voice assistants auto-send. Let's auto-send if not empty.
                    if (text.isNotBlank()) {
                         sendMessage(text)
                         binding.etMessage.text?.clear()
                    }
                }
            }
        }
        
        binding.btnVoice.setOnClickListener {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to Reality...")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice input not supported on this device.", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Send / Stop Button
        binding.btnSend.setOnClickListener {
            if (isGenerating) {
                // STOP LOGIC
                currentGenerationJob?.cancel()
                currentGenerationJob = null
                isGenerating = false
                
                // UI Feedback
                binding.tvThinking.text = "Stopped by user."
                adapter.addMessage(ChatMessage("⛔ Stopped by user.", false, false))
                saveBotMessage("Stopped by user.")
                
                // Reset UI
                binding.btnSend.setImageResource(R.drawable.baseline_send_24)
                binding.tvThinking.visibility = View.GONE
            } else {
                // SEND LOGIC
                val text = binding.etMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    isGenerating = true // Set flag immediately
                    binding.btnSend.setImageResource(R.drawable.baseline_close_24) 
                    
                    sendMessage(text)
                    binding.etMessage.text?.clear()
                }
            }
        }

        // Mode Toggle (Single Button)
        binding.btnMode.setOnClickListener {
            isProMode = !isProMode
            val context = this
            val colorOnSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY)
            val colorPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)

            if (isProMode) {
                binding.btnMode.imageTintList = ColorStateList.valueOf(colorPrimary)
                Toast.makeText(this, "Pro Mode Activated (Agentic Tools)", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnMode.imageTintList = ColorStateList.valueOf(colorOnSurfaceVariant)
                Toast.makeText(this, "Normal Mode Activated", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateSendButtonState(generating: Boolean) {
        isGenerating = generating
        val iconRes = if (generating) R.drawable.baseline_close_24 else R.drawable.baseline_send_24
        binding.btnSend.setImageResource(iconRes)
    }
    
    // Model selection logic removed (using default preference)
    private fun refreshModels() {
        // No-op or remove entirely if unused
    }

    private fun sendMessage(text: String) {
        adapter.addMessage(ChatMessage(text, true))
        binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
        
        handleSessionInit(text)

        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val savedModelString = prefs.getString("model", "OpenAI: gpt-3.5-turbo") ?: "OpenAI: gpt-3.5-turbo"
        
        val (provider, model) = if (savedModelString.contains(": ")) {
            val split = savedModelString.split(": ", limit = 2)
            split[0] to split[1]
        } else {
            (prefs.getString("provider", "OpenAI") ?: "OpenAI") to savedModelString
        }
        
        val apiKey = prefs.getString("api_key_$provider", "") ?: ""

        if (apiKey.isEmpty()) {
            val err = "Missing API Key for $provider. Please configure in Settings."
            adapter.addMessage(ChatMessage(err, false))
            saveBotMessage(err)
            return
        }
        
        binding.tvThinking.visibility = View.VISIBLE
        binding.tvThinking.text = "Reality is thinking..." // Reset text

        // Prepare context
        val history = ArrayList(messages.filter { !it.isAnimating }) // Exclude animating ones if any?

        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                if (provider == "Gemini") {
                     // Gemini logic embedded safely or simplified
                     // callGemini(history, apiKey, model) // Re-implement if needed, for now focusing on Groq/OpenAI Agent
                     withContext(Dispatchers.Main) {
                         adapter.addMessage(ChatMessage("Gemini streaming not yet supported.", false))
                     }
                     "Gemini streaming not yet supported."
                } else {
                     if (isProMode) {
                         // Pro Mode: Still uses the agent loop (not streaming yet)
                         withContext(Dispatchers.Main) { binding.tvThinking.text = "Reality is working..." }
                         runAgentLoop(history, apiKey, model, provider)
                     } else {
                         // Standard Mode: NEW STREAMING PATH
                         processStreamingChat(history, apiKey, model, provider)
                     }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    adapter.addMessage(ChatMessage("Error: ${e.message}", false))
                }
                "Error: ${e.message}"
            }
            
            withContext(Dispatchers.Main) {
                binding.tvThinking.visibility = View.GONE
                
                // For PRO MODE or GEMINI: Add message after complete (not streaming)
                if (isProMode || provider == "Gemini") {
                    adapter.addMessage(ChatMessage(response, false, true))
                    binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                }
                // For STREAMING: Message already added/updated in processStreamingChat
                
                saveBotMessage(response)
                
                // FORCE REFRESH to fix table rendering glitches (for tables in response)
                binding.recyclerChat.post {
                    adapter.notifyDataSetChanged()
                    binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                }
                
                // Reset State
                updateSendButtonState(false)
            }
        }
    }
    
    // --- Agentic AI Core ---

    // --- Standard Chat (Streaming Mode) ---
    /**
     * Streaming SSE-based chat completion.
     * Reads tokens as they arrive and updates UI in real-time.
     * Returns the complete response for saving to history.
     */
    private suspend fun processStreamingChat(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        val TAG = "AIChat"
        
        val url = when(provider) {
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        
        android.util.Log.d(TAG, "=== STREAMING REQUEST START ===")
        android.util.Log.d(TAG, "Provider: $provider")
        android.util.Log.d(TAG, "Model: $model")
        android.util.Log.d(TAG, "URL: $url")
        android.util.Log.d(TAG, "API Key (first 10 chars): ${apiKey.take(10)}...")

        // System prompt with user introduction
        val userIntro = com.neubofy.reality.ui.activity.AISettingsActivity.getUserIntroduction(this) ?: ""
        val systemPrompt = buildString {
            append("You are a helpful, intelligent assistant.")
            if (userIntro.isNotEmpty()) append(" User context: $userIntro")
        }
        
        // OPTIMIZED: Use sliding window + token management
        val optimizedContext = ConversationMemoryManager.buildOptimizedHistory(
            this, history, currentSessionId, systemPrompt
        )
        val jsonMessages = ConversationMemoryManager.toJsonMessages(systemPrompt, optimizedContext)
        
        // Construct Request WITH STREAMING ENABLED
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", jsonMessages)
            put("stream", true) // CRITICAL: Enable SSE streaming
        }
        
        android.util.Log.d(TAG, "Request body model: $model, messages count: ${jsonMessages.length()}")

        // Define outside try for access in catch
        val fullResponse = StringBuilder()
        var conn: java.net.HttpURLConnection? = null
        
        // Helper to update status on UI thread
        suspend fun updateStatus(status: String) {
            withContext(Dispatchers.Main) {
                binding.tvThinking.text = status
                binding.tvThinking.visibility = View.VISIBLE
            }
        }
        
        try {
            // STATUS: Connecting
            updateStatus("🔗 Connecting to $provider...")
            
            // API Call
            conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 120000 // Longer read timeout for streaming
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "text/event-stream") // SSE header
            if (provider == "OpenRouter") {
                 conn.setRequestProperty("HTTP-Referer", "https://neubofy.com")
                 conn.setRequestProperty("X-Title", "Reality App")
            }
            conn.doOutput = true
            
            // STATUS: Sending
            updateStatus("📤 Sending request...")
            android.util.Log.d(TAG, "Sending request...")
            conn.outputStream.write(jsonBody.toString().toByteArray())
            conn.outputStream.flush()
            conn.outputStream.close()
            
            // STATUS: Waiting for response
            updateStatus("⏳ Waiting for $provider...")
            
            val responseCode = conn.responseCode
            android.util.Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                android.util.Log.e(TAG, "API Error: $responseCode - $errorBody")
                
                // STATUS: Error
                updateStatus("❌ Error: $responseCode")
                kotlinx.coroutines.delay(1500) // Show error briefly
                
                return "Error: $responseCode - $errorBody"
            }
            
            // STATUS: Streaming
            updateStatus("✨ Receiving response...")
            android.util.Log.d(TAG, "Response OK, starting stream read...")

            // --- SSE Stream Processing ---
            
            // Add placeholder message to UI immediately (will be updated)
            withContext(Dispatchers.Main) {
                adapter.addMessage(ChatMessage("", false, isAnimating = true))
                binding.recyclerChat.scrollToPosition(adapter.itemCount - 1)
                
                // Start streaming mode - captures TextView for direct updates (no flicker!)
                binding.recyclerChat.post {
                    adapter.startStreaming(binding.recyclerChat)
                }
                
                binding.tvThinking.visibility = View.GONE // Hide status, we're streaming now!
            }
            
            val reader = conn.inputStream.bufferedReader()
            var line: String?
            var updateCounter = 0
            
            while (true) {
                line = reader.readLine()
                
                // End of stream
                if (line == null) break
                
                // Skip empty lines (SSE keepalive)
                if (line.isBlank()) continue
                
                // SSE format: "data: {...json...}" or "data: [DONE]"
                if (line.startsWith("data:")) {
                    val jsonStr = line.removePrefix("data:").trim()
                    
                    if (jsonStr == "[DONE]") {
                        // Stream finished
                        break
                    }
                    
                    // Skip empty data
                    if (jsonStr.isEmpty()) continue
                    
                    try {
                        val chunk = JSONObject(jsonStr)
                        val choices = chunk.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            
                            if (content.isNotEmpty()) {
                                fullResponse.append(content)
                                updateCounter++
                                
                                // Log each chunk for debugging
                                android.util.Log.d("Streaming", "Chunk $updateCounter: '$content'")
                                
                                // Update UI on EVERY chunk for visible typewriter effect
                                val currentText = fullResponse.toString()
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    adapter.updateLastMessageText(currentText)
                                    // Scroll only every 5 updates to reduce jitter
                                    if (updateCounter % 5 == 0) {
                                        binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Malformed JSON chunk, skip silently
                        android.util.Log.w("Streaming", "Chunk parse error: ${e.message}")
                    }
                }
            }
            
            // Final UI update to ensure all content is shown with proper Markwon rendering
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                // Finish streaming mode - triggers ONE final Markwon render
                adapter.finishStreaming()
                binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
            }
            
            reader.close()
        } catch (e: Exception) {
            android.util.Log.e("Streaming", "Stream error: ${e.message}", e)
            return fullResponse.toString().ifEmpty { "Streaming Error: ${e.message}" }
        } finally {
            conn?.disconnect()
        }
        
        return fullResponse.toString()
    }

    // --- Agentic Chat (Pro Mode - Iterative Loop) ---
    private suspend fun runAgentLoop(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        return withContext(Dispatchers.IO) {
            val maxTurns = 10
            var turnCount = 0
            val toolRetryCounts = mutableMapOf<String, Int>() // Track retries per tool+args
            
            // 1. Prepare Initial Context
            val userIntro = com.neubofy.reality.ui.activity.AISettingsActivity.getUserIntroduction(this@AIChatActivity) ?: ""
            val toolDiscovery = com.neubofy.reality.utils.ToolRegistry.getDiscoveryPrompt(this@AIChatActivity)
            val systemPrompt = buildString {
                append("You are Reality Pro, an intelligent Life OS Agent. ")
                append("You have access to the user's real-time data via tools. ")
                append("Use them only when necessary to give accurate, personalized answers. ")
                append("All times are in IST (India Standard Time). ")
                append("\n\nCRITICAL CONSTRAINTS:")
                append("\n- ONE SEARCH POLICY: Web search is extremely expensive. NEVER call `web_search` more than once per user request. Consolidate ALL information needs into a single comprehensive query.")
                append("\n- DISCOVERY FLOW: You start with only `get_tool_schema`. Always fetch schemas for the tools you need in the first turn.")
                append("\n- ANTI-LOOP: Do not call the same tool with the same arguments twice.")
                append("\n- COMPLETION: Do not call tools indefinitely. Aim to answer in 2-3 steps maximum.")
                append("\n- IMAGE: If you use `generate_image`, include the markdown link ![Generated Image](URL) in your message.")
                if (userIntro.isNotEmpty()) append("\n\nUser context: $userIntro")
                append("\n\n$toolDiscovery")
            }
            
            val optimizedContext = ConversationMemoryManager.buildOptimizedHistory(
                this@AIChatActivity, history, currentSessionId, systemPrompt
            )
            val messagesJson = ConversationMemoryManager.toJsonMessages(systemPrompt, optimizedContext)
            
            // 2. Start Loop
            var finalResponse = ""
            var lastImageUrl: String? = null
            val requestedToolIds = mutableSetOf<String>()
            var webSearchCount = 0
            
            while (turnCount < maxTurns) {
                turnCount++
                
                // Track if tool result has image (Check all tool results in context)
                for (i in 0 until messagesJson.length()) {
                    val m = messagesJson.getJSONObject(i)
                    if (m.optString("role") == "tool" && m.optString("content").contains("![Generated Image](")) {
                        val toolContent = m.getString("content")
                        val start = toolContent.indexOf("![Generated Image](") + "![Generated Image](".length
                        val end = toolContent.indexOf(")", start)
                        if (start >= 0 && end > start) {
                            lastImageUrl = toolContent.substring(start, end).trim()
                        }
                    }
                }

                // Construct API Request with Dynamic Tools
                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messagesJson)
                    // Dynamic schema loading: only send meta-tool + tools AI has asked for
                    put("tools", com.neubofy.reality.utils.ToolRegistry.buildToolsArray(this@AIChatActivity, requestedToolIds.toList()))
                    put("tool_choice", "auto")
                }
                
                val apiUrl = when(provider) {
                    "OpenAI" -> "https://api.openai.com/v1/chat/completions"
                    "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
                    "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                
                // Execute Request
                val conn = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 45000 
                conn.readTimeout = 45000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                if (provider == "OpenRouter") {
                     conn.setRequestProperty("HTTP-Referer", "https://neubofy.com")
                     conn.setRequestProperty("X-Title", "Reality App")
                }
                conn.doOutput = true
                
                try {
                    conn.outputStream.write(jsonBody.toString().toByteArray())
                    
                    if (conn.responseCode != 200) {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        return@withContext "API Error ($turnCount): $err"
                    }
                    
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(resp)
                    val choice = responseJson.getJSONArray("choices").getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    val content = message.optString("content", "")
                    val toolCalls = message.optJSONArray("tool_calls")
                    
                    // Add AI response to context for next turn
                    messagesJson.put(message)
                    
                    // Check if done (no tool calls)
                    if (toolCalls == null || toolCalls.length() == 0) {
                        finalResponse = content
                        break // Loop finishes
                    }
                    
                    // Handle Tool Calls
                    for (i in 0 until toolCalls.length()) {
                        val toolCall = toolCalls.getJSONObject(i)
                        val id = toolCall.getString("id")
                        val function = toolCall.getJSONObject("function")
                        val toolName = function.getString("name")
                        val args = function.getString("arguments")
                        
                        val callSignature = "$toolName:$args"
                        val currentRetries = toolRetryCounts.getOrDefault(callSignature, 0)
                        
                        // 1. Handle Schema Requests (Internal state update)
                        if (toolName == "get_tool_schema") {
                            try {
                                val toolId = JSONObject(args).optString("tool_id")
                                if (toolId.isNotEmpty()) requestedToolIds.add(toolId)
                            } catch (e: Exception) {}
                        }
                        
                        // 2. ANTI-LOOP & BUDGET Check
                        val result = if (toolName == "web_search" && webSearchCount >= 1) {
                            "Error: Search Budget Exhausted. You are ONLY allowed one web search per request to save user credits. Use the information already provided or answer based on your knowledge."
                        } else if (currentRetries >= 1 && toolName == "web_search") {
                            "Error: You already performed this exact search. Do not repeat it."
                        } else if (currentRetries >= 3) {
                             "Error: Max retries (3) reached for this specific tool call."
                        } else {
                            if (toolName == "web_search") webSearchCount++
                            toolRetryCounts[callSignature] = currentRetries + 1
                            withContext(Dispatchers.Main) { 
                                adapter.addMessage(ChatMessage("⚙️ Executing: $toolName...", false, false))
                            }
                            com.neubofy.reality.utils.AgentTools.execute(this@AIChatActivity, toolName, args)
                        }
                        
                        messagesJson.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", id)
                            put("name", toolName)
                            put("content", result)
                        })
                    }
                } catch (e: Exception) {
                    return@withContext "Agent Loop Error: ${e.localizedMessage}"
                }
            }
            
            if (turnCount >= maxTurns && finalResponse.isEmpty()) {
                finalResponse = "I reached the limit of 10 steps. Here is what I found so far."
            }
            
            // --- SAFETY AUTO-APPEND ---
            if (lastImageUrl != null && !finalResponse.contains(lastImageUrl)) {
                finalResponse += "\n\n![Generated Image]($lastImageUrl)"
            }
            
            finalResponse
        }
    }

    // Replace the dispatching logic in sendMessage
    /*
            lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                if (provider == "Gemini") {
                     callGemini(history, apiKey, model)
                } else {
                     processChatLoop(history, apiKey, model, provider) // NEW ENTRY POINT
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            // ...
    */

    private fun callGemini(history: List<ChatMessage>, apiKey: String, model: String): String {
        // ... (Keep existing Gemini logic roughly the same, or upgrade later)
        return "Gemini does not support Pro Agent mode yet."
    }

    private fun setupDrawer() {
        // History Adapter
        sessionAdapter = ChatSessionAdapter(emptyList()) { session ->
            loadSession(session.id)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.recyclerHistory.adapter = sessionAdapter
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        
        // Clear History
        binding.btnClearHistory.setOnClickListener {
             lifecycleScope.launch {
                 val sessions = chatDao.getAllSessions()
                 sessions.forEach { chatDao.deleteMessagesForSession(it.id) }
                 sessions.forEach { chatDao.deleteSession(it.id) }
                 
                 messages.clear()
                 adapter.notifyDataSetChanged()
                 loadSessions()
                 currentSessionId = null
                 binding.drawerLayout.closeDrawer(GravityCompat.START)
                 Toast.makeText(this@AIChatActivity, "History Cleared", Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    private fun loadSessions() {
        lifecycleScope.launch {
            val sessions = chatDao.getAllSessions()
            sessionAdapter.updateData(sessions)
            
            // Auto-load last session if exists and current is null
            if (currentSessionId == null && sessions.isNotEmpty()) {
                loadSession(sessions.first().id)
            } else if (sessions.isEmpty()) {
                // Show welcome if no history
                 if (messages.isEmpty()) {
                     adapter.addMessage(ChatMessage("Hello! I am Reality. Tap the sparkle icon to toggle Pro Mode.", false))
                 }
            }
        }
    }
    
    private fun loadSession(sessionId: Long) {
        currentSessionId = sessionId
        lifecycleScope.launch {
            val entities = chatDao.getMessagesForSession(sessionId)
            messages.clear()
            entities.forEach { entity ->
                // Loading from history -> isAnimating = false
                messages.add(ChatMessage(entity.content, entity.isUser, isAnimating = false, timestamp = entity.timestamp))
            }
            adapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) {
                binding.recyclerChat.scrollToPosition(messages.size - 1)
            }
        }
    }
    
    private fun createNewSession() {
        currentSessionId = null
        messages.clear()
        adapter.notifyDataSetChanged()
        adapter.addMessage(ChatMessage("Reviewing fresh context...", false))
    }

    private fun handleSessionInit(firstMessage: String) {
        lifecycleScope.launch {
            if (currentSessionId == null) {
                // Generate title from first few words
                val title = firstMessage.take(30) + "..."
                val session = ChatSession(title = title)
                currentSessionId = chatDao.insertSession(session)
                loadSessions() // Refresh drawer
            }
            
            // Save User Message
            chatDao.insertMessage(ChatMessageEntity(
                sessionId = currentSessionId!!,
                content = firstMessage,
                isUser = true,
                timestamp = System.currentTimeMillis()
            ))
        }
    }
    
    private fun saveBotMessage(content: String) {
        if (currentSessionId != null) {
            lifecycleScope.launch {
                chatDao.insertMessage(ChatMessageEntity(
                    sessionId = currentSessionId!!,
                    content = content,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }

    private fun callOpenAIStyle(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        // Deprecated by processChatLoop, but keeping signature if needed or redirecting
        return "Deprecated" 
    }
    
    // ...
}
