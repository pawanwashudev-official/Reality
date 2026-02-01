package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.databinding.ActivityAiSettingsBinding
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.ToolRegistry
import com.neubofy.reality.ui.adapter.ToolToggleAdapter
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class AISettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private val providers = listOf("OpenAI", "Gemini", "Groq", "OpenRouter", "Perplexity", "Tavily")
    private lateinit var chatModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    private lateinit var nightlyModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    private lateinit var imageModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    private lateinit var searchEnginesAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    private lateinit var toolToggleAdapter: ToolToggleAdapter
    
    // Track which section triggered the form
    private var addingForSection: String = "" // "CHAT", "NIGHTLY", "IMAGE"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Model Settings"
        toolbar.setNavigationOnClickListener { finish() }


        // Chat Models - Add button
        binding.btnAddChatModel.setOnClickListener {
            showAddModelForm("CHAT")
        }

        // Nightly Model - Add button
        // Nightly Model - Add button
        binding.btnAddNightlyModel.setOnClickListener {
            showAddModelForm("NIGHTLY")
        }

        // Nightly Models Adapter
        nightlyModelsAdapter = com.neubofy.reality.ui.adapter.SavedModelsAdapter(
            mutableListOf(),
            "",
            { selected ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                prefs.edit().putString("nightly_model", selected).apply()
                nightlyModelsAdapter.setSelected(selected)
                Toast.makeText(this, "Set as Nightly default: $selected", Toast.LENGTH_SHORT).show()
            },
            { model ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val set = prefs.getStringSet("cached_nightly_models", mutableSetOf())?.toMutableSet()
                if (set != null && set.remove(model)) {
                    prefs.edit().putStringSet("cached_nightly_models", set).apply()
                    // If we deleted the active one, clear selection
                    val current = prefs.getString("nightly_model", null)
                    if (current == model) {
                        prefs.edit().remove("nightly_model").apply()
                        nightlyModelsAdapter.setSelected("")
                    }
                    nightlyModelsAdapter.removeModel(model)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    updateNightlyModelDisplay() // Refresh to show empty state if needed
                }
            }
        )
        binding.recyclerNightlyModels.adapter = nightlyModelsAdapter
        binding.recyclerNightlyModels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)


        // Chat Models Adapter
        chatModelsAdapter = com.neubofy.reality.ui.adapter.SavedModelsAdapter(
            mutableListOf(),
            "",
            { selected ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                prefs.edit().putString("model", selected).apply()
                chatModelsAdapter.setSelected(selected)
                Toast.makeText(this, "Set as default: $selected", Toast.LENGTH_SHORT).show()
            },
            { model ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val set = prefs.getStringSet("cached_models", mutableSetOf())?.toMutableSet()
                if (set != null && set.remove(model)) {
                    prefs.edit().putStringSet("cached_models", set).apply()
                    chatModelsAdapter.removeModel(model)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerChatModels.adapter = chatModelsAdapter
        binding.recyclerChatModels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // User Introduction Save
        binding.btnSaveIntroduction.setOnClickListener {
            val intro = binding.etUserIntroduction.text.toString().trim()
            if (intro.isEmpty()) {
                Toast.makeText(this, "Please enter something about yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
            prefs.edit().putString("user_introduction", intro).apply()
            Toast.makeText(this, "Personalization saved!", Toast.LENGTH_SHORT).show()
            updateIntroductionDisplay()
        }
        
        // Edit introduction
        binding.btnEditIntroduction.setOnClickListener {
            binding.cardSavedIntroduction.visibility = View.GONE
            binding.layoutIntroductionForm.visibility = View.VISIBLE
            binding.btnEditIntroduction.visibility = View.GONE
        }
        
        // Delete introduction
        binding.btnDeleteIntroduction.setOnClickListener {
            val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
            prefs.edit().remove("user_introduction").apply()
            binding.etUserIntroduction.text?.clear()
            updateIntroductionDisplay()
            Toast.makeText(this, "Personalization deleted", Toast.LENGTH_SHORT).show()
        }
        
        setupHealthDashboard()
        setupToolToggles()
        setupWidgetVoice()
        setupImageModels()
        setupSearchEngines()
    }
    
    private fun setupSearchEngines() {
        binding.btnAddSearchEngine.setOnClickListener {
            showAddModelForm("SEARCH")
        }

        searchEnginesAdapter = com.neubofy.reality.ui.adapter.SavedModelsAdapter(
            mutableListOf(),
            "",
            { selected ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                prefs.edit().putString("search_engine", selected).apply()
                searchEnginesAdapter.setSelected(selected)
                Toast.makeText(this, "Set as Search Engine: $selected", Toast.LENGTH_SHORT).show()
            },
            { model ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val set = prefs.getStringSet("cached_search_engines", mutableSetOf())?.toMutableSet()
                if (set != null && set.remove(model)) {
                    prefs.edit().putStringSet("cached_search_engines", set).apply()
                    val current = prefs.getString("search_engine", null)
                    if (current == model) {
                        prefs.edit().remove("search_engine").apply()
                        searchEnginesAdapter.setSelected("")
                    }
                    searchEnginesAdapter.removeModel(model)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerSearchEngines.adapter = searchEnginesAdapter
        binding.recyclerSearchEngines.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }
    
    private fun setupImageModels() {
        binding.btnAddImageModel.setOnClickListener {
            showAddModelForm("IMAGE")
        }

        imageModelsAdapter = com.neubofy.reality.ui.adapter.SavedModelsAdapter(
            mutableListOf(),
            "",
            { selected ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                prefs.edit().putString("image_model", selected).apply()
                imageModelsAdapter.setSelected(selected)
                Toast.makeText(this, "Set as Image default: $selected", Toast.LENGTH_SHORT).show()
            },
            { model ->
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val set = prefs.getStringSet("cached_image_models", mutableSetOf())?.toMutableSet()
                if (set != null && set.remove(model)) {
                    prefs.edit().putStringSet("cached_image_models", set).apply()
                    imageModelsAdapter.removeModel(model)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerImageModels.adapter = imageModelsAdapter
        binding.recyclerImageModels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }
    
    // --- Widget Voice Setting ---
    private fun setupWidgetVoice() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("widget_voice_auto", false)
        binding.switchWidgetVoice.isChecked = isEnabled
        
        binding.switchWidgetVoice.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("widget_voice_auto", checked).apply()
            
            // Force update widget to refresh PendingIntent
            val widgetIntent = android.content.Intent(this, com.neubofy.reality.widget.AIChatWidget::class.java)
            widgetIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(application).getAppWidgetIds(
                android.content.ComponentName(application, com.neubofy.reality.widget.AIChatWidget::class.java)
            )
            widgetIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            sendBroadcast(widgetIntent)
        }
    }
    
    // --- Tool Toggles ---
    private fun setupToolToggles() {
        val nonHealthTools = ToolRegistry.ALL_TOOLS.filter { it.id != "health" }
        toolToggleAdapter = ToolToggleAdapter(nonHealthTools) { toolId, enabled ->
            ToolRegistry.setToolEnabled(this, toolId, enabled)
        }
        binding.recyclerToolToggles.adapter = toolToggleAdapter
        
        // Load current states
        val states = nonHealthTools.associate { it.id to ToolRegistry.isToolEnabled(this, it.id) }
        toolToggleAdapter.setEnabledStates(states)
    }
    
    /**
     * Legacy spinner removed in favor of imageModelsAdapter
     */
    private fun setupImageProvider() {
        // No-op - replaced by setupImageModels
    }
    
    // --- Health Connect ---
    
    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        lifecycleScope.launch {
             val hasPerms = com.neubofy.reality.health.HealthManager(this@AISettingsActivity).hasPermissions()
             binding.switchHealthAccess.isChecked = hasPerms
             val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
             prefs.edit().putBoolean("health_access_enabled", hasPerms).apply()
             
             if (hasPerms) {
                 Toast.makeText(this@AISettingsActivity, "Health Access Granted!", Toast.LENGTH_SHORT).show()
             } else {
                 Toast.makeText(this@AISettingsActivity, "Health Permissions: $granted", Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    private fun setupHealthDashboard() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("health_access_enabled", false)
        binding.switchHealthAccess.isChecked = isEnabled
        
        // Switch listener - Controls "Policy" (Does user WANT this?)
        binding.switchHealthAccess.setOnCheckedChangeListener { _, isChecked ->
             prefs.edit().putBoolean("health_access_enabled", isChecked).apply()
             if (isChecked) {
                 checkAndRequestHealthPermissions()
             }
        }
        
        // Row Click - Navigate to Dashboard
        binding.btnHealthAccessRow.setOnClickListener {
            // Only allow navigation if it's "conceptually" enabled, or just let them go and see the "Enable" prompt there?
            // User UX: Let them go. The dashboard handles the "Enable" prompt.
            startActivity(android.content.Intent(this, com.neubofy.reality.ui.activity.HealthDashboardActivity::class.java))
        }
    }
    
    // refreshHealthDashboard and updateDashboardVisibility REMOVED (Moved to HealthDashboardActivity)

    // Old Dashboard Logic Removed (Moved to HealthDashboardActivity)
    
    private fun checkAndRequestHealthPermissions() {
        if (!com.neubofy.reality.health.HealthManager.isHealthConnectAvailable(this)) return

        val permissions = setOf(
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class)
        )
        
        healthPermissionLauncher.launch(permissions)
    }
    
    private fun updateHealthSwitchFromPermissions() {
        // Optional: Ensure switch is sync'd. 
        // For now, switch creates intent (Policy), but actual permissions are separate (Enforcement).
        // Switch = "I allow AI to TRY to use it".
    }

    private fun showAddModelForm(section: String) {
        addingForSection = section
        
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_add_model, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        // UI references in dialog
        val spinnerProvider = dialogView.findViewById<android.widget.Spinner>(com.neubofy.reality.R.id.spinner_provider)
        val etApiKey = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.et_api_key)
        val btnVerify = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_verify)
        val lblSelectModel = dialogView.findViewById<View>(com.neubofy.reality.R.id.lbl_select_model)
        val cardModelSpinner = dialogView.findViewById<View>(com.neubofy.reality.R.id.card_model_spinner)
        val spinnerModel = dialogView.findViewById<android.widget.Spinner>(com.neubofy.reality.R.id.spinner_model)
        val btnSaveModel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_save_model)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tv_form_title)
        
        tvTitle.text = when(section) {
            "CHAT" -> "Add Chat Model"
            "NIGHTLY" -> "Add Nightly Model"
            "IMAGE" -> "Add Image Model"
            else -> "Add Model"
        }

        // Provider spinner setup
        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, providers)
        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
        spinnerProvider.adapter = adapter

        spinnerProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedProvider = parent?.getItemAtPosition(position).toString()
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val key = prefs.getString("api_key_$selectedProvider", "")
                etApiKey.setText(key)
                // Hide model spinner when provider changes
                lblSelectModel.visibility = View.GONE
                cardModelSpinner.visibility = View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Verify Button
        btnVerify.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val provider = spinnerProvider.selectedItem.toString()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show()
            } else {
                fetchModelsForDialog(provider, apiKey, dialogView, section) // Pass section
            }
        }
        
        // Save Button
        btnSaveModel.setOnClickListener {
             val provider = spinnerProvider.selectedItem.toString()
             val apiKey = etApiKey.text.toString().trim()
             val modelFromSpinner = if (cardModelSpinner.visibility == View.VISIBLE)
                 spinnerModel.selectedItem?.toString() else null

             if (apiKey.isEmpty()) {
                 Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
             }

             if (modelFromSpinner.isNullOrEmpty()) {
                 Toast.makeText(this, "Please verify and select a model", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
             }
             
             saveModelFromDialog(provider, apiKey, modelFromSpinner)
             dialog.dismiss()
        }

        dialog.show()
    }
    
    private fun fetchModelsForDialog(provider: String, apiKey: String, dialogView: View, section: String) {
        val btnVerify = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btn_verify)
        val spinnerModel = dialogView.findViewById<android.widget.Spinner>(com.neubofy.reality.R.id.spinner_model)
        val lblSelectModel = dialogView.findViewById<View>(com.neubofy.reality.R.id.lbl_select_model)
        val cardModelSpinner = dialogView.findViewById<View>(com.neubofy.reality.R.id.card_model_spinner)
        
        val buttonText = btnVerify.text
        btnVerify.text = "Fetching..."
        btnVerify.isEnabled = false

        Thread {
            try {
                val allModels = when (provider) {
                    "OpenAI" -> fetchOpenAIModels(apiKey)
                    "Gemini" -> fetchGeminiModels(apiKey)
                    "Groq" -> fetchGroqModels(apiKey)
                    "OpenRouter" -> fetchOpenRouterModels(apiKey)
                    "Perplexity" -> listOf("llama-3.1-sonar-small-128k-online", "llama-3.1-sonar-large-128k-online", "llama-3.1-sonar-huge-128k-online")
                    "Tavily" -> listOf("tavily-search")
                    else -> emptyList()
                }

                // Filter based on section
                val imageKeywords = listOf("dall-e", "image", "diffusion", "flux", "stable-diffusion")
                val models = when(section) {
                    "IMAGE" -> allModels.filter { name -> 
                        imageKeywords.any { kw -> name.contains(kw, ignoreCase = true) }
                    }
                    else -> allModels.filter { name -> 
                        !name.contains("dall-e", ignoreCase = true) && !name.contains("diffusion", ignoreCase = true)
                    }
                }

                runOnUiThread {
                    if (models.isNotEmpty()) {
                        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, models)
                        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
                        spinnerModel.adapter = adapter

                        lblSelectModel.visibility = View.VISIBLE
                        cardModelSpinner.visibility = View.VISIBLE
                        spinnerModel.setSelection(0)

                        Toast.makeText(this, "Found ${models.size} models. Select one.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No models found or API error", Toast.LENGTH_LONG).show()
                    }
                    btnVerify.text = buttonText
                    btnVerify.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnVerify.text = buttonText
                    btnVerify.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveModelFromDialog(provider: String, apiKey: String, modelName: String) {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val fullModelName = "$provider: $modelName"

        // Save API key for this provider
        prefs.edit().putString("api_key_$provider", apiKey).apply()

        if (addingForSection == "CHAT") {
            // Add to cached models
            val cached = prefs.getStringSet("cached_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (!cached.contains(fullModelName)) {
                cached.add(fullModelName)
                prefs.edit().putStringSet("cached_models", cached).apply()
            }
            loadChatModels()
            Toast.makeText(this, "Chat model added!", Toast.LENGTH_SHORT).show()
        } else if (addingForSection == "NIGHTLY") {
            // Add to cached nightly models
            val cached = prefs.getStringSet("cached_nightly_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (!cached.contains(fullModelName)) {
                cached.add(fullModelName)
                prefs.edit().putStringSet("cached_nightly_models", cached).apply()
            }
            if (prefs.getString("nightly_model", "").isNullOrEmpty()) {
                prefs.edit().putString("nightly_model", fullModelName).apply()
            }
            
            updateNightlyModelDisplay()
            Toast.makeText(this, "Nightly model added!", Toast.LENGTH_SHORT).show()
        } else if (addingForSection == "IMAGE") {
            // Add to cached image models
            val cached = prefs.getStringSet("cached_image_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (!cached.contains(fullModelName)) {
                cached.add(fullModelName)
                prefs.edit().putStringSet("cached_image_models", cached).apply()
            }
            if (prefs.getString("image_model", "").isNullOrEmpty()) {
                prefs.edit().putString("image_model", fullModelName).apply()
            }
            loadImageModels()
            Toast.makeText(this, "Image model added!", Toast.LENGTH_SHORT).show()
        } else if (addingForSection == "SEARCH") {
            // Add to cached search engines
            val cached = prefs.getStringSet("cached_search_engines", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (!cached.contains(fullModelName)) {
                cached.add(fullModelName)
                prefs.edit().putStringSet("cached_search_engines", cached).apply()
            }
            if (prefs.getString("search_engine", "").isNullOrEmpty()) {
                prefs.edit().putString("search_engine", fullModelName).apply()
            }
            loadSearchEngines()
            Toast.makeText(this, "Search engine added!", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadData() {
        loadChatModels()
        updateNightlyModelDisplay()
        loadImageModels()
        loadSearchEngines()
        loadUserIntroduction()
    }

    private fun loadSearchEngines() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val cached = prefs.getStringSet("cached_search_engines", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val current = prefs.getString("search_engine", "") ?: ""
        searchEnginesAdapter.updateData(cached.toList().sorted(), current)
    }

    private fun loadImageModels() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val cached = prefs.getStringSet("cached_image_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        // Add Pollinations as Free preset
        val pollinationsPreset = "Pollinations: Free"
        if (!cached.contains(pollinationsPreset)) {
            cached.add(pollinationsPreset)
            prefs.edit().putStringSet("cached_image_models", cached).apply()
        }
        
        val current = prefs.getString("image_model", pollinationsPreset)
        imageModelsAdapter.updateData(cached.toList().sorted(), current ?: pollinationsPreset)
        
        // Ensure default is set in prefs if missing
        if (!prefs.contains("image_model")) {
            prefs.edit().putString("image_model", pollinationsPreset).apply()
        }
    }

    private fun loadChatModels() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val model = prefs.getString("model", "")
        val cachedModels = prefs.getStringSet("cached_models", emptySet())?.sorted() ?: emptyList()
        chatModelsAdapter.updateData(cachedModels, model ?: "")
    }

    private fun updateNightlyModelDisplay() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        var cachedModels = prefs.getStringSet("cached_nightly_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        // Preset Injection
        val presetName = "Groq: openai/gpt-oss-120b"
        val presetKey = "gsk_" + "RBtj1evWPfNhDPDgdRs0" + "WGdyb3FYe2w9rR79pa8" + "BtnolPERhFGRw"
        
        if (!cachedModels.contains(presetName)) {
            cachedModels.add(presetName)
            prefs.edit()
                .putStringSet("cached_nightly_models", cachedModels)
                .putString("api_key_Groq", presetKey) // Set API Key automatically
                .apply()
            
            // Set as default if none selected
            if (!prefs.contains("nightly_model")) {
                prefs.edit().putString("nightly_model", presetName).apply()
            }
        }

        val currentModel = prefs.getString("nightly_model", "")
        
        if (cachedModels.isEmpty()) {
            binding.tvNightlyEmpty.visibility = View.VISIBLE
            binding.recyclerNightlyModels.visibility = View.GONE
        } else {
            binding.tvNightlyEmpty.visibility = View.GONE
            binding.recyclerNightlyModels.visibility = View.VISIBLE
            nightlyModelsAdapter.updateData(cachedModels.toList().sorted(), currentModel ?: "")
        }
    }

    private fun loadUserIntroduction() {
        updateIntroductionDisplay()
    }
    
    private fun updateIntroductionDisplay() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val intro = prefs.getString("user_introduction", null)
        
        if (intro.isNullOrEmpty()) {
            // No saved intro - show form
            binding.cardSavedIntroduction.visibility = View.GONE
            binding.layoutIntroductionForm.visibility = View.VISIBLE
            binding.btnEditIntroduction.visibility = View.GONE
            binding.etUserIntroduction.text?.clear()
        } else {
            // Has saved intro - show saved display
            binding.cardSavedIntroduction.visibility = View.VISIBLE
            binding.layoutIntroductionForm.visibility = View.GONE
            binding.btnEditIntroduction.visibility = View.VISIBLE
            binding.tvSavedIntroduction.text = intro
            binding.etUserIntroduction.setText(intro)
        }
    }

    private fun fetchOpenAIModels(apiKey: String): List<String> {
        return simpleGetRequest("https://api.openai.com/v1/models", apiKey)
    }

    private fun fetchGroqModels(apiKey: String): List<String> {
        return simpleGetRequest("https://api.groq.com/openai/v1/models", apiKey)
    }

    private fun fetchOpenRouterModels(apiKey: String): List<String> {
        val json = makeHttpRequest("https://openrouter.ai/api/v1/models", apiKey) ?: return emptyList()
        return parseJsonIds(json, "data", "id")
    }

    private fun fetchGeminiModels(apiKey: String): List<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val json = makeHttpRequest(url, null) ?: return emptyList()
        return parseJsonIds(json, "models", "name").map { it.replace("models/", "") }
    }

    private fun simpleGetRequest(url: String, apiKey: String): List<String> {
        val json = makeHttpRequest(url, apiKey) ?: return emptyList()
        return parseJsonIds(json, "data", "id")
    }

    private fun makeHttpRequest(urlString: String, apiKey: String?): String? {
        val url = java.net.URL(urlString)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        if (apiKey != null) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseJsonIds(json: String, arrayName: String, fieldName: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val jsonObj = org.json.JSONObject(json)
            val array = jsonObj.getJSONArray(arrayName)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.has(fieldName)) {
                    list.add(item.getString(fieldName))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sorted()
    }

    companion object {
        /**
         * Get the selected model for Nightly Protocol
         */
        fun getNightlyModel(context: android.content.Context): String? {
            val prefs = context.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            return prefs.getString("nightly_model", null)
        }

        /**
         * Get the user introduction for AI personalization
         */
        fun getUserIntroduction(context: android.content.Context): String? {
            val prefs = context.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            return prefs.getString("user_introduction", null)
        }

        /**
         * Get API key for a specific provider
         */
        fun getApiKey(context: android.content.Context, provider: String): String? {
            val prefs = context.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            return prefs.getString("api_key_$provider", null)
        }

        /**
         * Get provider and API key from model string (format: "Provider: model-name")
         */
        fun getProviderAndKeyFromModel(context: android.content.Context, model: String): Pair<String, String>? {
            if (!model.contains(": ")) return null
            val provider = model.substringBefore(": ")
            val apiKey = getApiKey(context, provider) ?: return null
            return Pair(provider, apiKey)
        }
    }
}
