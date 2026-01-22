package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.neubofy.reality.databinding.ActivityNightlyPromptsBinding
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.utils.ThemeManager
import org.json.JSONArray
import org.json.JSONObject

class NightlyPromptsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlyPromptsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlyPromptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardReflectionPrompt.setOnClickListener {
            launchEditor(
                "Reflection Questions",
                "custom_ai_prompt",
                NightlyAIHelper.getDefaultQuestionsPromptTemplate(), // Need to implement/expose this in Helper
                listOf(
                    "{user_intro}" to "User Intro",
                    "{date}" to "Current Date",
                    "{calendar}" to "Calendar Events",
                    "{tasks_due}" to "Tasks Due",
                    "{tasks_completed}" to "Completed Tasks",
                    "{sessions}" to "Tapasya Sessions",
                    "{stats}" to "Statistics"
                )
            )
        }

        binding.cardAnalyzerPrompt.setOnClickListener {
             launchEditor(
                "Reflection Grader",
                "custom_analyzer_prompt",
                NightlyAIHelper.getDefaultAnalyzerPromptTemplate(),
                listOf(
                    "{user_intro}" to "User Intro",
                    "{diary_content}" to "Diary Text"
                )
            )
        }

        binding.cardPlanPrompt.setOnClickListener {
             launchEditor(
                "Plan Extraction",
                "custom_plan_prompt",
                NightlyAIHelper.getDefaultPlanPromptTemplate(),
                listOf(
                     "{plan_content}" to "Raw Plan Text",
                     "{list_context}" to "Available Task Lists"
                )
            )
        }

        binding.cardReportPrompt.setOnClickListener {
             launchEditor(
                "Report Generator",
                "custom_report_prompt",
                NightlyAIHelper.getDefaultReportPromptTemplate(),
                listOf(
                    "{user_intro}" to "User Intro",
                    "{date}" to "Date",
                    "{efficiency}" to "Efficiency %",
                    "{total_effective}" to "Effective Minutes",
                    "{total_planned}" to "Planned Minutes",
                    "{tasks_done}" to "Tasks Completed Count",
                    "{xp_earned}" to "XP Earned",
                    "{level}" to "User Level",
                    "{reflection_content}" to "Diary Text",
                    "{plan_content}" to "Plan Text"
                )
            )
        }

        binding.cardDiaryTemplate.setOnClickListener {
            launchEditor(
                "Diary Template",
                "template_diary",
                com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_DIARY_TEMPLATE,
                listOf(
                    "{date}" to "Current Date",
                    "{data}" to "Usage Stats Block",
                    "{questions}" to "AI Generated Questions"
                )
            )
        }

        binding.cardPlanTemplate.setOnClickListener {
            launchEditor(
                "Plan Template",
                "template_plan",
                com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_PLAN_TEMPLATE,
                listOf(
                    // No specific placeholders used in default plan template currently, but user can add text
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
