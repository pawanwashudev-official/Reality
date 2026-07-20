package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.databinding.ActivityHealthDashboardBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.R
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class HealthDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityHealthDashboardBinding

    private val PERMISSIONS = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.SleepSessionRecord::class)
    )


    private val requestPermissions = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(PERMISSIONS)) {
            // All permissions granted
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            loader.saveSmartSleepEnabled(true)
            checkAndShowSleepVerification()
            loadData()
        } else {
            Toast.makeText(this, "Permissions required for Smart Sleep Monitoring", Toast.LENGTH_SHORT).show()
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            loader.saveSmartSleepEnabled(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        if (!com.neubofy.reality.utils.RealityProManager.checkVerification(this)) return
        binding = ActivityHealthDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Health Dashboard"
        toolbar.setNavigationOnClickListener { finish() }
        
        binding.swipeRefreshHealth.setOnRefreshListener {
            loadData()
        }
        
        binding.btnManageHealthPerms.setOnClickListener {
            launchHealthPermissionIntent()
        }
        
        // Digital Wellbeing Card -> Usage Stats
        binding.cardDigitalWellbeing.setOnClickListener {
            startActivity(android.content.Intent(this, StatisticsActivity::class.java))
        }
        
        binding.btnGrantUsage.setOnClickListener {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        setupChart()
        setupSmartSleepToggle()
    }
    
    private fun setupChart() {
        val chart = binding.chartHealthTrends
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        
        // Hide right axis
        chart.axisRight.isEnabled = false
        
        // Setup left axis
        val leftAxis = chart.axisLeft
        leftAxis.textColor = ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#20888888")
        leftAxis.axisMinimum = 0f
        
        // Setup X axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant)
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        
        // Legend
        val legend = chart.legend
        legend.textColor = ContextCompat.getColor(this, R.color.md_theme_onSurface)
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_health_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_data -> {
                showResetSleepSyncDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showResetSleepSyncDialog() {
        val options = arrayOf("Today's Sync", "Yesterday's Sync", "Last 7 Days", "All Reality Data")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Manage Reality Data")
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
                    val today = LocalDate.now()
                    
                    val range = when (which) {
                        0 -> { // Today
                             val start = today.minusDays(1).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()
                             val end = today.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant()
                             start to end
                        }
                        1 -> { // Yesterday
                             val start = today.minusDays(2).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()
                             val end = today.minusDays(1).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant()
                             start to end
                        }
                        2 -> { // This Week
                             val start = today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()
                             val end = Instant.now()
                             start to end
                        }
                        else -> { // All Time
                             Instant.EPOCH to Instant.now()
                        }
                    }
                    
                    healthManager.deleteSleepSessions(range.first, range.second)
                    
                    Toast.makeText(this@HealthDashboardActivity, "Data deleted for: ${options[which]}", Toast.LENGTH_SHORT).show()
                    loadData()
                    if (which == 0) checkAndShowSleepVerification()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSmartSleepToggle() {
        // Obsolete UI removed. Kept placeholder function to avoid breaking other calls
        // Or if I check the file, setupSmartSleepToggle() is called in onCreate.
    }
    
    private fun launchHealthPermissionIntent() {
        lifecycleScope.launch {
            try {
                val intent = android.content.Intent("android.settings.HEALTH_CONNECT_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = android.content.Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    startActivity(intent)
                } catch (e2: Exception) {
                     Toast.makeText(this@HealthDashboardActivity, "Unable to open Health Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.neubofy.reality.utils.PermissionHelper.checkAndPromptForCore(this)
        loadData() // Refresh on resume in case permissions granted
        checkAndShowSleepVerification()
    }

    private fun loadData() {
        lifecycleScope.launch {
            binding.swipeRefreshHealth.isRefreshing = true
            try {
                // 1. Load Digital Wellbeing
                val hasUsagePermission = com.neubofy.reality.utils.UsageUtils.hasUsageStatsPermission(this@HealthDashboardActivity)
                if (!hasUsagePermission) {
                    binding.btnGrantUsage.visibility = android.view.View.VISIBLE
                    binding.tvPhonelessTime.text = "--"
                    binding.tvScreenTime.text = "--"
                } else {
                    binding.btnGrantUsage.visibility = android.view.View.GONE
                    val stats = withContext(Dispatchers.IO) { 
                        com.neubofy.reality.utils.UsageUtils.getProUsageMetrics(this@HealthDashboardActivity) 
                    }
                    
                    val now = java.time.LocalTime.now()
                    val minutesSinceMidnight = now.hour * 60 + now.minute
                    val screenTimeMinutes = stats.screenTimeMs / 60000
                    val phonelessMinutes = (minutesSinceMidnight - screenTimeMinutes).coerceAtLeast(0)
                    val totalTime = if (minutesSinceMidnight > 0) minutesSinceMidnight else 1
                    val ratio = (phonelessMinutes.toFloat() / totalTime.toFloat() * 100).toInt()

                    binding.tvScreenTime.text = formatDuration(stats.screenTimeMs)
                    binding.tvPhonelessTime.text = formatDuration(phonelessMinutes * 60000L)
                    binding.tvRealityRatio.text = "$ratio%"
                    binding.tvUnlockCount.text = "${stats.pickupCount}"
                    binding.tvLongestStreak.text = formatDuration(stats.longestStreakMs)
                }

                // 2. Load Physical Health from Health Connect (API-Direct)
                if (com.neubofy.reality.health.HealthManager.isHealthConnectAvailable(this@HealthDashboardActivity)) {
                    val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
                    if (healthManager.hasPermissions()) {
                        binding.tvHealthStatus.visibility = android.view.View.GONE
                        binding.gridHealthDashboard.visibility = android.view.View.VISIBLE
                        
                        val today = LocalDate.now()
                        val steps = withContext(Dispatchers.IO) { healthManager.getSteps(today) }
                        val calories = withContext(Dispatchers.IO) { healthManager.getCalories(today) }
                        val sleep = withContext(Dispatchers.IO) { healthManager.getSleep(today) }

                        binding.tvDashboardSteps.text = String.format("%,d", steps)
                        binding.tvDashboardCalories.text = String.format("%.0f kcal", calories)
                        binding.tvDashboardSleep.text = if (sleep != "No record" && sleep != "Permission Denied") sleep else "No data"
                    } else {
                        binding.tvHealthStatus.visibility = android.view.View.VISIBLE
                        binding.tvHealthStatus.text = "Click System Permissions below to enable Health Access"
                        binding.gridHealthDashboard.visibility = android.view.View.GONE
                    }
                } else {
                    binding.tvHealthStatus.visibility = android.view.View.VISIBLE
                    binding.tvHealthStatus.text = "Health Connect not installed"
                    binding.gridHealthDashboard.visibility = android.view.View.GONE
                }
                
                // 3. Load 7-Day Chart Data
                binding.progressChart.visibility = android.view.View.VISIBLE
                binding.chartHealthTrends.visibility = android.view.View.INVISIBLE
                
                withContext(Dispatchers.IO) {
                    val dates = mutableListOf<String>()
                    val screenTimeEntries = ArrayList<Entry>()
                    val stepsEntries = ArrayList<Entry>()
                    
                    val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
                    val hasHealthAccess = healthManager.hasPermissions()
                    
                    val today = LocalDate.now()
                    
                    for (i in 6 downTo 0) {
                        val date = today.minusDays(i.toLong())
                        val dayLabel = date.format(DateTimeFormatter.ofPattern("EEE"))
                        dates.add(dayLabel)
                        
                        // Screen Time (Hours)
                        var stHours = 0f
                        if (hasUsagePermission) {
                            val stMs = com.neubofy.reality.utils.UsageUtils.getProUsageMetrics(this@HealthDashboardActivity, date).screenTimeMs
                            stHours = stMs / (1000f * 60f * 60f)
                        }
                        screenTimeEntries.add(Entry((6 - i).toFloat(), stHours))
                        
                        // Steps (Thousands)
                        var stepK = 0f
                        if (hasHealthAccess) {
                            try {
                                val s = healthManager.getSteps(date)
                                stepK = s / 1000f
                            } catch (e: Exception) {}
                        }
                        stepsEntries.add(Entry((6 - i).toFloat(), stepK))
                    }
                    
                    withContext(Dispatchers.Main) {
                        val screenSet = LineDataSet(screenTimeEntries, "Screen Time (hrs)").apply {
                            color = ContextCompat.getColor(this@HealthDashboardActivity, R.color.md_theme_primary)
                            setCircleColor(color)
                            lineWidth = 3f
                            circleRadius = 4f
                            setDrawCircleHole(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            setDrawValues(false)
                        }
                        
                        val stepSet = LineDataSet(stepsEntries, "Steps (k)").apply {
                            color = ContextCompat.getColor(this@HealthDashboardActivity, R.color.accent_health)
                            setCircleColor(color)
                            lineWidth = 3f
                            circleRadius = 4f
                            setDrawCircleHole(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            setDrawValues(false)
                        }
                        
                        binding.chartHealthTrends.xAxis.valueFormatter = IndexAxisValueFormatter(dates)
                        binding.chartHealthTrends.data = LineData(screenSet, stepSet)
                        binding.progressChart.visibility = android.view.View.GONE
                        binding.chartHealthTrends.visibility = android.view.View.VISIBLE
                        binding.chartHealthTrends.animateY(1000)
                    }
                }

            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("DASHBOARD ERROR: ${e.message}")
            } finally {
                binding.swipeRefreshHealth.isRefreshing = false
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${hours}h ${mins}m"
    }

    private fun checkAndShowSleepVerification() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        if (!loader.isSmartSleepEnabled()) return

        lifecycleScope.launch {
            val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
            val today = LocalDate.now()
            
            // ALWAYS check Health Connect directly, no local storage for "confirmed" state
            if (healthManager.isSleepSyncedToday(today)) return@launch

            val sleepPrefs = getSharedPreferences("reality_sleep_prefs", android.content.Context.MODE_PRIVATE)
            val googleStart = sleepPrefs.getLong("google_sleep_start", 0L)
            val googleEnd = sleepPrefs.getLong("google_sleep_end", 0L)
            
            if (googleStart > 0 && googleEnd > 0) {
                showSleepVerificationDialog(Instant.ofEpochMilli(googleStart), Instant.ofEpochMilli(googleEnd))
            }
        }
    }

    private fun showSleepVerificationDialog(startTime: Instant, endTime: Instant) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_smart_sleep_confirm, null)
        val tvStart = dialogView.findViewById<TextView>(R.id.tv_sleep_start)
        val tvEnd = dialogView.findViewById<TextView>(R.id.tv_sleep_end)
        
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        tvStart.text = timeFormatter.format(startTime)
        tvEnd.text = timeFormatter.format(endTime)

        val dialog = MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setView(dialogView)
            .setCancelable(false) // User must interact
            .create()

        dialogView.findViewById<android.view.View>(R.id.btn_correct).setOnClickListener {
            // Confirm and Sync
            lifecycleScope.launch {
                val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
                
                // 1. Delete existing Reality sessions for this window to prevent duplication
                healthManager.deleteSleepSessions(startTime.minus(java.time.Duration.ofHours(2)), endTime.plus(java.time.Duration.ofHours(2)))
                
                // 2. Write new session
                healthManager.writeSleepSession(startTime, endTime)
                
                Toast.makeText(this@HealthDashboardActivity, "Sleep data synced to Health Connect", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadData() // Refresh dashboard
            }
        }

        dialogView.findViewById<android.view.View>(R.id.btn_wrong).setOnClickListener {
            dialog.dismiss()
            showSleepEditDialog(startTime, endTime)
        }

        dialogView.findViewById<android.view.View>(R.id.btn_still_sleeping).setOnClickListener {
            // Dismiss for now, will reappear on next resume
            dialog.dismiss()
            Toast.makeText(this, "Okay, we'll ask again later!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showSleepEditDialog(oldStart: Instant, oldEnd: Instant) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_smart_sleep_edit, null)
        val pickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker_start)
        val pickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker_end)

        pickerStart.setIs24HourView(true)
        pickerEnd.setIs24HourView(true)

        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = oldStart.toEpochMilli() }
        val endCal = java.util.Calendar.getInstance().apply { timeInMillis = oldEnd.toEpochMilli() }

        pickerStart.hour = startCal.get(java.util.Calendar.HOUR_OF_DAY)
        pickerStart.minute = startCal.get(java.util.Calendar.MINUTE)
        pickerEnd.hour = endCal.get(java.util.Calendar.HOUR_OF_DAY)
        pickerEnd.minute = endCal.get(java.util.Calendar.MINUTE)

        MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setView(dialogView)
            .setTitle("✏️ Adjust Sleep Time")
            .setPositiveButton("Save & Sync") { _, _ ->
                // Smart Inference for Dates:
                // We assume the user is confirming sleep for "Last Night" usually.
                // Rule:
                // If Start Hour > 14 (2 PM), it likely started Yesterday.
                // If Start Hour < 14, it likely started Today (early morning sleep).
                // End date is inferred from Start date + duration wrapping.
                
                val today = LocalDate.now()
                val startHour = pickerStart.hour
                val endHour = pickerEnd.hour
                
                val startDate = if (startHour > 14) today.minusDays(1) else today
                
                var newStart = startDate.atTime(startHour, pickerStart.minute).atZone(ZoneId.systemDefault()).toInstant()
                
                // For end time: if end hour is less than start hour, it means it crossed midnight -> next day relative to start date
                // OR if end hour is significantly smaller? 
                // Better approach: Construct EndTime on StartDate. If End < Start, add 1 day.
                
                var newEnd = startDate.atTime(endHour, pickerEnd.minute).atZone(ZoneId.systemDefault()).toInstant()
                
                if (newEnd.isBefore(newStart)) {
                    newEnd = newEnd.plus(java.time.Duration.ofDays(1))
                }
                
                // Guard: If user accidentally swaps dates (e.g. Start Today 8am, End Yesterday 10pm -> Logic above handles it?)
                // Actually, if End < Start, we added a day. So it becomes Start Today 8am, End Tomorrow ? 
                // E.g. start 8:00, end 7:00 -> End becomes 7:00 next day. Correct.
                
                // Double check if we are unintentionally setting future dates?
                if (newStart.isAfter(Instant.now())) {
                     // If start inferred is future, then maybe it was supposed to be yesterday?
                     // E.g. It's 10 AM. User picks Start 11 AM (thinking yesterday). My logic: 11 < 14 -> Today 11AM (Future). 
                     // Fix: subtract day.
                     newStart = newStart.minus(java.time.Duration.ofDays(1))
                     newEnd = newEnd.minus(java.time.Duration.ofDays(1))
                }

                lifecycleScope.launch {
                    val healthManager = com.neubofy.reality.health.HealthManager(this@HealthDashboardActivity)
                    
                    // 1. Delete existing Reality sessions to prevent duplication
                    healthManager.deleteSleepSessions(newStart.minus(java.time.Duration.ofHours(12)), newEnd.plus(java.time.Duration.ofHours(12)))
                    
                    // 2. Write new session
                    healthManager.writeSleepSession(newStart, newEnd)
                    
                    Toast.makeText(this@HealthDashboardActivity, "Adjusted sleep data synced", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showSleepVerificationDialog(oldStart, oldEnd) // Go back
            }
            .show()
    }

}
