package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.ArrayAdapter
import android.view.View
import android.widget.AdapterView
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

        setupModelSpinners()
    }

    private fun setupModelSpinners() {
        val models = listOf(
            "@cf/meta/llama-3.1-8b-instruct",
            "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            "@cf/qwen/qwen1.5-14b-chat-awq",
            "@hf/thebloke/mistral-7b-instruct-v0.1-awq",
            "@cf/openai/gpt-oss-20b"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerChatModel.adapter = adapter
        binding.spinnerNightlyModel.adapter = adapter

        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")
        val savedChatModel = prefs.getString("chat_model", "@cf/meta/llama-3.1-8b-instruct")
        val savedNightlyModel = prefs.getString("nightly_model", "@cf/meta/llama-3.1-8b-instruct")

        val chatModelIndex = models.indexOf(savedChatModel).takeIf { it >= 0 } ?: 0
        val nightlyModelIndex = models.indexOf(savedNightlyModel).takeIf { it >= 0 } ?: 0

        binding.spinnerChatModel.setSelection(chatModelIndex)
        binding.spinnerNightlyModel.setSelection(nightlyModelIndex)

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
