package com.neubofy.reality.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.databinding.ActivityReflectionDetailBinding
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReflectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReflectionDetailBinding
    private val dateRanges = arrayOf("7 Days", "14 Days", "30 Days")
    private var currentXpDays = 7
    private var currentStudyDays = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        binding = ActivityReflectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Erase XP Data Button
        binding.btnEraseXp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Erase All XP Data?")
                .setMessage("This will reset your Total XP, Today XP, Streak, and Level to zero. This cannot be undone.")
                .setPositiveButton("Erase") { _, _ ->
                    eraseAllXpData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        setupSpinners()
        setupCharts()
        loadData()
    }
    
    private fun setupSpinners() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dateRanges)
        
        binding.spinnerXpRange.adapter = adapter
        binding.spinnerXpRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentXpDays = when (position) {
                    0 -> 7
                    1 -> 14
                    else -> 30
                }
                loadXpChart()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        binding.spinnerStudyRange.adapter = adapter
        binding.spinnerStudyRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentStudyDays = when (position) {
                    0 -> 7
                    1 -> 14
                    else -> 30
                }
                loadStudyChart()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupCharts() {
        setupLineChart(binding.chartXpHistory)
        setupBarChart(binding.chartStudyTime)
    }
    
    private fun setupLineChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.md_theme_outlineVariant)
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            animateX(500)
        }
    }
    
    private fun setupBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            setFitBars(true)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.md_theme_outlineVariant)
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            animateY(500)
        }
    }
    
    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Use Projected breakdown for live "Expected" values
            val xp = XPManager.getProjectedDailyXP(applicationContext) 
            val levelName = XPManager.getLevelName(applicationContext, xp.level)
            
            withContext(Dispatchers.Main) {
                // Stats
                binding.tvTotalXp.text = "${xp.totalXP}*" // Indicate projected total
                binding.tvTodayXp.text = if (xp.todayXP >= 0) "+${xp.todayXP}*" else "${xp.todayXP}*"
                binding.tvStreak.text = "${xp.streak} days"
                binding.tvLevel.text = "Level ${xp.level}"
                binding.tvLevelName.text = levelName
                
                // Breakdown (All Projected)
                binding.tvTapasyaXp.text = "+${xp.tapasyaXP}" // Live (Real)
                
                // Projected values with suffix
                binding.tvTaskXp.text = formatXpValue(xp.taskXP) // Not projected anymore
                binding.tvSessionXp.text = "${formatXpValue(xp.sessionXP)}*"
                binding.tvDiaryXp.text = "+${xp.diaryXP}" // 0 usually
                binding.tvBonusXp.text = "+${xp.bonusXP}*"
                binding.tvPenaltyXp.text = if (xp.penaltyXP > 0) "-${xp.penaltyXP}*" else "0"
            }
        }
        
        loadXpChart()
        loadStudyChart()
    }
    
    private fun loadXpChart() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
            
            val entries = mutableListOf<Entry>()
            val labels = mutableListOf<String>()
            
            // Get data for last N days
            for (i in (currentXpDays - 1) downTo 0) {
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                val stats = db.dailyStatsDao().getStatsForDate(dateStr)
                
                val xpValue = stats?.totalXP?.toFloat() ?: 0f
                entries.add(Entry((currentXpDays - 1 - i).toFloat(), xpValue))
                labels.add(dateFormat.format(calendar.time))
            }
            
            withContext(Dispatchers.Main) {
                val dataSet = LineDataSet(entries, "XP").apply {
                    color = ContextCompat.getColor(this@ReflectionDetailActivity, R.color.accent_focus)
                    lineWidth = 2f
                    setDrawCircles(true)
                    circleRadius = 4f
                    setCircleColor(ContextCompat.getColor(this@ReflectionDetailActivity, R.color.accent_focus))
                    setDrawFilled(true)
                    fillColor = ContextCompat.getColor(this@ReflectionDetailActivity, R.color.accent_focus)
                    fillAlpha = 30
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                
                binding.chartXpHistory.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                binding.chartXpHistory.data = LineData(dataSet)
                binding.chartXpHistory.invalidate()
            }
        }
    }
    
    private fun loadStudyChart() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
            
            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()
            
            // Get study time for last N days
            for (i in (currentStudyDays - 1) downTo 0) {
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                
                val startOfDay = calendar.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)
                
                val endOfDay = startOfDay.clone() as Calendar
                endOfDay.add(Calendar.DAY_OF_YEAR, 1)
                
                val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay.timeInMillis, endOfDay.timeInMillis)
                val totalMinutes = sessions.sumOf { it.effectiveTimeMs / 60000 }.toFloat()
                
                entries.add(BarEntry((currentStudyDays - 1 - i).toFloat(), totalMinutes))
                labels.add(dateFormat.format(calendar.time))
            }
            
            withContext(Dispatchers.Main) {
                val dataSet = BarDataSet(entries, "Study Time").apply {
                    color = ContextCompat.getColor(this@ReflectionDetailActivity, R.color.accent_block)
                    setDrawValues(false)
                }
                
                binding.chartStudyTime.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                binding.chartStudyTime.data = BarData(dataSet).apply { barWidth = 0.6f }
                binding.chartStudyTime.invalidate()
            }
        }
    }
    
    private fun formatXpValue(value: Int): String {
        return if (value >= 0) "+$value" else value.toString()
    }
    
    private fun eraseAllXpData() {
        // Use same prefs name as XPManager
        val prefs = getSharedPreferences("reflection_prefs", MODE_PRIVATE)
        prefs.edit()
            .putInt("total_xp", 0)
            .putInt("today_xp", 0)
            .putInt("tapasya_xp", 0)
            .putInt("task_xp", 0)
            .putInt("session_xp", 0)
            .putInt("diary_xp", 0)
            .putInt("bonus_xp", 0)
            .putInt("penalty_xp", 0)
            .putInt("streak", 0)
            .putInt("level", 1)
            .apply()
        
        getSharedPreferences("nightly_prefs", MODE_PRIVATE).edit()
            .remove("protocol_state")
            .remove("current_diary_doc_id")
            .apply()
            
        android.widget.Toast.makeText(this, "All XP data erased", android.widget.Toast.LENGTH_SHORT).show()
        loadData()
    }
}
