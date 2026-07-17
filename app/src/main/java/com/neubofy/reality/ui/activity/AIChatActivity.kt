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
    private var hasTriggeredVoiceAuto = false
    private var isGenerating = false
    private var currentGenerationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (!com.neubofy.reality.utils.RealityProManager.checkVerification(this)) return
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
        loadSessions()
        
    }
    
    override fun onResume() {
        super.onResume()
        
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

    }
    
    private fun updateSendButtonState(generating: Boolean) {
        isGenerating = generating
        val iconRes = if (generating) R.drawable.baseline_close_24 else R.drawable.baseline_send_24
        binding.btnSend.setImageResource(iconRes)
    }
    
    private fun sendMessage(text: String) {
        adapter.addMessage(ChatMessage(text, true))
        binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
        
        handleSessionInit(text)
        
        binding.tvThinking.visibility = android.view.View.VISIBLE
        binding.tvThinking.text = "Reality is thinking..."

        val history = ArrayList(messages.filter { !it.isAnimating })

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val response = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { binding.tvThinking.text = "Reality is working..." }
                runAgentLoop(history)
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    adapter.addMessage(ChatMessage("Error: ${e.message}", false))
                }
                "Error: ${e.message}"
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (!isGenerating) return@withContext
                
                binding.tvThinking.visibility = android.view.View.GONE
                updateSendButtonState(false)

                if (response.isNotEmpty()) {
                    adapter.addMessage(ChatMessage(response, false))
                    saveBotMessage(response)
                }
                
                binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
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


    // --- Agentic Chat (Pro Mode - Iterative Loop) ---
    private suspend fun runAgentLoop(history: List<ChatMessage>): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val maxTurns = 10
            var turnCount = 0
            val toolRetryCounts = mutableMapOf<String, Int>() // Track retries per tool+args
            
            // 1. Prepare Initial Context
            val userIntro = com.neubofy.reality.ui.activity.AISettingsActivity.getUserIntroduction(this@AIChatActivity) ?: ""
            val toolDiscovery = com.neubofy.reality.utils.ToolRegistry.getDiscoveryPrompt(this@AIChatActivity)
            val baseSystemPrompt = com.neubofy.reality.ui.activity.AISettingsActivity.getSystemPrompt(this@AIChatActivity)
            val systemPrompt = buildString {
                append(baseSystemPrompt)
                if (userIntro.isNotEmpty()) append("\n\nUser context: $userIntro")
                append("\n\n$toolDiscovery")
            }
            
            val optimizedContext = com.neubofy.reality.utils.ConversationMemoryManager.buildOptimizedHistory(
                this@AIChatActivity, history, currentSessionId, systemPrompt
            )
            val messagesJson = com.neubofy.reality.utils.ConversationMemoryManager.toJsonMessages(systemPrompt, optimizedContext)
            
            // 2. Start Loop
            var finalResponse = ""
            val requestedToolIds = mutableSetOf<String>()
            
            while (turnCount < maxTurns) {
                turnCount++

                val aiPrefs = com.neubofy.reality.utils.SecurePreferences.get(this@AIChatActivity, "ai_prefs")
                val selectedModel = aiPrefs.getString("chat_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"
                val meshKey = aiPrefs.getString("mesh_api_key", "") ?: ""

                val isMeshModel = !selectedModel.startsWith("@cf/")
                if (isMeshModel && meshKey.isEmpty()) {
                    return@withContext "You have selected a Mesh API model but haven't provided an API key. Please add your Mesh API key in settings."
                }

                val apiUrl = if (isMeshModel) {
                    "https://api.meshapi.ai/v1/chat/completions"
                } else {
                    com.neubofy.reality.BuildConfig.AI_URL.removeSuffix("/")
                }

                // Construct API Request with Dynamic Tools
                val jsonBody = org.json.JSONObject().apply {
                    if (!isMeshModel) {
                        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(this@AIChatActivity)
                        val connectionSecret = com.neubofy.reality.utils.IdentityManager.getConnectionSecret(this@AIChatActivity)
                        if (userId.isEmpty() || connectionSecret.isEmpty()) {
                            return@withContext "You are signed in but your identity is not verified. Please go to the Elite Member page and tap 'Refresh Identity & Subscription' to continue."
                        }
                        put("userId", userId)
                        put("connectionSecret", connectionSecret)
                        put("activeExpiry", com.neubofy.reality.utils.IdentityManager.getActiveExpiry(this@AIChatActivity))
                        put("activeDuration", com.neubofy.reality.utils.IdentityManager.getActiveDuration(this@AIChatActivity))
                        put("activeStatus", com.neubofy.reality.utils.IdentityManager.getActiveStatus(this@AIChatActivity))
                        put("planType", com.neubofy.reality.utils.IdentityManager.getActivePlanType(this@AIChatActivity))
                        put("requestCount", com.neubofy.reality.utils.IdentityManager.getAndIncrementDailyAICount(this@AIChatActivity))
                    }
                    put("messages", messagesJson)
                    // Dynamic schema loading: only send meta-tool + tools AI has asked for
                    put("tools", com.neubofy.reality.utils.ToolRegistry.buildToolsArray(this@AIChatActivity, requestedToolIds.toList()))
                    put("tool_choice", "auto")
                    put("model", selectedModel)
                }
                
                if (apiUrl.isBlank()) throw Exception("AI endpoint is not configured (AI_URL is missing in build).")
                
                // Execute Request
                val conn = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 45000 
                conn.readTimeout = 45000
                conn.setRequestProperty("Content-Type", "application/json")
                if (isMeshModel) {
                    conn.setRequestProperty("Authorization", "Bearer $meshKey")
                }
                conn.doOutput = true
                
                try {
                    conn.outputStream.write(jsonBody.toString().toByteArray())
                    
                    if (conn.responseCode != 200) {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        return@withContext "API Error ($turnCount): $err"
                    }
                    
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = org.json.JSONObject(resp)
                    // Handle both direct "response" (common CF output) and "choices" (OpenAI format)
                    var content = ""
                    var toolCalls: org.json.JSONArray? = null
                    val message = org.json.JSONObject()

                    if (responseJson.has("response")) {
                        content = responseJson.getString("response")
                        message.put("role", "assistant")
                        message.put("content", content)
                    } else if (responseJson.has("choices")) {
                        val choice = responseJson.getJSONArray("choices").getJSONObject(0)
                        val msg = choice.getJSONObject("message")
                        content = msg.optString("content", "")
                        toolCalls = msg.optJSONArray("tool_calls")

                        message.put("role", "assistant")
                        message.put("content", content)
                        if (toolCalls != null) {
                            message.put("tool_calls", toolCalls)
                        }
                    } else {
                        content = "Error parsing response: " + responseJson.toString()
                    }
                    
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
                                val toolId = org.json.JSONObject(args).optString("tool_id")
                                if (toolId.isNotEmpty()) requestedToolIds.add(toolId)
                            } catch (e: Exception) {}
                        }
                        
                        // 2. ANTI-LOOP Check
                        val result = if (currentRetries >= 3) {
                             "Error: Max retries (3) reached for this specific tool call."
                        } else {
                            toolRetryCounts[callSignature] = currentRetries + 1
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                adapter.addMessage(ChatMessage("⚙️ Executing: $toolName...", false, false))
                            }
                            com.neubofy.reality.utils.AgentTools.execute(this@AIChatActivity, toolName, args)
                        }
                        
                        messagesJson.put(org.json.JSONObject().apply {
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
            
            finalResponse
        }
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


    
    // ...
}
