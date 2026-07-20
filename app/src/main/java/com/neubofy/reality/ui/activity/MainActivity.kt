package com.neubofy.reality.ui.activity

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.neubofy.reality.ui.base.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityMainBinding
import com.neubofy.reality.databinding.DialogPermissionInfoBinding
import com.neubofy.reality.databinding.DialogRemoveAntiUninstallBinding

import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.dialogs.StartFocusMode
import com.neubofy.reality.ui.fragments.anti_uninstall.ChooseModeFragment
import com.neubofy.reality.utils.SavedPreferencesLoader
import kotlinx.coroutines.*
import com.neubofy.reality.utils.BlockerStatusManager
import com.neubofy.reality.utils.BlockerStatus
import com.neubofy.reality.utils.BlockerType
import com.neubofy.reality.utils.TimeTools
import android.widget.ImageView
import com.neubofy.reality.utils.ThemeManager
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.view.LayoutInflater
import android.widget.TextView

class MainActivity : BaseActivity() {

    private val PERMISSIONS = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.SleepSessionRecord::class)
    )

    private val requestPermissions = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(PERMISSIONS)) {
            // Permissions successfully granted
            Log.d("HealthConnect", "All permissions granted")
        } else {
            // Not all permissions granted
            Log.d("HealthConnect", "Not all permissions granted")
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectFocusModeUnblockedAppsLauncher: ActivityResultLauncher<Intent>
    private lateinit var addAutoFocusHoursActivity: ActivityResultLauncher<Intent>

    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private lateinit var options: ActivityOptionsCompat
    private var isDeviceAdminOn = false
    private var isAntiUninstallOn = false
    private var statusUpdaterJob: Job? = null
    private var nightlyStepsJob: Job? = null
    private var nightlySessionJob: Job? = null
    private var currentBlockerStatus: BlockerStatus? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        // Check for Intro (Moved from LauncherActivity)
        // Check for Intro & Onboarding
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        if (!prefs.getBoolean("intro_shown", false)) {
            val intent = Intent(this, SecurityIntroActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Apply status bar inset only to header — matches standard separation on other pages
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)

        setupActivityLaunchers()
        setupClickListeners()
        setupAiChatFab()
        // logAppSignature() - masked as per user request

        // Startup permission check removed as per user request. 
        // Permissions are now requested on-demand.
        
        // Observe Terminal Logs
        scope.launch {
            com.neubofy.reality.utils.TerminalLogger.logs.collect { logText ->
                binding.tvTerminalLog.text = logText
            }
        }
        
        // Handle deep link actions (from Alarm dismiss, etc.)
        handleIntentAction(intent)
        
        // Apply theme borders to all cards (Run once on create)
        ThemeManager.applyToAllCards(binding.root)
        updateThemeVisuals()
        
        // Staggered Entry Animation (Run only once on create to avoid scroll lag)
        startStaggeredAnimation()
        
        // Passive identity refresh on app update
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            val lastVersionCode = prefs.getLong("last_version_code", -1L)
            if (lastVersionCode != currentVersionCode && lastVersionCode != -1L) {
                if (com.neubofy.reality.google.GoogleAuthManager.isSignedIn(this)) {
                    val appContext = applicationContext
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.neubofy.reality.utils.IdentityManager.refreshIdentity(appContext)
                    }
                }
            }
            prefs.edit().putLong("last_version_code", currentVersionCode).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleIntentAction(intent: Intent?) {
        val action = intent?.getStringExtra("action") ?: intent?.data?.host
        if (action == "sleep_verify" || action == "smart_sleep") {
            // User dismissed wake-up alarm or tapped notification - trigger smart sleep page
            startActivity(Intent(this, SmartSleepActivity::class.java))
        } else if (action == "wakeup_alarm") {
            val alarmIntent = Intent(this, SmartSleepActivity::class.java).apply {
                putExtra("action", "wakeup_alarm")
                putExtra("id", intent?.getStringExtra("id"))
            }
            startActivity(alarmIntent)
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    override fun onResume() {
        super.onResume()
        
        loadStatistics()
        startStatusUpdater()
        startNightlyFlowObservers()
        updateGreeting()
        updateTerminalLogVisibility()
        
        applyFeatureToggles()

        // Professional Auto-Update (7-day throttle)
        com.neubofy.reality.utils.UpdateManager.checkForUpdates(this, silent = true)
    }

    override fun onIdentityUpdated() {
        applyFeatureToggles()
    }

    private fun applyFeatureToggles() {
        // Features are now always visible on the home screen.
        // Pro access is strictly checked only when the user taps on these cards to open the pages.
        binding.cardReflection.visibility = android.view.View.VISIBLE
        binding.fabAiChat.visibility = android.view.View.VISIBLE
        binding.cardTapasyaHome.visibility = android.view.View.VISIBLE
    }

    // New Staggered Animation Logic
    private fun startStaggeredAnimation() {
        val viewsToAnimate = listOf(
            binding.cardReflection,
            binding.cardTapasyaHome,
            binding.cardNightlyHome,
            binding.cardBlockerMode,
            binding.cardUsageLimit,
            binding.blocklistCard,
            binding.cardAppLimits,
            binding.cardGroupLimits,
            binding.schedules,
            binding.cardBedtime
        )
        
        var delay = 0L
        for (view in viewsToAnimate) {
            view.alpha = 0f
            view.translationY = 50f
            
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
                
            delay += 50 // 50ms stagger
        }

        

    }

    private fun loadStatistics() {
        updateEmergencyUI()
        updateBedtimeUI()
        updateUsageLimitUI()
        loadReflectionCard()
    }
    
    private fun updateGreeting() {
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "")
        if (!name.isNullOrEmpty()) {
             binding.tvTitle.text = name
             binding.tvGreeting.text = "Hello,"
        }
    }

    private val scope = MainScope()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun logAppSignature() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                packageInfo.signatures
            }
            
            if (signatures == null) return

            for (signature in signatures) {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (b in digest) {
                    hexString.append(String.format("%02X:", b))
                }
                if (hexString.length > 0) {
                    hexString.setLength(hexString.length - 1)
                }
                val sha1 = hexString.toString()
                com.neubofy.reality.utils.TerminalLogger.log("APP SHA-1: $sha1")
                Log.d("RealityAuth", "SHA-1: $sha1")
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("Failed to get SHA-1: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        statusUpdaterJob?.cancel()
        nightlyStepsJob?.cancel()
        nightlySessionJob?.cancel()
    }

    private fun startStatusUpdater() {
        statusUpdaterJob?.cancel()
        statusUpdaterJob = scope.launch {
            var counter = 0
            while (isActive) {
                updateTapasyaStatus()
                if (counter % 4 == 0) {
                    updateBlockerStatus()
                    checkNightlyTimeWindow()
                }
                counter++
                delay(500)
            }
        }
    }

    private suspend fun updateBlockerStatus() {
        val status = withContext(Dispatchers.IO) {
            BlockerStatusManager(this@MainActivity).getCurrentStatus()
        }
        currentBlockerStatus = status
        
        withContext(Dispatchers.Main) {
             if (status.isActive) {
                  val remaining = status.endTime - com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this@MainActivity)
                  binding.tvBlockerCardTitle.text = status.title

                  // Breathing Glow Animation (Only start if not already animating)
                  if (binding.cardBlockerMode.tag != "animating") {
                      binding.cardBlockerMode.tag = "animating"
                      val colorFrom = getColor(R.color.accent_focus)
                      val colorTo = getColor(R.color.white)
                      val anim = android.animation.ObjectAnimator.ofArgb(
                          binding.cardBlockerMode, 
                          "strokeColor", 
                          colorFrom, 
                          colorTo
                      )
                      anim.duration = 1500
                      anim.repeatMode = android.animation.ValueAnimator.REVERSE
                      anim.repeatCount = android.animation.ValueAnimator.INFINITE
                      anim.start()
                  }
                  
                  // Check if Tapasya is running
                  val data = savedPreferencesLoader.getFocusModeData()
                  if (data.isTapasyaTriggered) {
                      binding.tvBlockerCardStatus.text = "Active until stopped"
                  } else if (remaining > 60_000) {
                      // Minute-only precision
                      val mins = (remaining / 60000) + 1 // Ceiling
                      binding.tvBlockerCardStatus.text = "Ends in ~ $mins min"
                  } else if (remaining > 0) {
                      binding.tvBlockerCardStatus.text = "Ends in < 1 min"
                  } else {
                      binding.tvBlockerCardStatus.text = "Completing..."
                  }
                  
                  // Button Text
                  if (status.type == BlockerType.MANUAL_BLOCKER) {
                      val data = savedPreferencesLoader.getFocusModeData()
                      if (data.isTapasyaTriggered) {
                          binding.blockerMode.text = "Tapasya Running"
                          binding.blockerMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.purple_500))
                      } else {
                          binding.blockerMode.text = "Stop Session"
                          binding.blockerMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.error_color))
                      }
                  } else {
                      binding.blockerMode.text = "Locked by Schedule"
                      binding.blockerMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.gray_dark))
                  }
             } else {
                  binding.tvBlockerCardTitle.text = "Blocker Session"
                  binding.tvBlockerCardStatus.text = "Ready to Start"
                  binding.blockerMode.text = "Start Blocker"
                  binding.blockerMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.teal_200))
                  
                  if (binding.cardBlockerMode.tag == "animating") {
                      binding.cardBlockerMode.tag = null
                      binding.cardBlockerMode.strokeColor = getColor(R.color.md_theme_outline) // Reset to default
                  }
             }
        }
    }

    private fun updateTapasyaStatus() {
        val featureManager = com.neubofy.reality.utils.FeatureManager(this)
        if (!featureManager.isTapasyaEnabled()) {
            binding.cardTapasyaHome.visibility = android.view.View.GONE
            return
        }
        binding.cardTapasyaHome.visibility = android.view.View.VISIBLE
        
        val state = com.neubofy.reality.services.TapasyaManager.getCurrentState(this)
        if (state.isSessionActive) {
            val statusText = if (state.isPaused) "Paused" else "Running"
            binding.tvTapasyaTime.text = "$statusText: ${com.neubofy.reality.services.TapasyaManager.formatTime(state.elapsedTimeMs)}"
            
            binding.btnTapasyaActionPrimary.text = if (state.isPaused) "Resume" else "Pause"
            binding.btnTapasyaActionSecondary.visibility = android.view.View.VISIBLE
            binding.btnTapasyaActionSecondary.text = "Stop"
        } else {
            binding.tvTapasyaTime.text = "Inactive"
            binding.btnTapasyaActionPrimary.text = "Start"
            binding.btnTapasyaActionSecondary.visibility = android.view.View.GONE
        }
    }

    private fun startNightlyFlowObservers() {
        nightlyStepsJob?.cancel()
        nightlySessionJob?.cancel()
        
        val date = java.time.LocalDate.now()
        val todayStr = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(this)
        
        nightlyStepsJob = scope.launch {
            db.nightlyDao().observeSteps(todayStr).collect { steps ->
                val stepMap = steps.associateBy { it.stepId }
                withContext(Dispatchers.Main) {
                    updateStepDot(binding.dotStep1, stepMap[1])
                    updateStepDot(binding.dotStep2, stepMap[2])
                    updateStepDot(binding.dotStep3, stepMap[3])
                    updateStepDot(binding.dotStep4, stepMap[4])
                    updateStepDot(binding.dotStep5, stepMap[5])
                    updateStepDot(binding.dotStep6, stepMap[6])
                }
            }
        }
        
        nightlySessionJob = scope.launch {
            db.nightlyDao().observeSession(todayStr).collect { session ->
                updateNightlyCardData(date)
            }
        }
    }

    private suspend fun updateNightlyCardData(date: java.time.LocalDate) {
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val protocolState = prefs.getInt("protocol_state", com.neubofy.reality.data.NightlyProtocolExecutor.STATE_IDLE)
        
        withContext(Dispatchers.Main) {
            binding.btnNightlyRun.isEnabled = true
            when (protocolState) {
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_IDLE -> binding.btnNightlyRun.text = "Start Nightly"
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_CREATING -> {
                    binding.btnNightlyRun.text = "Creating Diary..."
                    binding.btnNightlyRun.isEnabled = false
                }
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_PENDING_REFLECTION -> binding.btnNightlyRun.text = "Analyze Day"
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_ANALYZING -> {
                    binding.btnNightlyRun.text = "Analyzing..."
                    binding.btnNightlyRun.isEnabled = false
                }
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_PLANNING_READY -> binding.btnNightlyRun.text = "Create Plan"
                com.neubofy.reality.data.NightlyProtocolExecutor.STATE_COMPLETE -> {
                    binding.btnNightlyRun.text = "Review Complete"
                    binding.btnNightlyRun.isEnabled = false
                }
            }
        }
        
        val diaryId = withContext(Dispatchers.IO) {
            com.neubofy.reality.data.repository.NightlyRepository.getDiaryDocId(this@MainActivity, date)
        }
        val planId = withContext(Dispatchers.IO) {
            com.neubofy.reality.data.repository.NightlyRepository.getPlanDocId(this@MainActivity, date)
        }
        val reportId = withContext(Dispatchers.IO) {
            com.neubofy.reality.data.repository.NightlyRepository.getReportPdfId(this@MainActivity, date)
        }
        
        withContext(Dispatchers.Main) {
            if (diaryId != null || planId != null || reportId != null) {
                binding.nightlyDocsRow.visibility = android.view.View.VISIBLE
                binding.btnNightlyOpenDiary.visibility = if (diaryId != null) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnNightlyOpenPlan.visibility = if (planId != null) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnNightlyOpenReport.visibility = if (reportId != null) android.view.View.VISIBLE else android.view.View.GONE
                
                binding.btnNightlyOpenDiary.setOnClickListener {
                    val url = "https://docs.google.com/document/d/$diaryId/edit"
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
                binding.btnNightlyOpenPlan.setOnClickListener {
                    val intent = Intent(this@MainActivity, NightlyPlanActivity::class.java)
                    intent.putExtra("date", date)
                    startActivity(intent)
                }
                binding.btnNightlyOpenReport.setOnClickListener {
                    val intent = Intent(this@MainActivity, NightlyReportActivity::class.java)
                    intent.putExtra("date", date.toString())
                    startActivity(intent)
                }
            } else {
                binding.nightlyDocsRow.visibility = android.view.View.GONE
            }
        }
    }

    private fun checkNightlyTimeWindow() {
        // User requested to completely remove the Nightly card from the home page
        binding.cardNightlyHome.visibility = android.view.View.GONE
    }

    private fun updateStepDot(dotView: ImageView, step: com.neubofy.reality.data.db.NightlyStep?) {
        val status = step?.status ?: com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING
        val hasData = step != null && !step.resultJson.isNullOrEmpty()
        val realStatus = if (hasData) {
            com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED
        } else status
        
        when (realStatus) {
            com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED -> {
                dotView.setImageResource(R.drawable.baseline_check_circle_24)
                dotView.imageTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_light))
            }
            com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING -> {
                dotView.setImageResource(R.drawable.baseline_sync_24)
                dotView.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_focus))
            }
            com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR -> {
                dotView.setImageResource(R.drawable.baseline_error_24)
                dotView.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error_color))
            }
            else -> {
                dotView.setImageResource(R.drawable.baseline_radio_button_unchecked_24)
                dotView.imageTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.darker_gray))
            }
        }
    }

    private fun startNightlyProtocol() {
        if (!com.neubofy.reality.google.GoogleAuthManager.hasRequiredPermissions(this)) {
            Toast.makeText(this, "Google connection required. Open Nightly page to authorize.", Toast.LENGTH_LONG).show()
            return
        }
        val inputData = androidx.work.Data.Builder()
            .putString(com.neubofy.reality.workers.NightlyWorker.KEY_MODE, com.neubofy.reality.workers.NightlyWorker.MODE_CREATION)
            .build()
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_creation",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Toast.makeText(this, "Started Nightly Protocol", Toast.LENGTH_SHORT).show()
    }
    
    private fun analyzeDay() {
        val inputData = androidx.work.Data.Builder()
            .putString(com.neubofy.reality.workers.NightlyWorker.KEY_MODE, com.neubofy.reality.workers.NightlyWorker.MODE_ANALYSIS)
            .build()
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_analysis",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Toast.makeText(this, "Analyzing Day...", Toast.LENGTH_SHORT).show()
    }
    
    private fun processPlan() {
        val inputData = androidx.work.Data.Builder()
            .putString(com.neubofy.reality.workers.NightlyWorker.KEY_MODE, com.neubofy.reality.workers.NightlyWorker.MODE_PLANNING)
            .build()
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_planning",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Toast.makeText(this, "Creating Plan...", Toast.LENGTH_SHORT).show()
    }



    private fun setupActivityLaunchers() {
        selectFocusModeUnblockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    if (selectedApps != null) {
                        savedPreferencesLoader.saveFocusModeSelectedApps(selectedApps)
                        sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                    }
                }
            }

        addAutoFocusHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            }
    }

    private fun setupClickListeners() {
        // Emergency Mode
        binding.btnEmergencyClick.setOnClickListener {
            scope.launch { showEmergencyDialog() }
        }

        binding.btnEmergencySettings.setOnClickListener {
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val strictMode = loader.getStrictModeData()
            val isLocked = strictMode.isEnabled && strictMode.isEmergencyLocked

            val emergencyData = loader.getEmergencyData()
            val numberPicker = android.widget.NumberPicker(this).apply {
                minValue = 1
                maxValue = 15
                value = emergencyData.maxUses
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Emergency Quota")
                .setMessage(if (isLocked) "Settings are locked by Strict Mode. Current maximum is ${emergencyData.maxUses}." else "Set maximum emergency breaks allowed per day:")
                .setView(numberPicker)
                .setPositiveButton("Save") { _, _ ->
                    if (isLocked) {
                        Toast.makeText(this, "Cannot save: Emergency settings are locked by Strict Mode.", Toast.LENGTH_SHORT).show()
                    } else {
                        val diff = numberPicker.value - emergencyData.maxUses
                        emergencyData.maxUses = numberPicker.value
                        emergencyData.usesRemaining = (emergencyData.usesRemaining + diff).coerceIn(0, emergencyData.maxUses)
                        loader.saveEmergencyData(emergencyData)
                        updateEmergencyUI()
                        Toast.makeText(this, "Emergency quota updated.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // Blocklist
        binding.blocklistCard.setOnClickListener {
             startActivity(Intent(this, UnifiedBlocklistActivity::class.java), options.toBundle())
        }
        
        // Bedtime Mode
        binding.cardBedtime.setOnClickListener {
             startActivity(Intent(this, BedtimeActivity::class.java), options.toBundle())
        }
        
        // Schedules
        binding.schedules.setOnClickListener {
            val intent = Intent(this, ScheduleListActivity::class.java)
            startActivity(intent, options.toBundle())
        }
        
        // Menu Button - Show modern bottom sheet menu
        binding.btnInfo.setOnClickListener { view ->
            val featureManager = com.neubofy.reality.utils.FeatureManager(this)
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val sheetView = LayoutInflater.from(this).inflate(R.layout.layout_home_menu, null)
            val container = sheetView.findViewById<android.widget.LinearLayout>(R.id.ll_menu_container)
            
            data class MenuItem(val icon: Int, val title: String, val action: () -> Unit)
            
            val menuItems = mutableListOf<MenuItem>()
            
            menuItems.add(MenuItem(R.drawable.baseline_settings_24, "Settings") {
                startActivity(Intent(this, SettingsActivity::class.java), options.toBundle())
            })
            
            if (featureManager.isHealthConnectEnabled()) {
                menuItems.add(MenuItem(R.drawable.baseline_favorite_24, "Health Dashboard") {
                    startActivity(Intent(this, HealthDashboardActivity::class.java))
                })
            }
            menuItems.add(MenuItem(R.drawable.baseline_settings_24, "Appearance") {
                startActivity(Intent(this, AppearanceActivity::class.java))
            })
            if (featureManager.isRealityProEnabled()) {
                menuItems.add(MenuItem(R.drawable.baseline_restore_24, "Backup & Restore") {
                    startActivity(Intent(this, BackupRestoreActivity::class.java))
                })
                menuItems.add(MenuItem(R.drawable.baseline_bedtime_24, "Nightly Protocol") {
                    startActivity(Intent(this, NightlyActivity::class.java))
                })
            }
            menuItems.add(MenuItem(R.drawable.baseline_info_24, "About Reality") {
                startActivity(Intent(this, AboutActivity::class.java))
            })
            menuItems.add(MenuItem(R.drawable.baseline_schedule_24, "Sleep & Alarm") {
                startActivity(Intent(this, SmartSleepActivity::class.java))
            })
            menuItems.add(MenuItem(R.drawable.baseline_public_24, "Reality Website") {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://reality.neubofy.in"))
                startActivity(intent)
            })

            for (item in menuItems) {
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_home_menu, container, false)
                val ivIcon = itemView.findViewById<ImageView>(R.id.iv_icon)
                val tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
                
                ivIcon.setImageResource(item.icon)
                tvTitle.text = item.title
                
                itemView.setOnClickListener {
                    bottomSheetDialog.dismiss()
                    item.action()
                }
                
                container.addView(itemView)
            }
            
            bottomSheetDialog.setContentView(sheetView)
            bottomSheetDialog.show()
        }
        
        // Digital Life - Statistics with 7-Day Graphs
        binding.cardUsageLimit.setOnClickListener {
             startActivity(Intent(this, StatisticsActivity::class.java), options.toBundle())
        }
        
        // App Limits Card - Separate Page
        binding.cardAppLimits.setOnClickListener {
             val intent = Intent(this, AppLimitsActivity::class.java)
             startActivity(intent, options.toBundle())
        }
        
        // Group Limits Card - Separate Page
        binding.cardGroupLimits.setOnClickListener {
             val intent = Intent(this, GroupLimitsActivity::class.java)
             startActivity(intent, options.toBundle())
        }
        
        // Terminal Clear
        binding.btnTerminalClear.setOnClickListener {
            com.neubofy.reality.utils.TerminalLogger.clear()
        }

        // Blocker details button
        binding.btnBlockerDetails.setOnClickListener {
            startActivity(Intent(this, BlockerDetailsActivity::class.java))
        }

        // Tapasya Home Card Actions
        binding.btnTapasyaViewPage.setOnClickListener {
            startActivity(Intent(this, TapasyaActivity::class.java))
        }
        
        binding.btnTapasyaActionPrimary.setOnClickListener {
            val state = com.neubofy.reality.services.TapasyaManager.getCurrentState(this)
            if (state.isSessionActive) {
                if (state.isPaused) {
                    com.neubofy.reality.services.TapasyaManager.resumeSession(this)
                } else {
                    com.neubofy.reality.services.TapasyaManager.pauseSession(this)
                }
            } else {
                val tapasyaPrefs = getSharedPreferences("tapasya_prefs", MODE_PRIVATE)
                val targetMins = tapasyaPrefs.getInt("target_time_mins", 60)
                val pauseMins = tapasyaPrefs.getInt("pause_limit_mins", 15)
                com.neubofy.reality.services.TapasyaManager.startSession(this, "Tapasya", targetMins * 60 * 1000L, pauseMins * 60 * 1000L)
                Toast.makeText(this, "Tapasya Session Started", Toast.LENGTH_SHORT).show()
            }
            updateTapasyaStatus()
        }
        
        binding.btnTapasyaActionSecondary.setOnClickListener {
            val state = com.neubofy.reality.services.TapasyaManager.getCurrentState(this)
            if (state.isSessionActive) {
                com.neubofy.reality.services.TapasyaManager.stopSession(this, wasAutoStopped = false)
                Toast.makeText(this, "Tapasya Session Stopped", Toast.LENGTH_SHORT).show()
            }
            updateTapasyaStatus()
        }

        // Nightly Home Card Actions
        binding.btnNightlyViewPage.setOnClickListener {
            startActivity(Intent(this, NightlyActivity::class.java))
        }
        
        binding.btnNightlyRun.setOnClickListener {
            com.neubofy.reality.utils.NetworkUtils.checkInternetAndShowDialog(this) {
                val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
                val currentState = prefs.getInt("protocol_state", com.neubofy.reality.data.NightlyProtocolExecutor.STATE_IDLE)
                if (currentState == com.neubofy.reality.data.NightlyProtocolExecutor.STATE_PENDING_REFLECTION) {
                    analyzeDay()
                } else if (currentState == com.neubofy.reality.data.NightlyProtocolExecutor.STATE_PLANNING_READY) {
                    processPlan()
                } else {
                    startNightlyProtocol()
                }
            }
        }

        // Blocker Mode Button - FIXED
        binding.blockerMode.setOnClickListener {
            val status = currentBlockerStatus
            if (status != null && status.isActive) {
                // Stop Session
                if (status.type == BlockerType.MANUAL_BLOCKER) {
                    val data = savedPreferencesLoader.getFocusModeData()
                    
                    // Tapasya Guard
                    if (data.isTapasyaTriggered) {
                        Toast.makeText(this, "Active Tapasya session running. Stop it from the Tapasya Clock.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    data.isTurnedOn = false
                    savedPreferencesLoader.saveFocusModeData(data)
                    sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                    Toast.makeText(this, "Blocker Session Stopped", Toast.LENGTH_SHORT).show()
                    // Force UI Update
                    scope.launch { updateBlockerStatus() }
                } else {
                    Toast.makeText(this, "Cannot stop scheduled session", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Start Session
                StartFocusMode(savedPreferencesLoader) {
                    // Refresh triggered by dialog
                    scope.launch { updateBlockerStatus() }
                }.show(supportFragmentManager, "StartFocusMode")
            }
        }
        
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAiChatFab() {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val clickThreshold = 10
        var isDragging = false
        
        // GestureDetector for double-tap detection
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                com.neubofy.reality.utils.NetworkUtils.checkInternetAndShowDialog(this@MainActivity) {
                    // Single tap: Open AI Chat (with voice auto if enabled)
                    val prefs = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
                    val voiceAuto = prefs.getBoolean("widget_voice_auto", false)
                    
                    val intent = Intent(this@MainActivity, AIChatActivity::class.java).apply {
                        if (voiceAuto) putExtra("voice_auto", true)
                    }
                    startActivity(intent)
                }
                return true
            }
            
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                com.neubofy.reality.utils.NetworkUtils.checkInternetAndShowDialog(this@MainActivity) {
                    // Double tap: Open in PRO MODE
                    val intent = Intent(this@MainActivity, AIChatActivity::class.java).apply {
                        putExtra("extra_mode", "pro")
                    }
                    startActivity(intent)
                }
                return true
            }
        })
        
        binding.fabAiChat.setOnTouchListener { view, event ->
            // Always pass to gesture detector first
            val gestureHandled = gestureDetector.onTouchEvent(event)
            
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val diffX = kotlin.math.abs(event.rawX - startX)
                    val diffY = kotlin.math.abs(event.rawY - startY)
                    
                    // Only start dragging if moved beyond threshold
                    if (diffX > clickThreshold || diffY > clickThreshold) {
                        isDragging = true
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY
                        
                        // Bounds checking
                        val parent = view.parent as? android.view.View
                        if (parent != null) {
                            val maxX = parent.width - view.width.toFloat()
                            val maxY = parent.height - view.height.toFloat()
                            view.x = newX.coerceIn(0f, maxX)
                            view.y = newY.coerceIn(0f, maxY)
                        } else {
                            view.x = newX
                            view.y = newY
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Gesture detector handles taps, we just clean up here
                    true
                }
                else -> false
            }
        }
    }
    
    private suspend fun showEmergencyDialog() {
        if (!isBlockingActive()) {
            Toast.makeText(this, "Emergency Mode is only available when a blocking session is active.", Toast.LENGTH_LONG).show()
            return
        }
    
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        // Check if already in emergency session
        val secureNow = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)
        if (emergencyData.currentSessionEndTime > secureNow) {
            val remainingMins = (emergencyData.currentSessionEndTime - secureNow) / 60000
            Toast.makeText(this, "Emergency mode active for $remainingMins more minutes", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Reset daily if needed
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = emergencyData.lastResetDate
        val lastResetDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        calendar.timeInMillis = secureNow
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        if (currentDay != lastResetDay) {
            emergencyData.usesRemaining = emergencyData.maxUses
            emergencyData.lastResetDate = secureNow
            savedPreferencesLoader.saveEmergencyData(emergencyData)
        }
        
        // Check if uses remaining
        if (emergencyData.usesRemaining <= 0) {
            Toast.makeText(this, "No emergency breaks left today. Resets at midnight.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Show dialog
        val dialogBinding = com.neubofy.reality.databinding.DialogEmergencyModeBinding.inflate(layoutInflater)
        dialogBinding.tvRemaining.text = emergencyData.usesRemaining.toString()
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnActivate.setOnClickListener {
            // Activate emergency mode
            emergencyData.usesRemaining--
            emergencyData.currentSessionEndTime = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this@MainActivity) + Constants.EMERGENCY_DURATION_MS
            savedPreferencesLoader.saveEmergencyData(emergencyData)
            
            // Update blocker
            val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            Toast.makeText(this, "Emergency mode active for 5 minutes", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateEmergencyUI()
        }
        
        dialog.show()
    }
    
    private fun showRulesDialog() {
        val strictData = savedPreferencesLoader.getStrictModeData()
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        val sb = StringBuilder()
        
        sb.append("Current Status:\n\n")

        // Strict Mode
        val secureNow2 = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)
        sb.append("• Strict Mode: ")
        if (strictData.isEnabled) {
            sb.append("Active (${strictData.modeType})\n")
            if (strictData.timerEndTime > secureNow2) {
               val remaining = (strictData.timerEndTime - secureNow2) / 60000
               sb.append("  (Locked for ${remaining}m)\n")
            }
        } else {
            sb.append("Inactive\n")
        }
        sb.append("  (Anti-Uninstall: ${if (strictData.isAntiUninstallEnabled) "ON" else "OFF"})\n\n")
        
        // Emergency Access
        sb.append("• Emergency Access: ")
        sb.append("${emergencyData.usesRemaining} / ${emergencyData.maxUses} uses remaining today\n\n")
        
        sb.append("Quick Tips:\n")
        sb.append("• Maintenance Window: 00:00 - 00:10 daily (Settings unlocked)\n")
        sb.append("• Emergency Mode: Grants 5 minutes of access.\n")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Reality Status")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun updateEmergencyUI() {
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        // Reset daily if needed
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = emergencyData.lastResetDate
        val lastResetDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        calendar.timeInMillis = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        if (currentDay != lastResetDay) {
            emergencyData.usesRemaining = emergencyData.maxUses
            emergencyData.lastResetDate = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)
            savedPreferencesLoader.saveEmergencyData(emergencyData)
        }
        
        binding.tvEmergencyRemaining.text = "${emergencyData.usesRemaining} breaks remaining today"
    }
    
    private fun updateBedtimeUI() {
        val bedtimeData = savedPreferencesLoader.getBedtimeData()
        if (bedtimeData.isEnabled) {
            val startHour = bedtimeData.startTimeInMins / 60
            val startMin = bedtimeData.startTimeInMins % 60
            val endHour = bedtimeData.endTimeInMins / 60
            val endMin = bedtimeData.endTimeInMins % 60
            binding.tvBedtimeStatus.text = String.format("Active %02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin)
        } else {
            binding.tvBedtimeStatus.text = "Not configured"
        }
    }
    
    private fun updateUsageLimitUI() {
        scope.launch(Dispatchers.IO) {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
            val groups = db.appGroupDao().getAllGroups()
            val text = if (groups.isEmpty()) "Not configured" else "${groups.size} active groups"
            
            withContext(Dispatchers.Main) {
                binding.tvUsageStatus.text = text
                if (groups.isNotEmpty()) {
                     binding.tvUsageStatus.setTextColor(getColor(R.color.teal_200))
                } else {
                     binding.tvUsageStatus.setTextColor(getColor(R.color.gray_light))
                }
            }
        }
    }
    
    private fun loadReflectionCard() {
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
                
                // Get today's calendar events from device calendar (synced via CalendarRepository)
                val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(applicationContext)
                val todayEvents = calendarRepo.getEventsForToday()
                
                // Calculate total planned time from today's calendar events (in minutes)
                var totalPlannedMinutes = 0L
                for (event in todayEvents) {
                    val durationMs = event.endTime - event.startTime
                    totalPlannedMinutes += durationMs / 60000
                }
                
                // Get today's Tapasya sessions for effective study time
                val today = java.time.LocalDate.now()
                val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                val todaySessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                var totalEffectiveMinutes = 0L
                for (session in todaySessions) {
                    totalEffectiveMinutes += session.effectiveTimeMs / 60000
                }
                
                // Calculate progress percentage
                val progressPercent = if (totalPlannedMinutes > 0) {
                    ((totalEffectiveMinutes * 100) / totalPlannedMinutes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                
                // UNIFIED STATS: Use getLiveGamificationStats for single source of truth
                val liveStats = com.neubofy.reality.utils.XPManager.getLiveGamificationStats(applicationContext)
                
                withContext(Dispatchers.Main) {
                    // Update XP/streak/level
                    binding.tvTotalXp.text = liveStats.totalXP.toString()
                    binding.tvTodayXp.text = if (liveStats.todayXP >= 0) "+${liveStats.todayXP}" else "${liveStats.todayXP}"
                    binding.tvProjectedXp.text = liveStats.projectedXP.toString()
                    binding.tvStreak.text = liveStats.streak.toString()
                    binding.tvLevel.text = liveStats.level.toString()
                    
                    // Update study time progress (API/Webhook calendar sync is active)
                    binding.tvStudyProgress.text = "${totalEffectiveMinutes} / ${totalPlannedMinutes} min"
                    binding.progressStudyTime.progress = progressPercent

                    binding.btnViewProgress.text = "📊 View Progress Details"
                    binding.btnViewProgress.setOnClickListener {
                        startActivity(Intent(this@MainActivity, ReflectionDetailActivity::class.java))
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("Reflection card error: ${e.message}")
            }
        }
    }

    private fun checkPermissions() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, com.neubofy.reality.receivers.AdminLockReceiver::class.java)
        isDeviceAdminOn = dpm.isAdminActive(adminComponent)

        val antiUninstallPrefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = antiUninstallPrefs.getBoolean("is_anti_uninstall_on", false)
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(this, service)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName.flattenToString()) == true
    }

    private fun makeDeviceAdminPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Device Admin Required")
            .setMessage("Reality needs Device Admin permission for Anti-Uninstall to work properly.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, com.neubofy.reality.receivers.AdminLockReceiver::class.java))
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Reality Anti-Uninstall Protection")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFocusModeShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcut = ShortcutInfoCompat.Builder(this, "focus_mode_shortcut")
                .setShortLabel("Focus Mode")
                .setIcon(IconCompat.createWithResource(this, R.drawable.baseline_start_24))
                .setIntent(Intent(this, ShortcutActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        }
    }

    private fun sendRefreshRequest(action: String) {
        val intent = Intent(action)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun openAccessibilityServiceScreen(cls: Class<*>) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, cls)
            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun makeRemoveAntiUninstallDialog() {
        val data = savedPreferencesLoader.getStrictModeData()
        
        if (!data.isEnabled) {
             Toast.makeText(this, "Strict Mode is not active", Toast.LENGTH_SHORT).show()
             return
        }
        
        if (data.modeType == Constants.StrictModeData.MODE_TIMER) {
            val remaining = data.timerEndTime - com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)
            if (remaining > 0) {
                 val hours = remaining / (1000 * 60 * 60)
                 val mins = (remaining % (1000 * 60 * 60)) / (1000 * 60)
                 MaterialAlertDialogBuilder(this)
                     .setTitle("Strict Mode Active")
                     .setMessage("Locked for $hours hours $mins minutes.")
                     .setPositiveButton("OK", null)
                     .show()
            } else {
                 // Expired - Turn Off
                 data.isEnabled = false
                 savedPreferencesLoader.saveStrictModeData(data)
                 sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                 Snackbar.make(binding.root, "Strict Mode Expired & Removed", Snackbar.LENGTH_SHORT).show()
                 checkPermissions()
            }
        } else if (data.modeType == Constants.StrictModeData.MODE_PASSWORD) {
            val dialogBinding = DialogRemoveAntiUninstallBinding.inflate(layoutInflater)
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove Strict Mode")
                .setView(dialogBinding.root)
                .setPositiveButton("Unlock") { _, _ ->
                    val entered = dialogBinding.password.text.toString()
                    // Hash the entered password and compare to stored hash
                    val enteredHash = hashPassword(entered)
                    if (enteredHash == data.passwordHash) {
                        data.isEnabled = false
                        savedPreferencesLoader.saveStrictModeData(data)
                        sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                        Snackbar.make(binding.root, "Strict Mode Removed", Snackbar.LENGTH_SHORT).show()
                        checkPermissions()
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("Forgot Password") { _, _ ->
                     // Recovery: Switch to 24h Timer
                     data.modeType = Constants.StrictModeData.MODE_TIMER
                     data.timerEndTime = com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this@MainActivity) + (24 * 60 * 60 * 1000L)
                     savedPreferencesLoader.saveStrictModeData(data)
                     
                     MaterialAlertDialogBuilder(this)
                         .setTitle("Recovery Mode")
                         .setMessage("Strict Mode switched to 24-Hour Timer. You can unlock after 24 hours.")
                         .setPositiveButton("OK", null)
                         .show()
                     sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private suspend fun isBlockingActive(): Boolean {
        // 1. Check Main Blocking Modes (Focus, Schedule, Calendar, Bedtime)
        // using BlockerStatusManager which simplifies and includes Calendar (Fixing missing check)
        val blockerStatus = com.neubofy.reality.utils.BlockerStatusManager(this).getCurrentStatus()
        if (blockerStatus.isActive) return true
        
        // 2. Check DB Limits / Groups (Time-based active periods)
        val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
        val groups = db.appGroupDao().getAllGroups()
        groups.forEach { 
             if (com.neubofy.reality.utils.TimeTools.isActivePeriod(it.activePeriodsJson)) return true
        }
        val limits = db.appLimitDao().getAllLimits()
        limits.forEach {
             if (com.neubofy.reality.utils.TimeTools.isActivePeriod(it.activePeriodsJson)) return true
        }
        
        return false
    }


    

    private fun updateThemeVisuals() {
        val cards = listOf(
            binding.cardEmergency,
            binding.cardTapasyaHome,
            binding.cardNightlyHome,
            binding.cardBlockerMode,
            binding.cardUsageLimit,
            binding.blocklistCard,
            binding.cardAppLimits,
            binding.cardGroupLimits,
            binding.schedules,
            binding.cardBedtime
        )
        cards.forEach { ThemeManager.applyCardAppearance(it) }
        
        // Also update background pattern alpha if needed? No, handled by XML usually.
        // But if AMOLED, pattern view should be hidden?
        val patternView = binding.root.getChildAt(1) // Assuming 2nd view is pattern
        if (ThemeManager.isAmoledMode(this) && isSystemNight()) {
            patternView.visibility = View.GONE
        } else {
            // Restore visibility based on Pattern preference?
            val pattern = ThemeManager.getBackgroundPattern(this)
            patternView.visibility = if (pattern == ThemeManager.BackgroundPattern.NONE) View.GONE else View.VISIBLE
            // Update drawable if needed
            if (pattern != ThemeManager.BackgroundPattern.NONE) {
                 patternView.setBackgroundResource(pattern.drawableResId)
            }
        }
    }
    
    private fun isSystemNight(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun hashPassword(password: String): String {
        return com.neubofy.reality.utils.SecurityUtils.hashPassword(password)
    }
    
    private fun updateTerminalLogVisibility() {
        val appPrefs = getSharedPreferences("reality_prefs", MODE_PRIVATE)
        val showTerminalLog = appPrefs.getBoolean("show_terminal_log", false) // Default OFF
        
        // Find the terminal log card parent (it's inside ScrollView > LinearLayout)
        // The terminal card is at the bottom of the layout
        binding.tvTerminalLog.parent?.parent?.parent?.let { terminalCard ->
            if (terminalCard is android.view.View) {
                terminalCard.visibility = if (showTerminalLog) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

}
