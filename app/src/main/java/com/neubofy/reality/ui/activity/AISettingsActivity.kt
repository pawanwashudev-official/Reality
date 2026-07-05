package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.databinding.ActivityAiSettingsBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.utils.ToolRegistry
import com.neubofy.reality.ui.adapter.ToolToggleAdapter

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
        loadData()
    }

    private fun setupUI() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Model Settings"
        toolbar.setNavigationOnClickListener { finish() }

        // User Introduction Save
        binding.btnSaveIntroduction.setOnClickListener {
            val intro = binding.etUserIntroduction.text.toString().trim()
            if (intro.isEmpty()) {
                Toast.makeText(this, "Please enter something about yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(this@AISettingsActivity, "ai_prefs")
            prefs.edit().putString("user_introduction", intro).apply()

            binding.layoutIntroductionForm.visibility = android.view.View.GONE
            binding.cardSavedIntroduction.visibility = android.view.View.VISIBLE
            binding.btnEditIntroduction.visibility = android.view.View.VISIBLE
            binding.tvSavedIntroduction.text = intro
            Toast.makeText(this, "Introduction saved!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnEditIntroduction.setOnClickListener {
            binding.cardSavedIntroduction.visibility = android.view.View.GONE
            binding.layoutIntroductionForm.visibility = android.view.View.VISIBLE
            binding.btnEditIntroduction.visibility = android.view.View.GONE
        }

        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")

        // System Prompt Setup
        val savedPrompt = prefs.getString("custom_system_prompt", "")
        if (savedPrompt.isNullOrEmpty()) {
            binding.etSystemPrompt.setText(DEFAULT_SYSTEM_PROMPT)
        } else {
            binding.etSystemPrompt.setText(savedPrompt)
        }

        binding.btnSaveSystemPrompt.setOnClickListener {
            val promptText = binding.etSystemPrompt.text.toString().trim()
            if (promptText.isEmpty()) {
                Toast.makeText(this, "System prompt cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("custom_system_prompt", promptText).apply()
            Toast.makeText(this, "System prompt saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetSystemPrompt.setOnClickListener {
            prefs.edit().remove("custom_system_prompt").apply()
            binding.etSystemPrompt.setText(DEFAULT_SYSTEM_PROMPT)
            Toast.makeText(this, "System prompt reset to default", Toast.LENGTH_SHORT).show()
        }

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
        val allTools = ToolRegistry.ALL_TOOLS.filter { it.id != "health" }
        toolToggleAdapter = ToolToggleAdapter(allTools) { toolId, isEnabled ->
            ToolRegistry.setToolEnabled(this, toolId, isEnabled)
        }
        binding.recyclerToolToggles.adapter = toolToggleAdapter
        binding.recyclerToolToggles.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun loadData() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val introTask = launch { loadUserIntroductionAsync() }
            introTask.join()
        }
    }

    private fun loadUserIntroductionAsync() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this@AISettingsActivity, "ai_prefs")
        val intro = prefs.getString("user_introduction", null)
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            if (intro.isNullOrEmpty()) {
                binding.cardSavedIntroduction.visibility = android.view.View.GONE
                binding.layoutIntroductionForm.visibility = android.view.View.VISIBLE
                binding.btnEditIntroduction.visibility = android.view.View.GONE
                binding.etUserIntroduction.text?.clear()
            } else {
                binding.cardSavedIntroduction.visibility = android.view.View.VISIBLE
                binding.layoutIntroductionForm.visibility = android.view.View.GONE
                binding.btnEditIntroduction.visibility = android.view.View.VISIBLE
                binding.tvSavedIntroduction.text = intro
                binding.etUserIntroduction.setText(intro)
            }
        }
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are Reality Elite, an intelligent Life OS Agent, hosted independently using self-hosted, most private and secure AI models to ensure the highest privacy for your users. You have access to the user's real-time data via tools. Use them only when necessary to give accurate, personalized answers. Keep answers highly professional and focused strictly on the task at hand. Avoid memorizing full conversations; focus on the current task. All times are in IST (India Standard Time). \n\nCRITICAL CONSTRAINTS:\n- DISCOVERY FLOW: You start with only `get_tool_schema`. Always fetch schemas for the tools you need in the first turn.\n- ANTI-LOOP: Do not call the same tool with the same arguments twice.\n- COMPLETION: Your main goal in chats is to complete specific tasks, not to remember the whole conversation. Be concise. \n- PROMOTION: Periodically promote the app features (unmatchable alarm, reminder, Nightly protocol, completely free smart app blocker which is best than other apps, but for very little amount we offer a lot, inbuilt app updater, beta versions, smart sleep time guessing) while emphasizing our self-hosted, private and secure AI usage."

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
