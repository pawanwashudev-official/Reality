package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

open class AIChatActivity : AppCompatActivity() {

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
            binding.modeToggleGroup.check(R.id.btn_mode_pro)
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
    }

    private fun setupUI() {
        // Chat Adapter
        adapter = ChatAdapter(messages)
        binding.recyclerChat.adapter = adapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        // Toolbar Actions
        binding.btnMenu.setOnClickListener {
             binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        
        binding.btnNewChat.setOnClickListener {
             createNewSession()
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
        
        // Mode Toggle
        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                isProMode = (checkedId == R.id.btn_mode_pro)
                val modeName = if (isProMode) "Pro Mode (Actions Active)" else "Normal Mode"
                Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
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
                    sendMessage(text)
                    binding.etMessage.text?.clear()
                }
            }
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
                 // Delete sessions logic if needed, or just clear all
                 // For now, let's delete session entities if possible or assume cascading?
                 // Room doesn't cascade unless configured. Manual delete:
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
                     adapter.addMessage(ChatMessage("Hello! I am Reality. Select a mode and start chatting.", false))
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

    private fun refreshModels() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        var currentModel = prefs.getString("model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
        val cachedModels = prefs.getStringSet("cached_models", emptySet())?.sorted() ?: emptyList()
        
        val modelsList = if (cachedModels.isNotEmpty()) cachedModels.toMutableList() else mutableListOf(currentModel)
        if (!modelsList.contains(currentModel)) {
            modelsList.add(0, currentModel)
        }
        
        val adapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, modelsList)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerChatModel.adapter = adapter
        
        val currentIdx = modelsList.indexOf(currentModel)
        if (currentIdx >= 0) binding.spinnerChatModel.setSelection(currentIdx)
        
        binding.spinnerChatModel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
             override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                 val selected = modelsList[position]
                 if (selected != currentModel) {
                     prefs.edit().putString("model", selected).apply()
                     currentModel = selected
                     Toast.makeText(this@AIChatActivity, "Switched to $selected", Toast.LENGTH_SHORT).show()
                 }
             }
             override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
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
                     "Gemini Agent mode coming soon."
                } else {
                     if (isProMode) {
                         // Update UI with "Acting..." if using tools (could be callback based in future)
                         withContext(Dispatchers.Main) { binding.tvThinking.text = "Reality is working..." }
                         runAgentLoop(history, apiKey, model, provider)
                     } else {
                         processStandardChat(history, apiKey, model, provider)
                     }
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            
            withContext(Dispatchers.Main) {
                binding.tvThinking.visibility = View.GONE
                adapter.addMessage(ChatMessage(response, false, true))
                binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                saveBotMessage(response)
            }
        }
    }
    
    // --- Agentic AI Core ---

    // --- Standard Chat (Normal Mode) ---
    private suspend fun processStandardChat(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        val url = when(provider) {
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

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
        
        // 3. Construct Request (NO TOOLS)
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", jsonMessages)
        }

        // 4. API Call
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        if (provider == "OpenRouter") {
             conn.setRequestProperty("HTTP-Referer", "https://neubofy.com")
             conn.setRequestProperty("X-Title", "Reality App")
        }
        conn.doOutput = true
        
        conn.outputStream.write(jsonBody.toString().toByteArray())
        
        if (conn.responseCode != 200) {
            return "Error: ${conn.responseCode} - ${conn.errorStream?.bufferedReader()?.use { it.readText() }}"
        }

        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(resp)
        val choice = responseJson.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")
        return message.optString("content", "")
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
                append("Use them whenever needed to give accurate, personalized answers. ")
                append("All times are in IST (India Standard Time). ")
                if (userIntro.isNotEmpty()) append("\n\nUser context: $userIntro")
                append("\n\n$toolDiscovery")
            }
            
            val optimizedContext = ConversationMemoryManager.buildOptimizedHistory(
                this@AIChatActivity, history, currentSessionId, systemPrompt
            )
            val messagesJson = ConversationMemoryManager.toJsonMessages(systemPrompt, optimizedContext)
            
            // 2. Start Loop
            var finalResponse = ""
            
            while (turnCount < maxTurns) {
                turnCount++
                
                // Construct API Request
                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messagesJson)
                    // Only send tools if we haven't hit the limit? No, always send, AI decides.
                    put("tools", com.neubofy.reality.utils.AgentTools.definitions)
                    put("tool_choice", "auto")
                }
                
                val url = when(provider) {
                    "OpenAI" -> "https://api.openai.com/v1/chat/completions"
                    "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
                    "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                
                // Execute Request
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 45000 // Extended timeout for agent
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
                        val name = function.getString("name")
                        val args = function.getString("arguments")
                        
                        // Retry Logic Check
                        val callSignature = "$name:$args"
                        val currentRetries = toolRetryCounts.getOrDefault(callSignature, 0)
                        
                        val result = if (currentRetries >= 3) {
                             "Error: Max retries (3) reached for this specific tool call. Do not try it again with same arguments."
                        } else {
                            toolRetryCounts[callSignature] = currentRetries + 1
                            
                            // UI Feedback
                            withContext(Dispatchers.Main) {
                                binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                                adapter.addMessage(ChatMessage("⚙️ Executing: $name...", false, false))
                            }
                            
                            com.neubofy.reality.utils.AgentTools.execute(this@AIChatActivity, name, args)
                        }
                        
                        // Add Result to Context
                        messagesJson.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", id)
                            put("name", name)
                            put("content", result)
                        })
                    }
                    
                    // Loop continues to next turn with new context (AI message + Tool results)
                    
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

    private fun callOpenAIStyle(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        // Deprecated by processChatLoop, but keeping signature if needed or redirecting
        return "Deprecated" 
    }
    
    // ...
}
