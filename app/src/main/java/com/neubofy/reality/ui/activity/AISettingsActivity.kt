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

        // Widget Voice Autostart Toggle
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "ai_prefs")
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
        fun getUserIntroduction(context: android.content.Context): String? {
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
            return prefs.getString("user_introduction", null)
        }
    }
}
