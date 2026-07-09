package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.neubofy.reality.databinding.ActivityNightlyPromptsBinding
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.utils.ThemeManager
import org.json.JSONArray
import org.json.JSONObject

class NightlyPromptsActivity : BaseActivity() {

    private lateinit var binding: ActivityNightlyPromptsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlyPromptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupMode()
        setupListeners()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                binding.header.paddingLeft,
                systemBars.top + 16,
                binding.header.paddingRight,
                binding.header.paddingBottom
            )
            insets
        }
    }

    private fun setupMode() {
        val mode = intent.getStringExtra("mode") ?: "prompts"
        if (mode == "templates") {
            binding.tvHeaderTitle.text = "Docs Templates"
            binding.tvPromptsSubtitle.text = "Customize the Google Docs templates generated during the protocol."
            
            // Hide prompts
            binding.cardReflectionPrompt.visibility = android.view.View.GONE
            binding.cardAnalyzerPrompt.visibility = android.view.View.GONE
            binding.cardPlanPrompt.visibility = android.view.View.GONE
            binding.cardReportPrompt.visibility = android.view.View.GONE
            binding.cardTaskNormalizePrompt.visibility = android.view.View.GONE
            binding.btnAiSettings.visibility = android.view.View.GONE
            
            // Show templates
            binding.tvTemplatesTitle.visibility = android.view.View.VISIBLE
            binding.cardDiaryTemplate.visibility = android.view.View.VISIBLE
            binding.cardPlanTemplate.visibility = android.view.View.VISIBLE
        } else {
            binding.tvHeaderTitle.text = "System Prompts"
            binding.tvPromptsSubtitle.text = "Customize how the AI thinks and analyzes."
            
            // Show prompts
            binding.cardReflectionPrompt.visibility = android.view.View.VISIBLE
            binding.cardAnalyzerPrompt.visibility = android.view.View.VISIBLE
            binding.cardPlanPrompt.visibility = android.view.View.VISIBLE
            binding.cardReportPrompt.visibility = android.view.View.VISIBLE
            binding.btnAiSettings.visibility = android.view.View.VISIBLE
            
            // Hide templates
            binding.tvTemplatesTitle.visibility = android.view.View.GONE
            binding.cardDiaryTemplate.visibility = android.view.View.GONE
            binding.cardPlanTemplate.visibility = android.view.View.GONE
            binding.cardTaskNormalizePrompt.visibility = android.view.View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardReflectionPrompt.setOnClickListener {
            launchEditor(
                "Reflection Questions (Step 2: Create Diary)",
                "custom_ai_prompt",
                NightlyAIHelper.getDefaultQuestionsPromptTemplate(),
                listOf(
                    "{user_intro}" to "User Introduction",
                    "{date}" to "Current Date",
                    "{calendar}" to "Calendar Events",
                    "{tasks_due}" to "Tasks Due Today",
                    "{tasks_completed}" to "Completed Tasks",
                    "{sessions}" to "Tapasya Sessions",
                    "{health}" to "Digital Wellbeing Data",
                    "{stats}" to "Day Statistics",
                    "{report}" to "Previous Day AI Report"
                )
            )
        }

        binding.cardAnalyzerPrompt.setOnClickListener {
             launchEditor(
                "Reflection Grader (Step 3: Save Analytics)",
                "custom_analyzer_prompt",
                NightlyAIHelper.getDefaultAnalyzerPromptTemplate(),
                listOf(
                    "{user_intro}" to "User Introduction",
                    "{diary_content}" to "Full Diary Text"
                )
             )
        }

        binding.cardPlanPrompt.setOnClickListener {
             launchEditor(
                "Plan Extraction (Step 5: Apply Plan)",
                "custom_plan_prompt",
                NightlyAIHelper.getDefaultPlanPromptTemplate(),
                emptyList()
             )
        }

        binding.cardReportPrompt.setOnClickListener {
             launchEditor(
                "Report Generator (Step 6: Report)",
                "custom_report_prompt",
                NightlyAIHelper.getDefaultReportPromptTemplate(),
                listOf(
                    "{user_intro}" to "User Introduction",
                    "{date}" to "Date",
                    "{efficiency}" to "Efficiency %",
                    "{total_effective}" to "Effective Minutes",
                    "{total_planned}" to "Planned Minutes",
                    "{tasks_done}" to "Tasks Completed Count",
                    "{xp_earned}" to "XP Earned",
                    "{level}" to "User Level",
                    "{reflection_content}" to "User Reflection Text",
                    "{plan_content}" to "Tomorrow's Plan Text"
                )
             )
        }

        binding.cardDiaryTemplate.setOnClickListener {
            launchEditor(
                "Diary Template (Step 2: Create Diary)",
                "template_diary",
                com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_DIARY_TEMPLATE,
                listOf(
                    "{date}" to "Current Date",
                    "{data}" to "Day Summary & Usage Stats",
                    "{stats}" to "Statistics Block",
                    "{questions}" to "AI Generated Reflection Questions"
                )
            )
        }

        binding.cardPlanTemplate.setOnClickListener {
            launchEditor(
                "Plan Template (Step 4: Create Plan)",
                "template_plan",
                com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_PLAN_TEMPLATE,
                listOf(
                    // User fills this template manually; no auto-replaced placeholders
                )
            )
        }

        binding.btnAiSettings.setOnClickListener {
            startActivity(Intent(this, AISettingsActivity::class.java))
        }
    }

    private fun launchEditor(title: String, prefKey: String, defaultVal: String, placeholders: List<Pair<String,String>>) {
        val intent = Intent(this, PromptEditorActivity::class.java).apply {
            putExtra(PromptEditorActivity.EXTRA_TITLE, title)
            putExtra(PromptEditorActivity.EXTRA_PREF_KEY, prefKey)
            putExtra(PromptEditorActivity.EXTRA_DEFAULT_VALUE, defaultVal)
            
            // Serialize placeholders to JSON
            val jsonArray = JSONArray()
            placeholders.forEach {
                val obj = JSONObject()
                obj.put("key", it.first)
                obj.put("desc", it.second)
                jsonArray.put(obj)
            }
            putExtra(PromptEditorActivity.EXTRA_PLACEHOLDERS_JSON, jsonArray.toString())
        }
        startActivity(intent)
    }
}
