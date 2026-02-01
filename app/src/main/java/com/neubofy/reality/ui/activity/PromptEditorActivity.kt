package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityPromptEditorBinding
import com.neubofy.reality.utils.ThemeManager

class PromptEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromptEditorBinding
    private val PREFS_NAME = "nightly_prefs"
    
    // Extras
    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PREF_KEY = "extra_pref_key"
        const val EXTRA_DEFAULT_VALUE = "extra_default_value"
        const val EXTRA_PLACEHOLDERS_JSON = "extra_placeholders_json" // List<Pair<String,String>> serialized
    }

    private var prefKey: String? = null
    private var defaultValue: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPromptEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        parseIntent()
        setupToolbar()
        setupSaveButton()
        loadCurrentPrompt()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            binding.rootLayout.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }

    private fun parseIntent() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Edit Prompt"
        prefKey = intent.getStringExtra(EXTRA_PREF_KEY)
        defaultValue = intent.getStringExtra(EXTRA_DEFAULT_VALUE)
        
        binding.toolbar.title = title
        
        // Load chips
        val placeholdersJson = intent.getStringExtra(EXTRA_PLACEHOLDERS_JSON)
        if (placeholdersJson != null) {
            try {
                // Manual simple JSON parsing since we don't have GSON
                // Expecting array of objects: [{"key":"{date}", "desc":"Date"}]
                val jsonArray = org.json.JSONArray(placeholdersJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val key = obj.getString("key")
                    val desc = obj.getString("desc")
                    addPlaceholderChip(key, desc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_reset -> {
                    resetPrompt()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupSaveButton() {
        binding.btnSavePrompt.setOnClickListener {
            savePrompt()
        }
    }

    private fun addPlaceholderChip(text: String, description: String) {
        val chip = Chip(this)
        chip.text = text
        chip.setOnClickListener {
            // Insert at cursor
            val start = binding.etPromptContent.selectionStart.coerceAtLeast(0)
            val end = binding.etPromptContent.selectionEnd.coerceAtLeast(0)
            binding.etPromptContent.text?.replace(start.coerceAtMost(end), start.coerceAtLeast(end), text)
        }
        chip.setOnLongClickListener {
            Toast.makeText(this, description, Toast.LENGTH_SHORT).show()
            true
        }
        binding.chipGroupPlaceholders.addView(chip)
    }

    private fun loadCurrentPrompt() {
        if (prefKey == null) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val current = prefs.getString(prefKey, null) ?: defaultValue
        binding.etPromptContent.setText(current)
    }

    private fun savePrompt() {
        if (prefKey == null) return
        val newText = binding.etPromptContent.text.toString()
        
        if (newText.isBlank()) {
           Toast.makeText(this, "Prompt cannot be empty", Toast.LENGTH_SHORT).show()
           return
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(prefKey, newText)
            .apply()

        Toast.makeText(this, "Prompt Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetPrompt() {
        if (defaultValue != null) {
            binding.etPromptContent.setText(defaultValue)
            Toast.makeText(this, "Reset to default (unsaved)", Toast.LENGTH_SHORT).show()
        }
    }
}
