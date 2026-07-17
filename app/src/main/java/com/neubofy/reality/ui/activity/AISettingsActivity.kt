package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.ArrayAdapter
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import android.widget.EditText
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.databinding.ActivityAiSettingsBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.utils.ToolRegistry
import com.neubofy.reality.ui.adapter.ToolToggleAdapter
import com.neubofy.reality.utils.IdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
class AISettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private lateinit var toolToggleAdapter: ToolToggleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Model Settings"
        toolbar.setNavigationOnClickListener { finish() }

                // Setup UI for User Introduction and System Prompt
        updateTextDisplays()

        binding.btnEditIntroduction.setOnClickListener {
            val currentIntro = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")
                .getString("user_introduction", "") ?: ""
            showEditDialog(
                title = "Personalization",
                initialText = currentIntro,
                hint = "Tell AI about yourself (goals, challenges, studies)",
                isSystemPrompt = false
            )
        }

        binding.btnEditSystemPrompt.setOnClickListener {
            val currentPrompt = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")
                .getString("custom_system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
            showEditDialog(
                title = "System Prompt",
                initialText = currentPrompt,
                hint = "Modify the AI system prompt",
                isSystemPrompt = true
            )
        }

        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")

// Widget Voice Autostart Toggle
        binding.switchWidgetVoice.isChecked = prefs.getBoolean("widget_voice_autostart", false)
        binding.switchWidgetVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("widget_voice_autostart", isChecked).apply()
        }

        // Health Connect Toggle
        binding.switchHealthAccess.isChecked = ToolRegistry.isToolEnabled(this, "health")
        binding.switchHealthAccess.setOnCheckedChangeListener { _, isChecked ->
            ToolRegistry.setToolEnabled(this, "health", isChecked)
        }
        binding.btnHealthAccessRow.setOnClickListener {
            binding.switchHealthAccess.isChecked = !binding.switchHealthAccess.isChecked
        }


        // Tool Toggles (Excluding Health which is handled specially)
        val allTools = ToolRegistry.ALL_TOOLS.filter { it.id != "health" }.sortedBy { it.category.name }
        toolToggleAdapter = ToolToggleAdapter(allTools) { toolId, isEnabled ->
            ToolRegistry.setToolEnabled(this, toolId, isEnabled)
        }
        binding.recyclerToolToggles.adapter = toolToggleAdapter
        binding.recyclerToolToggles.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        setupMeshApiUI()
        setupModelSpinners()
        setupUsageCheck()
    }

    private fun setupUsageCheck() {
        binding.btnCheckUsage.setOnClickListener {
            binding.btnCheckUsage.isEnabled = false
            binding.btnCheckUsage.text = "Checking..."
            binding.tvUsageResult.visibility = View.GONE
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val userId = IdentityManager.getUserId(this@AISettingsActivity)
                    val connectionSecret = IdentityManager.getConnectionSecret(this@AISettingsActivity)
                    
                    val aiUrl = com.neubofy.reality.BuildConfig.AI_URL
                    
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("connectionSecret", connectionSecret)
                        put("activeExpiry", IdentityManager.getActiveExpiry(this@AISettingsActivity))
                        put("activeDuration", IdentityManager.getActiveDuration(this@AISettingsActivity))
                        put("activeStatus", IdentityManager.getActiveStatus(this@AISettingsActivity))
                        put("planType", IdentityManager.getActivePlanType(this@AISettingsActivity))
                        put("action", "get_usage")
                    }
                    
                    val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(aiUrl)
                        .post(requestBody)
                        .build()
                        
                    val client = OkHttpClient()
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""
                    
                    withContext(Dispatchers.Main) {
                        binding.btnCheckUsage.isEnabled = true
                        binding.btnCheckUsage.text = "Check Neubofy Model Usage"
                        
                        if (response.isSuccessful) {
                            val respJson = JSONObject(responseStr)
                            val usage = respJson.optInt("usage", 0)
                            val limit = respJson.optInt("limit", 25)
                            binding.tvUsageResult.text = "You have used $usage / $limit requests today"
                            binding.tvUsageResult.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            binding.tvUsageResult.text = "Failed to fetch usage: ${response.code}"
                            binding.tvUsageResult.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                        binding.tvUsageResult.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
                    withContext(Dispatchers.Main) {
                        binding.btnCheckUsage.isEnabled = true
                        binding.btnCheckUsage.text = "Check Neubofy Model Usage"
                        binding.tvUsageResult.text = "Network Error"
                        binding.tvUsageResult.setTextColor(getColor(android.R.color.holo_red_dark))
                        binding.tvUsageResult.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupMeshApiUI() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")
        val savedMeshKey = prefs.getString("mesh_api_key", "")
        if (!savedMeshKey.isNullOrEmpty()) {
            binding.etMeshApiKey.setText(savedMeshKey)
        }

        binding.btnMeshSave.setOnClickListener {
            val key = binding.etMeshApiKey.text.toString().trim()
            prefs.edit().putString("mesh_api_key", key).apply()
            Toast.makeText(this, "Mesh API Key saved successfully", Toast.LENGTH_SHORT).show()
        }

        binding.btnMeshRemove.setOnClickListener {
            binding.etMeshApiKey.setText("")
            prefs.edit().remove("mesh_api_key").apply()
            Toast.makeText(this, "Mesh API Key removed", Toast.LENGTH_SHORT).show()
        }

        binding.tvMeshGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.meshapi.ai/")))
        }

        binding.tvMeshPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meshapi.ai/privacy")))
        }

        binding.tvMeshTos.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meshapi.ai/terms")))
        }
    }

    private var modelsFetched = false

    private fun setupModelSpinners() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this@AISettingsActivity, "ai_prefs")
        val savedChatModel = prefs.getString("chat_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"
        val savedNightlyModel = prefs.getString("nightly_model", "@cf/openai/gpt-oss-120b") ?: "@cf/openai/gpt-oss-120b"

        val models = mutableListOf<String>()
        val defaultModels = listOf("@cf/openai/gpt-oss-120b", "openai/gpt-4o", "anthropic/claude-3-5-sonnet")
        models.addAll(defaultModels)
        if (!models.contains(savedChatModel)) models.add(savedChatModel)
        if (!models.contains(savedNightlyModel)) models.add(savedNightlyModel)

        val customModelsStr = prefs.getString("custom_mesh_models", "")
        if (!customModelsStr.isNullOrEmpty()) {
            val customModels = customModelsStr.split(",")
            for (m in customModels) {
                if (m.isNotEmpty() && !models.contains(m)) {
                    models.add(m)
                }
            }
        }

        val adapter = object : ArrayAdapter<String>(this@AISettingsActivity, android.R.layout.simple_spinner_item, models) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                val modelName = getItem(position) ?: ""
                view.text = if (modelName.startsWith("@cf/")) "$modelName (Powered by Neubofy)" else "$modelName (Powered by Mesh API)"
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as android.widget.TextView
                val modelName = getItem(position) ?: ""
                view.text = if (modelName.startsWith("@cf/")) "$modelName (Powered by Neubofy)" else "$modelName (Powered by Mesh API)"
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerChatModel.adapter = adapter
        binding.spinnerNightlyModel.adapter = adapter

        binding.spinnerChatModel.setSelection(models.indexOf(savedChatModel).takeIf { it >= 0 } ?: 0)
        binding.spinnerNightlyModel.setSelection(models.indexOf(savedNightlyModel).takeIf { it >= 0 } ?: 0)

        val fetchModelsIfNeeded = {
            if (!modelsFetched) {
                modelsFetched = true
                Toast.makeText(this@AISettingsActivity, "Fetching latest models...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val fetchedModels = fetchModelsFromWorker()
                    for (m in fetchedModels) {
                        if (!models.contains(m)) models.add(m)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }

        binding.spinnerChatModel.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) fetchModelsIfNeeded()
            false
        }
        
        binding.spinnerNightlyModel.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) fetchModelsIfNeeded()
            false
        }

        binding.spinnerChatModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("chat_model", models[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerNightlyModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("nightly_model", models[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnAddCustomModel.setOnClickListener {
            val input = EditText(this@AISettingsActivity)
            input.hint = "e.g., openai/gpt-4"
            AlertDialog.Builder(this@AISettingsActivity)
                .setTitle("Add Custom Mesh API Model")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val newModel = input.text.toString().trim()
                    if (newModel.isNotEmpty()) {
                        val currentCustoms = prefs.getString("custom_mesh_models", "") ?: ""
                        val updatedCustoms = if (currentCustoms.isEmpty()) newModel else "$currentCustoms,$newModel"
                        prefs.edit().putString("custom_mesh_models", updatedCustoms).apply()
                        setupModelSpinners() // Refresh
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnRemoveCustomModel.visibility = View.VISIBLE
        binding.btnRemoveCustomModel.setOnClickListener {
            val currentCustomsStr = prefs.getString("custom_mesh_models", "") ?: ""
            if (currentCustomsStr.isEmpty()) {
                Toast.makeText(this@AISettingsActivity, "No custom models to remove", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val customModelsList = currentCustomsStr.split(",").filter { it.isNotEmpty() }.toTypedArray()
            AlertDialog.Builder(this@AISettingsActivity)
                .setTitle("Remove Custom Model")
                .setItems(customModelsList) { _, which ->
                    val modelToRemove = customModelsList[which]
                    val newList = customModelsList.filter { it != modelToRemove }.joinToString(",")
                    prefs.edit().putString("custom_mesh_models", newList).apply()

                    // If selected model is being removed, reset to default
                    if (prefs.getString("chat_model", "") == modelToRemove) {
                        prefs.edit().putString("chat_model", "@cf/openai/gpt-oss-120b").apply()
                    }
                    if (prefs.getString("nightly_model", "") == modelToRemove) {
                        prefs.edit().putString("nightly_model", "@cf/openai/gpt-oss-120b").apply()
                    }

                    setupModelSpinners()
                }
                .show()
        }
    }

    private suspend fun fetchModelsFromWorker(): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val fallbackModels = listOf("@cf/openai/gpt-oss-120b", "@cf/openai/gpt-oss-20b")
        val apiUrl = com.neubofy.reality.BuildConfig.AI_URL.removeSuffix("/")
        if (apiUrl.isBlank()) return@withContext fallbackModels

        try {
            val url = java.net.URL(apiUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                val array = json.getJSONArray("models")
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("Failed to fetch models from worker: ${e.message}")
        }
        return@withContext fallbackModels
    }




    private fun updateTextDisplays() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")

        val intro = prefs.getString("user_introduction", null)
        if (intro.isNullOrEmpty()) {
            binding.tvSavedIntroduction.text = "Not set. Tell AI about yourself."
        } else {
            binding.tvSavedIntroduction.text = intro
        }
        
        val prompt = prefs.getString("custom_system_prompt", DEFAULT_SYSTEM_PROMPT)
        binding.tvSavedSystemPrompt.text = prompt
    }

    private fun showEditDialog(title: String, initialText: String, hint: String, isSystemPrompt: Boolean) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_edit_ai_text, null)
        dialog.setContentView(dialogView)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tv_dialog_title)
        val etText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.et_dialog_text)
        val tilText = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.neubofy.reality.R.id.til_edit_text)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_dialog_save)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_dialog_cancel)
        val btnReset = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_dialog_reset)

        tvTitle.text = title
        tilText.hint = hint
        etText.setText(initialText)

        if (isSystemPrompt) {
            btnReset.visibility = android.view.View.VISIBLE
            btnReset.setOnClickListener {
                etText.setText(DEFAULT_SYSTEM_PROMPT)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newText = etText.text.toString().trim()
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")

            if (isSystemPrompt) {
                if (newText.isEmpty()) {
                    Toast.makeText(this, "System prompt cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("custom_system_prompt", newText).apply()
            } else {
                prefs.edit().putString("user_introduction", newText).apply()
            }

            updateTextDisplays()
            dialog.dismiss()
            Toast.makeText(this, "Saved successfully!", Toast.LENGTH_SHORT).show()
        }

        // Request focus and open keyboard slightly delayed to let dialog animate
        dialog.setOnShowListener {
            etText.requestFocus()
        }

        dialog.show()
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are Reality Intelligence Assistant, an intelligence assistant with agentic capabilities like Jarvis for in-app features. You have access to the user's real-time data via tools. Use them only when necessary to give accurate, personalized answers. Keep answers clean, direct, concise, and professional, focusing strictly on the task at hand. Do not make assumptions. Be practical and honest; if you do not have information, do not hallucinate, but simply state that you do not know or deny the request. For short overview information, look at the `get_about_content` tool. For detailed information about any specific feature or working of the app, use the `get_readme_content` tool. All times are in IST (India Standard Time).\n\nCRITICAL CONSTRAINTS:\n- DISCOVERY FLOW: You start with only `get_tool_schema`. Always fetch schemas for the tools you need in the first turn.\n- ANTI-LOOP: Do not call the same tool with the same arguments twice.\n- COMPLETION: Your main goal in chats is to complete specific tasks, not to remember the whole conversation. Be concise."

        fun getUserIntroduction(context: android.content.Context): String? {
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
            return prefs.getString("user_introduction", null)
        }

        fun getSystemPrompt(context: android.content.Context): String {
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
            return prefs.getString("custom_system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        }
    }
}
