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
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class AIChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var sessionAdapter: ChatSessionAdapter
    
    // Database
    private lateinit var db: AppDatabase
    private lateinit var chatDao: ChatDao
    private var currentSessionId: Long? = null
    
    // State
    private var isProMode = false

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
    }
    
    override fun onResume() {
        super.onResume()
        refreshModels()
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
        
        // Mode Toggle
        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                isProMode = (checkedId == R.id.btn_mode_pro)
                val modeName = if (isProMode) "Pro Mode (Actions Active)" else "Normal Mode"
                Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
            }
        }

        // Send Button
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.text?.clear()
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
        
        binding.progressThinking.visibility = View.VISIBLE

        // Prepare context
        val history = ArrayList(messages.filter { !it.isAnimating }) // Exclude animating ones if any?

        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                when (provider) {
                    "OpenAI", "Groq", "OpenRouter" -> callOpenAIStyle(history, apiKey, model, provider)
                    "Gemini" -> callGemini(history, apiKey, model)
                    else -> "Provider $provider not supported yet."
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            
            // Check for Actions if Pro Mode
            val finalResponse = if (isProMode && response.trim().startsWith("{") && response.contains("\"tool\"")) {
                executeToolCall(response)
            } else {
                response
            }

            withContext(Dispatchers.Main) {
                binding.progressThinking.visibility = View.GONE
                adapter.addMessage(ChatMessage(finalResponse, false, true))
                binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
                saveBotMessage(finalResponse)
            }
        }
    }
    
    private suspend fun executeToolCall(jsonString: String): String {
        try {
            // Simple parsing to find JSON object
            val startIndex = jsonString.indexOf("{")
            val endIndex = jsonString.lastIndexOf("}")
            if (startIndex == -1 || endIndex == -1) return jsonString
            
            val json = JSONObject(jsonString.substring(startIndex, endIndex + 1))
            val tool = json.optString("tool")
            val params = json.optJSONObject("params")
            
            return when (tool) {
                "get_screen_time" -> {
                    // Mock Implementation
                    "You have used your phone for 3 hours and 15 minutes today."
                }
                "get_tasks" -> {
                    "You have 3 tasks: \n1. Buy groceries\n2. Finish coding\n3. Call Mom"
                }
                else -> "Tool $tool not found."
            }
        } catch (e: Exception) {
            return "Failed to execute tool: ${e.message}"
        }
    }

    private fun callOpenAIStyle(history: List<ChatMessage>, apiKey: String, model: String, provider: String): String {
        val url = when(provider) {
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        
        val jsonMessages = JSONArray()
        
        // System Prompt
        val systemContent = if (isProMode) {
            """
            You are Reality Pro. You have access to tools. 
            To use a tool, reply ONLY with JSON: {"tool": "tool_name", "params": {}}.
            Tools:
            - get_screen_time(): Get daily usage.
            - get_tasks(): Get Google Tasks.
            """
        } else {
            "You are a helpful, intelligent assistant."
        }
        
        jsonMessages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemContent)
        })
        
        for (msg in history) {
            jsonMessages.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.message)
            })
        }
        
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", jsonMessages)
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        if (provider == "OpenRouter") {
             conn.setRequestProperty("HTTP-Referer", "https://neubofy.com")
             conn.setRequestProperty("X-Title", "Reality App")
        }
        conn.doOutput = true
        
        conn.outputStream.write(jsonBody.toString().toByteArray())
        
        return if (conn.responseCode == 200) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            "Error: ${conn.responseCode} - ${conn.errorStream?.bufferedReader()?.use { it.readText() }}"
        }
    }

    private fun callGemini(history: List<ChatMessage>, apiKey: String, model: String): String {
        val modelName = if (model.startsWith("models/")) model else "models/$model"
        val url = "https://generativelanguage.googleapis.com/v1beta/$modelName:generateContent?key=$apiKey"
        
        val jsonContents = JSONArray()
        
        // Gemini System Prompt Hack: Prepend to first message or use system_instruction if supported
        // We'll prepend to first user message logic implicitly via the prompt content construction
        
        val systemPrompt = if (isProMode) {
            "SYSTEM: You have tools. Reply JSON for: get_screen_time, get_tasks.\n"
        } else ""

        history.forEachIndexed { index, msg ->
             val text = if (index == 0) systemPrompt + msg.message else msg.message
             jsonContents.put(JSONObject().apply {
                 put("role", if (msg.isUser) "user" else "model")
                 put("parts", JSONArray().apply {
                     put(JSONObject().apply {
                         put("text", text)
                     })
                 })
             })
        }
        
        val jsonBody = JSONObject().apply {
            put("contents", jsonContents)
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        conn.outputStream.write(jsonBody.toString().toByteArray())
        
        return if (conn.responseCode == 200) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } else {
            "Error: ${conn.responseCode} - ${conn.errorStream?.bufferedReader()?.use { it.readText() }}"
        }
    }
}
