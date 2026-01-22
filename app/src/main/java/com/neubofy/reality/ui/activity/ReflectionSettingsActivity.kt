package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityReflectionSettingsBinding
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.UsageUtils
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReflectionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReflectionSettingsBinding
    private val PREFS_NAME = "nightly_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReflectionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupRetentionSpinner()
        setupGamificationStats()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRetentionSpinner() {
        val options = listOf("1 Week", "2 Weeks", "4 Weeks")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRetention.adapter = adapter

        // Load saved preference
        val prefs = getSharedPreferences("xp_prefs", MODE_PRIVATE)
        val savedDays = prefs.getInt("xp_retention_days", 7)
        val savedIndex = when (savedDays) {
            7 -> 0
            14 -> 1
            28 -> 2
            else -> 0
        }
        binding.spinnerRetention.setSelection(savedIndex)

        binding.spinnerRetention.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val weeks = when (position) {
                    0 -> 1
                    1 -> 2
                    2 -> 4
                    else -> 1
                }
                XPManager.setRetentionPolicy(this@ReflectionSettingsActivity, weeks)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupGamificationStats() {
        // Setup "Edit Levels" Button (Dialog)
        binding.btnOpenLevelEditor.setOnClickListener {
            if (checkLocked()) return@setOnClickListener
            try {
                showLevelEditorDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // Setup Screen Time Limit
        setupScreenTimeLimit()

        // Initial Refresh
        refreshGamificationTable()
    }

    private fun checkLocked(): Boolean {
        val loader = SavedPreferencesLoader(this)
        val strictData = loader.getStrictModeData()
        if (strictData.isEnabled && strictData.isGamificationLocked) {
            Toast.makeText(this, "Locked by Strict Mode", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    private fun showLevelEditorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_level_editor, null)

        // Find views in dialog layout
        val spinnerSelector = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_level_selector)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.et_level_name_dialog)
        val etXp = dialogView.findViewById<android.widget.EditText>(R.id.et_level_xp_dialog)
        val etStreak = dialogView.findViewById<android.widget.EditText>(R.id.et_level_streak_dialog)

        // Setup Selector
        val levels = (1..100).map { "Level $it" }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
        spinnerSelector.adapter = spinnerAdapter

        // Helper to load data
        fun loadLevel(levelId: Int) {
            val allLevels = XPManager.getAllLevels(this)
            val levelInfo = allLevels.find { it.level == levelId } ?: allLevels[0]
            etName.setText(levelInfo.name)
            etXp.setText(levelInfo.requiredXP.toString())
            etStreak.setText(levelInfo.requiredStreak.toString())
        }

        // Listener
        spinnerSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val levelId = position + 1
                loadLevel(levelId)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Build Dialog
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Update Preset") { _, _ ->
                // Save Logic
                val levelId = spinnerSelector.selectedItemPosition + 1

                val name = etName.text.toString().trim()
                val xp = etXp.text.toString().toIntOrNull()
                val streak = etStreak.text.toString().toIntOrNull()

                if (name.isNotEmpty() && xp != null && streak != null) {
                    XPManager.saveLevelOverride(this, levelId, name, xp, streak)
                    Toast.makeText(this, "Level $levelId preset updated!", Toast.LENGTH_SHORT).show()
                    refreshGamificationTable() // Refresh main table
                } else {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshGamificationTable() {
        binding.llLevelTableContainer.removeAllViews()
        val allLevels = XPManager.getAllLevels(this)
        val currentLevel = XPManager.getLevel(this)

        for (level in allLevels) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                if (level.level == currentLevel) {
                    setBackgroundColor(android.graphics.Color.parseColor("#20FFFFFF")) // Highlight
                }
            }

            // ID
            row.addView(android.widget.TextView(this).apply {
                text = level.level.toString()
                width = dpToPx(30)
                setTextColor(
                    if (level.level == currentLevel) context.getColor(R.color.md_theme_primary)
                    else android.graphics.Color.GRAY
                )
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // Name
            row.addView(android.widget.TextView(this).apply {
                text = level.name
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(context.getColor(R.color.md_theme_onSurface))
            })

            // XP
            row.addView(android.widget.TextView(this).apply {
                text = "${level.requiredXP / 1000}k"
                width = dpToPx(60)
                gravity = android.view.Gravity.END
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 12f
            })

            // Streak
            row.addView(android.widget.TextView(this).apply {
                text = level.requiredStreak.toString()
                width = dpToPx(40)
                gravity = android.view.Gravity.END
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 12f
            })

            binding.llLevelTableContainer.addView(row)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupScreenTimeLimit() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        fun updateDisplay() {
            val savedLimit = prefs.getInt("screen_time_limit_minutes", 0)
            if (savedLimit > 0) {
                binding.tvCurrentLimit.text = "$savedLimit min / day"
                binding.etScreenTimeLimit.setText(savedLimit.toString())
            } else {
                binding.tvCurrentLimit.text = "Not set"
                binding.etScreenTimeLimit.setText("")
            }
        }

        // Initial state
        updateDisplay()

        // Load affected apps & usage stats
        lifecycleScope.launch(Dispatchers.IO) {
            val prefsLoader = SavedPreferencesLoader(this@ReflectionSettingsActivity)
            val focusData = prefsLoader.getFocusModeData()
            val allSelected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefsLoader.getFocusModeSelectedApps())

            // 1. Filter for Focus Mode apps
            val affectedPkgs = allSelected.filter { pkg ->
                prefsLoader.getBlockedAppConfig(pkg).blockInFocus
            }

            // 2. Get Usage Stats (if permitted)
            val hasUsagePerm = UsageUtils.hasUsageStatsPermission(this@ReflectionSettingsActivity)
            val totalUsedMillis = UsageUtils.getFocusedAppsUsage(this@ReflectionSettingsActivity)


            // 3. Get Labels
            val affectedLabels = affectedPkgs.mapNotNull { pkg ->
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    null
                }
            }.sorted()

            withContext(Dispatchers.Main) {
                // Update Apps List
                binding.tvAffectedApps.text = if (affectedLabels.isNotEmpty()) {
                    "Applied to: ${affectedLabels.joinToString(", ")}"
                } else {
                    "Applied to: No apps marked for Focus Mode"
                }

                // Update Progress & Status
                val limitMins = prefs.getInt("screen_time_limit_minutes", 0)
                if (!hasUsagePerm) {
                    binding.tvLimitStatus.text = "Permission needed for usage stats"
                    binding.progressLimit.isIndeterminate = true
                } else if (limitMins > 0) {
                    val usedMins = (totalUsedMillis / 60000).toInt()

                    binding.progressLimit.isIndeterminate = false
                    binding.progressLimit.max = limitMins
                    binding.progressLimit.progress = usedMins

                    if (usedMins > limitMins) {
                        val over = usedMins - limitMins
                        val penalty = (over * 10).coerceAtMost(500)
                        binding.tvLimitStatus.text = "Used: ${usedMins}m (Over by ${over}m ⚠️) • Penalty: -${penalty} XP"
                        binding.progressLimit.setIndicatorColor(android.graphics.Color.parseColor("#B00020")) // Error Red
                    } else {
                        val left = limitMins - usedMins
                        val bonus = (left * 10).coerceAtMost(500)
                        binding.tvLimitStatus.text = "Used: ${usedMins}m (${left}m left) • Bonus: +${bonus} XP"
                        binding.progressLimit.setIndicatorColor(getColor(R.color.md_theme_primary))
                    }
                } else {
                    binding.tvLimitStatus.text = "Set a limit to see progress"
                    binding.progressLimit.progress = 0
                }
            }
        }

        // Edit button
        binding.btnEditLimit.setOnClickListener {
            val loader = SavedPreferencesLoader(this)
            val strictData = loader.getStrictModeData()

            if (strictData.isEnabled && strictData.isNightlyLimitLocked) {
                Toast.makeText(this, "Locked by Strict Mode", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.layoutViewLimit.visibility = View.GONE
            binding.layoutEditLimit.visibility = View.VISIBLE
            binding.etScreenTimeLimit.requestFocus()
        }

        // Save button
        binding.btnSaveScreenLimit.setOnClickListener {
            val limitStr = binding.etScreenTimeLimit.text.toString()
            val limit = limitStr.toIntOrNull() ?: 0

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt("screen_time_limit_minutes", limit)
                .apply()

            updateDisplay()

            binding.layoutEditLimit.visibility = View.GONE
            binding.layoutViewLimit.visibility = View.VISIBLE

            Toast.makeText(this, "Screen time limit saved", Toast.LENGTH_SHORT).show()
        }
        
        // Delete XP Data Button
        binding.btnDeleteXpData.setOnClickListener {
             showDeleteDataDialog()
        }
    }
    
    private fun showDeleteDataDialog() {
        lifecycleScope.launch(Dispatchers.Main) {
            val dates = XPManager.getAllStatsDates(this@ReflectionSettingsActivity)
            
            if (dates.isEmpty()) {
                Toast.makeText(this@ReflectionSettingsActivity, "No XP history found.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val selectedIndices = mutableListOf<Int>()
            val dateArray = dates.toTypedArray()
            
            MaterialAlertDialogBuilder(this@ReflectionSettingsActivity)
                .setTitle("Delete XP History")
                .setMultiChoiceItems(dateArray, null) { _, which, isChecked ->
                    if (isChecked) {
                        selectedIndices.add(which)
                    } else if (selectedIndices.contains(which)) {
                        selectedIndices.remove(Integer.valueOf(which))
                    }
                }
                .setPositiveButton("Delete Selected") { _, _ ->
                    if (selectedIndices.isNotEmpty()) {
                        val selectedDates = selectedIndices.map { dateArray[it] }
                        deleteDates(selectedDates)
                    } else {
                        Toast.makeText(this@ReflectionSettingsActivity, "No dates selected", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("Delete ALL") { _, _ ->
                     MaterialAlertDialogBuilder(this@ReflectionSettingsActivity)
                        .setTitle("Confirm Delete All")
                        .setMessage("Are you sure you want to wipe ALL XP history? This cannot be undone.")
                        .setPositiveButton("Delete Everything") { _, _ ->
                            deleteDates(dates)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun deleteDates(dates: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            dates.forEach { date ->
                XPManager.deleteDailyStats(applicationContext, date)
            }
            // Add slight delay to ensure DB syncs before refresh
            kotlinx.coroutines.delay(500)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ReflectionSettingsActivity, "Deleted ${dates.size} entries", Toast.LENGTH_SHORT).show()
                refreshGamificationTable() // Refresh UI stats if any
            }
        }
    }
}
