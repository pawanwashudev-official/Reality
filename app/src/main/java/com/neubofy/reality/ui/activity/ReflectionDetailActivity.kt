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
        
        // Reflection Settings
        binding.toolbar.findViewById<View>(R.id.btn_reflection_settings)?.setOnClickListener {
            val intent = android.content.Intent(this, ReflectionSettingsActivity::class.java)
            startActivity(intent)
        }

        // Calculate Task XP (Manual Trigger)
        binding.btnCalcTaskXp.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                XPManager.calculateTaskXP(applicationContext)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@ReflectionDetailActivity, "Task XP Updated", android.widget.Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
        }


        
        setupSpinners()
        setupCharts()
        
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
        
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
        
        // Setup History Recycler
        binding.rvXpHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvXpHistory.adapter = com.neubofy.reality.ui.adapter.XpHistoryAdapter(emptyList())
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
    
    // loadData removed (duplicate)
    
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
                
                // Chart Selection Listener
                binding.chartXpHistory.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                        e?.let {
                            val index = it.x.toInt()
                            // Recover date from index logic: (currentXpDays - 1 - i) = index => i = currentXpDays - 1 - index
                            // But cleaner is to recreate the date
                            val daysAgo = currentXpDays - 1 - index
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                            
                            // Fetch stats for this date
                            lifecycleScope.launch(Dispatchers.IO) {
                                val stats = XPManager.getDailyStats(applicationContext, dateStr)
                                withContext(Dispatchers.Main) {
                                    updateBreakdownUI(stats ?: XPManager.XPBreakdown(dateStr), isHistorical = true)
                                }
                            }
                        }
                    }

                    override fun onNothingSelected() {
                        // Revert to Today/Projected
                        loadData()
                    }
                })
            }
        }
    }
    
    private fun updateBreakdownUI(xp: XPManager.XPBreakdown, isHistorical: Boolean = false) {
        if (isHistorical) {
            binding.tvBreakdownTitle.text = "XP Breakdown (${xp.date})"
            binding.tvTodayXp.text = if (xp.totalDailyXP >= 0) "+${xp.totalDailyXP}" else "${xp.totalDailyXP}"
        } else {
            binding.tvBreakdownTitle.text = "Today's XP Breakdown"
            val totalXP = XPManager.getTotalXP(applicationContext)
            binding.tvTotalXp.text = "$totalXP" // Cumulative
            binding.tvTodayXp.text = if (xp.totalDailyXP >= 0) "+${xp.totalDailyXP}" else "${xp.totalDailyXP}"
            binding.tvStreak.text = "${xp.streak} days"
            binding.tvLevel.text = "Level ${xp.level}"
            
            lifecycleScope.launch(Dispatchers.IO) {
                 val levelName = XPManager.getLevelName(applicationContext, xp.level)
                 withContext(Dispatchers.Main) {
                     binding.tvLevelName.text = levelName
                 }
            }
        }

        binding.tvTapasyaXp.text = "+${xp.tapasyaXP}" // Originally mapped to SessionXP but user wants specific?
        // Wait, earlier mapping was: tvTapasyaXp -> sessionXP (from breakdown). 
        // User screenshot shows: "Tapasya XP +0", "Task XP +100", "Session XP +0", "Diary XP +94", "Bonus XP +500", "Penalty XP 0"
        // Let's match Breakdown properties directly
        binding.tvTapasyaXp.text = "+${xp.tapasyaXP}"
        binding.tvTaskXp.text = formatXpValue(xp.taskXP) 
        binding.tvSessionXp.text = "+${xp.sessionXP}"
        binding.tvDiaryXp.text = "+${xp.reflectionXP}" 
        binding.tvBonusXp.text = "+${xp.screenTimeXP}"
        binding.tvPenaltyXp.text = if (xp.penaltyXP > 0) "-${xp.penaltyXP}" else "0"
    }

    private fun loadData() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            // Sync Global Stats first (Recalculate Totals from DB)
            XPManager.recalculateGlobalStats(applicationContext) 
            
            // UNIFICATION: Force recalculation of today's stats on load/refresh
            val today = java.time.LocalDate.now().toString()
            XPManager.recalculateDailyStats(applicationContext, today)
            
            // Use Real breakdown from DB (Projected is no longer needed if we recalculate live)
            val xp = XPManager.getDailyStats(applicationContext, today) ?: XPManager.XPBreakdown(today) 
            
            // Fetch History list (All dates)
            val historyDates = XPManager.getAllStatsDates(applicationContext)
            val historyItems = historyDates.mapNotNull { XPManager.getDailyStats(applicationContext, it) }
            
            withContext(Dispatchers.Main) {
                updateBreakdownUI(xp)
                (binding.rvXpHistory.adapter as? com.neubofy.reality.ui.adapter.XpHistoryAdapter)?.updateData(historyItems)
                binding.swipeRefresh.isRefreshing = false
            }
        }
        
        loadXpChart()
        loadStudyChart()
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
    

}
