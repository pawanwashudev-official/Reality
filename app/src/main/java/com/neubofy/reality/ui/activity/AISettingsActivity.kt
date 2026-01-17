package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.databinding.ActivityAiSettingsBinding
import com.neubofy.reality.utils.ThemeManager

class AISettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private val providers = listOf("OpenAI", "Gemini", "Groq", "OpenRouter", "Perplexity")
    private lateinit var savedModelsAdapter: com.neubofy.reality.ui.adapter.SavedModelsAdapter

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

        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, providers)
        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter

        binding.spinnerProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                 val selectedProvider = parent?.getItemAtPosition(position).toString()
                 val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                 val key = prefs.getString("api_key_$selectedProvider", "")
                 binding.etApiKey.setText(key)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnSave.setOnClickListener {
            saveData()
        }
        
        binding.btnVerify.setOnClickListener {
             val apiKey = binding.etApiKey.text.toString().trim()
             val provider = binding.spinnerProvider.selectedItem.toString()
             if (apiKey.isEmpty()) {
                 Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show()
             } else {
                 fetchModels(provider, apiKey)
             }
        // etModelName references removed
    }
        savedModelsAdapter = com.neubofy.reality.ui.adapter.SavedModelsAdapter(
            mutableListOf(), 
            "",
            { selected ->
                 val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                 prefs.edit().putString("model", selected).apply()
                 savedModelsAdapter.setSelected(selected)
                 Toast.makeText(this, "Set as default: $selected", Toast.LENGTH_SHORT).show()
            },
            { model ->
                 val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
                 val set = prefs.getStringSet("cached_models", mutableSetOf())?.toMutableSet()
                 if (set != null && set.remove(model)) {
                     prefs.edit().putStringSet("cached_models", set).apply()
                     savedModelsAdapter.removeModel(model)
                     Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                 }
            }
        )
        binding.recyclerSavedModels.adapter = savedModelsAdapter
        binding.recyclerSavedModels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun loadData() {
        loadForm()
        loadSavedModels()
    }
    
    private fun loadForm() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val provider = prefs.getString("provider", "OpenAI")
        // Load key for this provider
        val apiKey = prefs.getString("api_key_$provider", "")
        
        // We do NOT load model into etModelName because that's for NEW entry.
        // But previously we did. "model" pref is the default selected.
        // The user wants form CLEARED.
        // But on startup? Maybe leave it clear.
        // But we DO want to show the API key if it exists? 
        // User said "cleared out after saving".
        // On restart? Usually you want to see if you have a key.
        // I will keep key loading on startup.
        
        val position = providers.indexOf(provider)
        if (position >= 0) {
            binding.spinnerProvider.setSelection(position)
        }
        binding.etApiKey.setText(apiKey)
    }
    
    private fun loadSavedModels() {
        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val model = prefs.getString("model", "")
        
        // Load cached models if available
        val cachedModels = prefs.getStringSet("cached_models", emptySet())?.sorted() ?: emptyList()
        
        savedModelsAdapter.updateData(cachedModels, model ?: "")
        
        if (cachedModels.isNotEmpty()) {
            val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, cachedModels)
            adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
            binding.spinnerModel.adapter = adapter
            
            binding.lblSelectModel.visibility = android.view.View.VISIBLE
            binding.cardModelSpinner.visibility = android.view.View.VISIBLE
            
            // Try to select current model (checking with and without provider prefix just in case)
            val index = cachedModels.indexOfFirst { it == model || it.endsWith(": $model") }
            if (index >= 0) {
                binding.spinnerModel.setSelection(index)
            }
        }
    }

    private fun saveData() {
        val provider = binding.spinnerProvider.selectedItem.toString()
        val apiKey = binding.etApiKey.text.toString().trim()
        val selectedModel = if (binding.spinnerModel.adapter != null && binding.spinnerModel.paddingRight > 0) // check visibility or count?
            binding.spinnerModel.selectedItem?.toString() else ""
            
        // Use simpler check: verify spinner is visible
        val modelFromSpinner = if (binding.cardModelSpinner.visibility == android.view.View.VISIBLE) 
             binding.spinnerModel.selectedItem?.toString() else ""

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (modelFromSpinner.isNullOrEmpty()) {
             Toast.makeText(this, "Please verify and select a model", Toast.LENGTH_SHORT).show()
             return
        }

        val prefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("provider", provider)
            .putString("api_key_$provider", apiKey) // Save per provider
            // .putString("model", model) // Default model removed? OR saved?
            // User says "when i slect anyone then save it". 
            // Saving "model" pref usually means "Default".
            // But user also wants list.
            // I'll save selected as default "model" AND add to list.
            .apply()

        // Add to cache
        val cached = prefs.getStringSet("cached_models", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val fullModelName = "$provider: $modelFromSpinner"
        if (!cached.contains(fullModelName)) {
            cached.add(fullModelName)
            prefs.edit().putStringSet("cached_models", cached).apply()
        }
        
        Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show()
        
        // Clear inputs to be ready for next
        binding.etApiKey.text?.clear()
        
        // Refresh List
        loadSavedModels()
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
                    "Perplexity" -> listOf("llama-3.1-sonar-small-128k-online", "llama-3.1-sonar-large-128k-online", "llama-3.1-sonar-huge-128k-online") // Static for now as API might differ
                    else -> emptyList()
                }
                
                runOnUiThread {
                    if (models.isNotEmpty()) {
                        val adapter = ArrayAdapter(this, com.neubofy.reality.R.layout.spinner_item, models)
                        adapter.setDropDownViewResource(com.neubofy.reality.R.layout.spinner_dropdown_item)
                        binding.spinnerModel.adapter = adapter
                        
                        binding.lblSelectModel.visibility = android.view.View.VISIBLE
                        binding.cardModelSpinner.visibility = android.view.View.VISIBLE
                        
                        // Select first by default
                        binding.spinnerModel.setSelection(0)
                        
                        // DO NOT SAVE to cached_models here.
                        // Wait for manual save.
                            
                        Toast.makeText(this, "Models Fetched: ${models.size}. Select one and Save.", Toast.LENGTH_SHORT).show()
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
    
    // Groq compatible with OpenAI format
    private fun fetchGroqModels(apiKey: String): List<String> {
        return simpleGetRequest("https://api.groq.com/openai/v1/models", apiKey)
    }
    
    private fun fetchOpenRouterModels(apiKey: String): List<String> {
        // OpenRouter returns data: [ { id: "..." } ... ]
        // Using same parser usually works if field is 'id'
        val json = makeHttpRequest("https://openrouter.ai/api/v1/models", apiKey) ?: return emptyList()
        return parseJsonIds(json, "data", "id")
    }

    private fun fetchGeminiModels(apiKey: String): List<String> {
        // Gemini uses GET https://generativelanguage.googleapis.com/v1beta/models?key=API_KEY
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val json = makeHttpRequest(url, null) ?: return emptyList()
        // Response: { "models": [ { "name": "models/gemini-pro", ... } ] }
        // "name" field contains full path. we usually want "gemini-pro" or the full name.
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
}
