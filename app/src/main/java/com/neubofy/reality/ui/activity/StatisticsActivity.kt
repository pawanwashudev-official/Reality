package com.neubofy.reality.ui.activity

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.neubofy.reality.databinding.ActivityStatisticsBinding
import com.neubofy.reality.databinding.ItemAppUsageBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StatisticsActivity : BaseActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private val appUsageList = mutableListOf<AppUsageItem>()
    private lateinit var adapter: AppUsageAdapter
    
    data class AppUsageItem(
        val packageName: String,
        val appName: String,
        val usageTime: Long,
        val icon: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply padding to toolbar only or root? Root is easiest for quick fix
            // Actually, we should apply top to toolbar and bottom to root/scrollview
            // But simply padding root works for preventing overlap
            view.setPadding(0, insets.top, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Usage Statistics"
        
        setupRecyclerView()
        loadStatistics()
    }
    
    private fun setupRecyclerView() {
        adapter = AppUsageAdapter()
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter
    }
    
    private fun loadStatistics() {
        // Show loading
        val rotateAnim = android.view.animation.AnimationUtils.loadAnimation(this, com.neubofy.reality.R.anim.rotate_indefinite)
        binding.loadingLogo.visibility = android.view.View.VISIBLE
        binding.loadingLogo.startAnimation(rotateAnim)
        
        binding.contentLayout.visibility = android.view.View.GONE
        
        lifecycleScope.launch {
            // Background Work
            val data = withContext(Dispatchers.IO) {
                val usage7Days = getLast7DaysUsage()
                
                // Load today's heavy data
                val todaysStats = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(this@StatisticsActivity)
                val totalScreenTime = com.neubofy.reality.utils.UsageUtils.getScreenTimeSinceMidnight(this@StatisticsActivity)
                
                Triple(usage7Days, todaysStats, totalScreenTime)
            }
            
            // UI Updates
            binding.loadingLogo.clearAnimation()
            binding.loadingLogo.visibility = android.view.View.GONE
            binding.contentLayout.visibility = android.view.View.VISIBLE
            
            val (usageData, todaysStatsMap, totalScreenTime) = data
            
            // 1. Chart
            setupChart(binding.chartDaily, usageData)
            
            // 2. Weekly Avg
            val avgUsage = if (usageData.isNotEmpty()) usageData.map { it.second }.average().toLong() else 0L
            val avgHours = TimeUnit.MILLISECONDS.toHours(avgUsage)
            val avgMins = TimeUnit.MILLISECONDS.toMinutes(avgUsage) % 60
            binding.tvWeeklyAvg.text = "${avgHours}h ${avgMins}m"
            
            // 3. Today's List
            updateTodayUsageUI(todaysStatsMap, totalScreenTime)
        }
    }

    private fun updateTodayUsageUI(statsMap: Map<String, Long>, totalScreenTime: Long) {
        val packageManager = packageManager
        appUsageList.clear()
        
        // This part is lightweight enough for Main thread, or could be moved to IO too if list is huge
        // But PackageManager looks ups can be slow, so let's be careful.
        // Actually, let's keep it simple for now as the heavy lifting was the usage query.
        // Optimization: We could move PM calls to IO too, but let's test stability first.
        
        // Update Total Text
        val hours = TimeUnit.MILLISECONDS.toHours(totalScreenTime)
        val mins = TimeUnit.MILLISECONDS.toMinutes(totalScreenTime) % 60
        binding.tvTodayUsage.text = "${hours}h ${mins}m"
        
        // Filter and Populate
        statsMap.forEach { (pkg, usageTime) ->
             if (pkg == "com.android.systemui" || pkg.contains("launcher") || pkg == packageName) {
                return@forEach
             }
             if (usageTime > 60000) { 
                 try {
                     // CAUTION: PM calls on main thread can frame drop.
                     // Ideally we cache this or load in IO. 
                     // For stability fix 1.0, we just solved the 5-second IO freeze.
                     val appInfo = packageManager.getApplicationInfo(pkg, 0)
                     val label = packageManager.getApplicationLabel(appInfo).toString()
                     val icon = packageManager.getApplicationIcon(appInfo)
                     appUsageList.add(AppUsageItem(pkg, label, usageTime, icon))
                 } catch (e: Exception) { }
             }
        }
        
        appUsageList.sortByDescending { it.usageTime }
        adapter.notifyDataSetChanged()
    }
    
    private fun getLast7DaysUsage(): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return result
        
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
        
        // System packages to exclude
        val excludedPrefixes = listOf(
            "com.android.",
            "android",
            "com.google.android.inputmethod",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher"
        )
        
        // Get usage for last 7 days
        for (i in 6 downTo 0) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val dayStart = calendar.timeInMillis
            
            // End of day (or now)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis 
            
            // Limit end to "now" if it's today
            val queryEnd = if (i == 0) System.currentTimeMillis() else dayEnd
            
            val dayLabel = dateFormat.format(Date(dayStart))
            
            try {
                // Use queryUsageStats with INTERVAL_DAILY for proper daily bucketing
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStart,
                    queryEnd
                )
                
                // Sum foreground time for non-system apps
                var totalTime = 0L
                for (stat in stats) {
                    // Skip if no foreground time
                    if (stat.totalTimeInForeground <= 0) continue
                    
                    // Skip system apps
                    if (excludedPrefixes.any { stat.packageName.startsWith(it) }) continue
                    if (stat.packageName.contains("launcher", ignoreCase = true)) continue
                    if (stat.packageName == packageName) continue // Exclude self
                    
                    totalTime += stat.totalTimeInForeground
                }
                
                result.add(Pair(dayLabel, totalTime))
            } catch (e: Exception) {
                result.add(Pair(dayLabel, 0L))
            }
        }
        
        return result
    }
    
    private fun setupChart(chart: BarChart, data: List<Pair<String, Long>>) {
        val entries = data.mapIndexed { index, pair ->
            val hours = TimeUnit.MILLISECONDS.toMinutes(pair.second).toFloat() / 60f
            BarEntry(index.toFloat(), hours)
        }
        
        val colorOnSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorOutlineVariant = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
        val colorPrimary = resolveColor(com.google.android.material.R.attr.colorPrimary)

        val dataSet = BarDataSet(entries, "Hours").apply {
            color = colorPrimary
            valueTextColor = colorOnSurface
            valueTextSize = 10f
            setDrawValues(false)
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        chart.apply {
            this.data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setScaleEnabled(false)
            setTouchEnabled(false)
            
            // X-axis (days)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(data.map { it.first })
                granularity = 1f
                textColor = colorOnSurfaceVariant
                setDrawGridLines(false)
                setDrawAxisLine(false)
            }
            
            // Y-axis (hours)
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f   // Show labels every hour
                textColor = colorOnSurfaceVariant
                setDrawGridLines(true)
                gridColor = colorOutlineVariant
                setDrawAxisLine(false)
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}h"
                    }
                }
            }
            axisRight.isEnabled = false
            
            setBackgroundColor(Color.TRANSPARENT)
            animateY(800)
            invalidate()
        }
    }

    private fun resolveColor(@androidx.annotation.AttrRes attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
    
    // Adapter
    inner class AppUsageAdapter : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = appUsageList[position]
            holder.binding.tvAppName.text = item.appName
            holder.binding.ivIcon.setImageDrawable(item.icon)
            
            val hours = TimeUnit.MILLISECONDS.toHours(item.usageTime)
            val mins = TimeUnit.MILLISECONDS.toMinutes(item.usageTime) % 60
            holder.binding.tvUsageTime.text = if(hours > 0) "${hours}h ${mins}m" else "${mins}m"
            
            val maxUsage = appUsageList.firstOrNull()?.usageTime ?: 1L
            val progress = (item.usageTime.toDouble() / maxUsage.toDouble() * 100).toInt()
            holder.binding.progressBar.progress = progress
        }
        
        override fun getItemCount() = appUsageList.size
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
