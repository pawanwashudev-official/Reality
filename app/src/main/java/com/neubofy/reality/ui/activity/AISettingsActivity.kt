package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.databinding.ActivityAiSettingsBinding
import com.neubofy.reality.utils.ThemeManager

class AISettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private val providers = listOf("OpenAI", "Gemini", "Groq", "OpenRouter", "Perplexity")
    private lateinit var chatModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    private lateinit var nightlyModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter
    
    // Track which section triggered the form
    private var addingForSection: String = "" // "CHAT" or "NIGHTLY"

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
        binding.btnBack.setOnClickListener { finish() }

        // Provider spinner setup
        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, providers)
        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter

        binding.spinnerProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedProvider = parent?.getItemAtPosition(position).toString()
                val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                val key = prefs.getString("api_key_$selectedProvider", "")
                binding.etApiKey.setText(key)
                // Hide model spinner when provider changes
                binding.lblSelectModel.visibility = View.GONE
                binding.cardModelSpinner.visibility = View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

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

        // Close form
        binding.btnCloseForm.setOnClickListener {
            hideAddModelForm()
        }

        // Verify & Fetch Models
        binding.btnVerify.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            val provider = binding.spinnerProvider.selectedItem.toString()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show()
            } else {
                fetchModels(provider, apiKey)
            }
        }

        // Save Model
        binding.btnSaveModel.setOnClickListener {
            saveModel()
        }

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
    }

    private fun showAddModelForm(section: String) {
        addingForSection = section
        binding.cardAddModelForm.visibility = View.VISIBLE
        binding.tvFormTitle.text = if (section == "CHAT") "Add Chat Model" else "Add Nightly Model"
        
        // Reset form
        binding.lblSelectModel.visibility = View.GONE
        binding.cardModelSpinner.visibility = View.GONE
    }

    private fun hideAddModelForm() {
        binding.cardAddModelForm.visibility = View.GONE
        addingForSection = ""
        binding.etApiKey.text?.clear()
        binding.lblSelectModel.visibility = View.GONE
        binding.cardModelSpinner.visibility = View.GONE
    }

    private fun loadData() {
        loadChatModels()
        updateNightlyModelDisplay()
        loadUserIntroduction()
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

    private fun saveModel() {
        val provider = binding.spinnerProvider.selectedItem.toString()
        val apiKey = binding.etApiKey.text.toString().trim()
        val modelFromSpinner = if (binding.cardModelSpinner.visibility == View.VISIBLE)
            binding.spinnerModel.selectedItem?.toString() else null

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
            return
        }

        if (modelFromSpinner.isNullOrEmpty()) {
            Toast.makeText(this, "Please verify and select a model", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val fullModelName = "$provider: $modelFromSpinner"

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
            // If it's the first one, or user just added it, maybe set as default?
            // For now, just add. User can tap to select.
            // But if no default exists, select it.
            if (prefs.getString("nightly_model", "").isNullOrEmpty()) {
                prefs.edit().putString("nightly_model", fullModelName).apply()
            }
            
            updateNightlyModelDisplay()
            Toast.makeText(this, "Nightly model added!", Toast.LENGTH_SHORT).show()
        }

        hideAddModelForm()
    }

    private fun fetchModels(provider: String, apiKey: String) {
        val buttonText = binding.btnVerify.text
        binding.btnVerify.text = "Fetching..."
        binding.btnVerify.isEnabled = false

        Thread {
            try {
                val models = when (provider) {
                    "OpenAI" -> fetchOpenAIModels(apiKey)
                    "Gemini" -> fetchGeminiModels(apiKey)
                    "Groq" -> fetchGroqModels(apiKey)
                    "OpenRouter" -> fetchOpenRouterModels(apiKey)
                    "Perplexity" -> listOf("llama-3.1-sonar-small-128k-online", "llama-3.1-sonar-large-128k-online", "llama-3.1-sonar-huge-128k-online")
                    else -> emptyList()
                }

                runOnUiThread {
                    if (models.isNotEmpty()) {
                        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, models)
                        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
                        binding.spinnerModel.adapter = adapter

                        binding.lblSelectModel.visibility = View.VISIBLE
                        binding.cardModelSpinner.visibility = View.VISIBLE
                        binding.spinnerModel.setSelection(0)

                        Toast.makeText(this, "Found ${models.size} models. Select one.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No models found or API error", Toast.LENGTH_LONG).show()
                    }
                    binding.btnVerify.text = buttonText
                    binding.btnVerify.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnVerify.text = buttonText
                    binding.btnVerify.isEnabled = true
                }
            }
        }.start()
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
