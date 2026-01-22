package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityNightlyPlanBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

class NightlyPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlyPlanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlyPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
        loadPlanData()
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
    }

    private fun loadPlanData() {
        val dateSerializable = intent.getSerializableExtra("date")
        val date = if (dateSerializable is LocalDate) {
            dateSerializable
        } else {
            LocalDate.now() // Fallback
        }

        val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")
        binding.tvHeaderTitle.text = "Plan for ${date.plusDays(1).format(formatter)}"

        lifecycleScope.launch {
            // Load Step 9 Data (Generate Plan)
            val stepData = com.neubofy.reality.data.repository.NightlyRepository.loadStepData(
                this@NightlyPlanActivity, 
                date, 
                com.neubofy.reality.data.NightlyProtocolExecutor.STEP_GENERATE_PLAN
            )

            if (stepData?.resultJson != null) {
                parseAndDisplayPlan(stepData.resultJson)
            } else {
                binding.tvMentorship.text = "No plan data found for this date. Run the Nightly Protocol first."
            }
        }
    }

    private fun parseAndDisplayPlan(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            
            // 1. Mentorship
            val mentorship = json.optString("mentorship", "No specific advice generated.")
            binding.tvMentorship.text = mentorship

            // 2. Alarm Status
            val wakeupTime = json.optString("wakeupTime", "")
            if (wakeupTime.isNotEmpty()) {
                binding.chipAlarmStatus.visibility = View.VISIBLE
                binding.chipAlarmStatus.text = "Wake Up: $wakeupTime"
            } else {
                binding.chipAlarmStatus.visibility = View.GONE
            }

            // 3. Tasks
            binding.layoutTasksContainer.removeAllViews()
            val tasks = json.optJSONArray("tasks")
            if (tasks != null && tasks.length() > 0) {
                binding.tvNoTasks.visibility = View.GONE
                for (i in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(i)
                    val title = task.optString("title")
                    val time = task.optString("startTime", "")
                    
                    val itemView = LayoutInflater.from(this).inflate(R.layout.item_task_log, binding.layoutTasksContainer, false)
                    val tvTitle = itemView.findViewById<TextView>(R.id.tv_log_title)
                    val tvDetails = itemView.findViewById<TextView>(R.id.tv_log_details)
                    val iconStatus = itemView.findViewById<android.widget.ImageView>(R.id.img_log_status) // generic icon

                    tvTitle.text = title
                    tvDetails.text = if (time.isNotEmpty()) "Time: $time" else ""
                    iconStatus.setImageResource(R.drawable.baseline_check_circle_24) // Simple icon reuse
                    
                    binding.layoutTasksContainer.addView(itemView)
                }
            } else {
                binding.tvNoTasks.visibility = View.VISIBLE
            }

            // 4. Events
            binding.layoutEventsContainer.removeAllViews()
            val events = json.optJSONArray("events")
            if (events != null && events.length() > 0) {
                binding.tvNoEvents.visibility = View.GONE
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val title = event.optString("title")
                    val startTime = event.optString("startTime")
                    val endTime = event.optString("endTime")
                    
                    val itemView = LayoutInflater.from(this).inflate(R.layout.item_task_log, binding.layoutEventsContainer, false)
                    val tvTitle = itemView.findViewById<TextView>(R.id.tv_log_title)
                    val tvDetails = itemView.findViewById<TextView>(R.id.tv_log_details)
                    
                    tvTitle.text = title
                    tvDetails.text = "$startTime - $endTime"
                    
                    binding.layoutEventsContainer.addView(itemView)
                }
            } else {
                binding.tvNoEvents.visibility = View.VISIBLE
            }

        } catch (e: Exception) {
            binding.tvMentorship.text = "Error parsing plan data: ${e.message}"
        }
    }
}
