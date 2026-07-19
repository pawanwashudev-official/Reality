package com.neubofy.reality.services

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.provider.Settings
import com.neubofy.reality.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import com.neubofy.reality.Constants
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.utils.getCurrentKeyboardPackageName
import com.neubofy.reality.utils.getDefaultLauncherPackageName

class AppBlockerService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "com.neubofy.reality.refresh.focus_mode"
        const val INTENT_ACTION_REFRESH_ANTI_UNINSTALL = "com.neubofy.reality.refresh.anti_uninstall"
        const val INTENT_ACTION_START_LEARNING = "com.neubofy.reality.start.learning"
        const val INTENT_ACTION_STOP_LEARNING = "com.neubofy.reality.stop.learning"
        const val EXTRA_PAGE_TYPE = "page_type"
        
        // Custom page learning
        const val INTENT_ACTION_START_CUSTOM_PAGE_LEARNING = "com.neubofy.reality.start.custom_page_learning"
    }

    private var warningConfig = Constants.WarningData()
    val blocker = RealityBlocker()
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var lastPackage = ""
    
    // Timers
    private var lastUrlCheckTime = 0L
    
    // Browser Watchdog Manager
    private val browserWatchdog by lazy { BrowserWatchdog(this, serviceScope, handler) }
    
    // System State Manager (DND, Bedtime, Sleep Sync)
    private val systemStateManager by lazy { SystemStateManager(this) }
    
    // Settings Protection Manager (SettingsBox checks, Penalty Overlay)
    private val settingsProtectionManager by lazy { SettingsProtectionManager(this, serviceScope, handler) }
    
    // Threading
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    
    // Anti-bypass tracking
    @Volatile private var lastBlockedPackage: String = ""
    @Volatile private var lastBlockTime: Long = 0L
    
    // Settings Page Learning Manager
    private val settingsLearningManager by lazy { SettingsLearningManager(this) }
    
    // === SETTINGS PAGE LEARNING ===
    
    var lastWindowClassName: String = ""
    var lastWindowPackage: String = ""
    private var lastSettingsContentHash: String = ""  // Detects actual page change vs scroll
    private var lastContentChangedCheck: Long = 0L     // Debounce for TYPE_WINDOW_CONTENT_CHANGED
    @Volatile var learnedSettingsPages = Constants.LearnedSettingsPages()


    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_FOCUS_MODE -> {
                    refreshSettings()
                    // Reload Strict Mode data efficiently on background thread
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            blocker.strictModeData = savedPreferencesLoader.getStrictModeData()
                            learnedSettingsPages = savedPreferencesLoader.getLearnedSettingsPages()
                            // Rebuild SettingsBox with new strict mode settings
                            com.neubofy.reality.utils.SettingsBox.rebuildBox(applicationContext)
                            com.neubofy.reality.utils.TerminalLogger.log("STRICT: Reloaded settings - enabled=${blocker.strictModeData.isEnabled}")
                        } catch (e: Exception) {
                            com.neubofy.reality.utils.TerminalLogger.log("STRICT: Error reloading - ${e.message}")
                        }
                    }
                }
                INTENT_ACTION_START_LEARNING -> {
                    val pageTypeName = intent.getStringExtra(EXTRA_PAGE_TYPE)
                    if (pageTypeName != null) {
                        try {
                            settingsLearningManager.currentLearningPageType = Constants.PageType.valueOf(pageTypeName)
                            settingsLearningManager.isLearningMode = true
                            settingsLearningManager.showLearnConfirmOverlay()
                            com.neubofy.reality.utils.TerminalLogger.log("LEARN: Started for $pageTypeName")
                        } catch (e: Exception) {
                            com.neubofy.reality.utils.TerminalLogger.log("LEARN: Invalid page type - $pageTypeName")
                        }
                    }
                }
                INTENT_ACTION_START_CUSTOM_PAGE_LEARNING -> {
                    val customName = intent.getStringExtra("custom_name") ?: "Custom Page"
                    settingsLearningManager.currentCustomPageName = customName
                    settingsLearningManager.currentLearningPageType = null
                    settingsLearningManager.isLearningMode = true
                    settingsLearningManager.isCustomPageLearning = true
                    settingsLearningManager.showLearnConfirmOverlay()
                    com.neubofy.reality.utils.TerminalLogger.log("LEARN CUSTOM PAGE: Started - $customName")
                }
                INTENT_ACTION_STOP_LEARNING -> {
                    settingsLearningManager.stopLearning()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    browserWatchdog.stopBrowserCheckTimer() // Save battery when screen off
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (isBlockingActive) {
                        checkCurrentWindow()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    
                    // Full settings refresh on unlock - ensures schedules are loaded and evaluated immediately
                    refreshSettings()
                    com.neubofy.reality.utils.SmartScheduleManager.scheduleNextTransition(applicationContext)
                    
                    // Resume checking if needed
                    if (browserWatchdog.isWebsiteBlockActive()) {
                         browserWatchdog.startBrowserCheckTimer()
                    }
                }
            }
        }
    }
    
    private var isScreenOn = true
    private var isBlockingActive = true
    
    // Minute-tick heartbeat receiver for bedtime/schedule enforcement
    // Uses ACTION_TIME_TICK (fires every minute, OS-managed, zero battery cost)
    // Only does an O(1) HashMap lookup — does NOT intercept scrolls/taps
    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK && isScreenOn && isBlockingActive) {
                checkCurrentWindow()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScreenOn) return
        if (event == null) return
        
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        
        // 1. Strict Event Type Filtering
        // Drop high-frequency events we don't care about immediately.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return
        }
        
        val isSettingsPackage = packageName.contains("settings") || packageName.contains("securitycenter")
        val isBrowser = com.neubofy.reality.utils.UrlDetector.isBrowser(packageName)
        
        // 2. High-Frequency Event Dropping for Non-Target Apps
        // If it's not a settings app and not a browser, we only need to monitor app switching (TYPE_WINDOW_STATE_CHANGED).
        // This drops 99% of scrolling/typing events in regular apps (WhatsApp, Instagram, etc.) with zero overhead.
        if (!settingsLearningManager.isLearningMode && !isSettingsPackage && !isBrowser) {
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return
            }
        }
        
        // Capture window class for learning mode and strict mode
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            
            // === SMART SETTINGS PROTECTION ===
            // Only wake protection system when:
            // 1. It's a settings package
            // 2. Page just changed (not same page)
            // 3. Strict mode protection is active (early exit if not!)
            // 4. Not in learning mode
            val isNewPage = className != lastWindowClassName || packageName != lastWindowPackage
            
            lastWindowClassName = className
            lastWindowPackage = packageName
            
            // === EARLY EXIT: Skip if no protection is active ===
            // This is the key optimization - don't do ANY work unless protection is ON
            if (isSettingsPackage && isNewPage && !settingsLearningManager.isLearningMode && 
                com.neubofy.reality.utils.SettingsBox.isAnyProtectionActive()) {
                lastSettingsContentHash = "" // Reset content hash for new page
                settingsProtectionManager.scheduleSettingsProtectionCheck(className, packageName)
            }
            
            // Log only Settings-related packages (only if protection active)
            if (isSettingsPackage && com.neubofy.reality.utils.SettingsBox.isAnyProtectionActive()) {
                com.neubofy.reality.utils.TerminalLogger.log("SETTINGS: ${className.substringAfterLast(".")}")
            }
            
            // Update learning overlay when user navigates
            if (settingsLearningManager.isLearningMode && settingsLearningManager.learnOverlay != null) {
                handler.post { settingsLearningManager.updateLearnOverlayText() }
            }
        }
        
        // === CONTENT CHANGED: Catches SubSettings→SubSettings navigation ===
        // When navigating between pages that share the same class (SubSettings),
        // TYPE_WINDOW_STATE_CHANGED doesn't fire with a new className.
        // But TYPE_WINDOW_CONTENT_CHANGED fires when the content updates.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (isSettingsPackage && !settingsLearningManager.isLearningMode && 
                com.neubofy.reality.utils.SettingsBox.isAnyProtectionActive()) {
                // Debounce: max once per 500ms
                val now = System.currentTimeMillis()
                if (now - lastContentChangedCheck > 500) {
                    lastContentChangedCheck = now
                    // Content hash: only re-check if page content actually changed (not scroll/click)
                    val currentHash = settingsProtectionManager.getQuickContentHash()
                    if (currentHash.isNotEmpty() && currentHash != lastSettingsContentHash) {
                        lastSettingsContentHash = currentHash
                        com.neubofy.reality.utils.TerminalLogger.log("SETTINGS: Content changed (SubSettings navigation detected)")
                        settingsProtectionManager.scheduleSettingsProtectionCheck(lastWindowClassName, lastWindowPackage, delay = 100)
                    }
                }
            }
        }
        
        // === TOGGLE GUARDIAN REMOVED ===
        // Button interception logic removed as requested.
        // We now rely on robust page blocking.

        
        // PERIODIC CHECK RESTORED
        // This is CRITICAL for scheduled/auto focus to detect when a session starts.
        // Manual focus works via intent, but scheduled focus needs this time-based check.
        // Interval: 60 seconds (was 120 seconds before).
        // SMART BATTERY OPTIMIZATION:
        // Polling removed. We now rely on 'Screen On' broadcasts and 'HeartbeatWorker'.
        // 1. Browser Special Handling (Watchdog Trigger)
        
        // Check if any blocking mode is active (Focus/Schedule/Calendar/Bedtime)
        val isAnyBlockingModeActive = browserWatchdog.isWebsiteBlockActive()
        
        if (isBrowser) {
             // Browser is active - start/keep watchdog running if blocking is needed
             if (isAnyBlockingModeActive) {
                  browserWatchdog.startBrowserCheckTimer()
                  browserWatchdog.resetWatchdogRampUp()
                  
                  // FIX: IMMEDIATE URL check on any browser event (catches URL/tab changes)
                  // Check on content changes, text changes, focus changes, window state changes
                  if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                      eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                      eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                      eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                      val now = System.currentTimeMillis()
                      if (now - lastUrlCheckTime > 300) {
                          lastUrlCheckTime = now
                          val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                          if (rootNode != null) {
                              serviceScope.launch {
                                  try {
                                      browserWatchdog.checkUrl(packageName, rootNode)
                                  } catch (e: Exception) {
                                     com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
                                  } finally {
                                     try { rootNode.recycle() } catch (e: Exception) {}
                                  }
                              }
                          }
                      }
                  }
             }
        } else {
             // Left the browser - stop watchdog immediately to conserve battery
             browserWatchdog.stopBrowserCheckTimer()
        }
        
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        
        // 0. EMERGENCY MODE BYPASS (Highest Priority)
        // If Emergency Mode is active, bypass ALL app blocking checks.
        // Uses SecureTimeProvider for tamper-proof duration counting (works offline via cached offset + monotonic clock)
        if (com.neubofy.reality.utils.BlockCache.emergencySessionEndTime > com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this)) {
             return
        }
        
        // Session Block Check (Fast path)
        
        // NOTE: Removed 30-second polling for battery optimization.
        // Box rebuilds via: BlockCacheWorker (15 min), Screen ON, Settings changed.
        // The O(1) Box check below runs on EVERY app switch.

        
        // Anti-bypass for apps (PiP & Rapid Switch Defense)
        if (packageName == lastBlockedPackage && System.currentTimeMillis() - lastBlockTime < 3000) {
            // Prevent System UI crash by rate-limiting to 1 second
            if (System.currentTimeMillis() - lastBlockTime < 1000) return
            
            // Best effort kill to remove PiP or background tasks
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(packageName)
            } catch (e: Exception) {}

            val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(packageName)
            if (shouldBlock) {
                handleBlock(packageName, reasons.joinToString(", "))
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        lastPackage = packageName
        
        // CONDITIONAL USAGE TRACKER
        // Only run the 15-minute polling worker if the user is actively using an app that has a usage limit.
        if (com.neubofy.reality.utils.BlockCache.monitoredApps.contains(packageName)) {
            startUsageTracker()
        } else {
            stopUsageTracker()
        }
        
        // === THE BOX CHECK - SINGLE SOURCE OF TRUTH ===
        // No fallback. No calculations. Just check the box.
        val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(packageName)
        
        if (shouldBlock) {
            val reason = reasons.joinToString(", ")
            if (packageName == lastBlockedPackage && (System.currentTimeMillis() - lastBlockTime) < 60000) {
                 handleBlock(packageName, reason)
                 return
            }
            if (packageName == currentWarnedPackage) return 

            // Show warning overlay before blocking
            if (android.provider.Settings.canDrawOverlays(this)) {
                currentWarnedPackage = packageName
                showWarningOverlay(5) 
                
                warningRunnable = Runnable {
                    handleBlock(packageName, reason)
                    removeWarningOverlay()
                    currentWarnedPackage = null
                }
                handler.postDelayed(warningRunnable!!, 5000)
            } else {
                handleBlock(packageName, reason)
            }

        } else {
            if (currentWarnedPackage != null && packageName != "com.android.systemui") {
                cancelWarning()
            }
        }
    }
    
    private var currentWarnedPackage: String? = null
    private var warningOverlay: android.widget.TextView? = null
    private var warningRunnable: Runnable? = null

    private fun cancelWarning() {
        warningRunnable?.let { handler.removeCallbacks(it) }
        removeWarningOverlay()
        currentWarnedPackage = null
    }

    private fun showWarningOverlay(seconds: Int) {
        try {
            if (warningOverlay != null) return // Already showing

            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.os.Build.VERSION.SDK_INT.let { if (it >= 26) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE },
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            params.y = 100

            warningOverlay = android.widget.TextView(this).apply {
                text = "Limit Reached! Blocking in ${seconds}s..."
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(32, 16, 32, 16)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#CCFF4444")) // Semi-transparent Red
                    cornerRadius = 16f
                }
                elevation = 10f
            }

            windowManager.addView(warningOverlay, params)
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
        }
    }

    private fun removeWarningOverlay() {
        try {
            warningOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            warningOverlay = null
        } catch (e: Exception) {
            warningOverlay = null // Reset anyway
        }
    }




    // toggleGrayscale REMOVED - feature requires ADB

    private fun startUsageTracker() {
        try {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.neubofy.reality.workers.UsageTrackerWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "UsageTrackerWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("TRACKER START ERROR: ${e.message}")
        }
    }

    private fun stopUsageTracker() {
        try {
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("UsageTrackerWorker")
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("TRACKER STOP ERROR: ${e.message}")
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (packageName == getPackageName()) return true
            return isSystem
        } catch (e: Exception) { return false }
    }
    

    
    /**
     * Helper: Check if any node in hierarchy contains specified keywords
     */
    private fun hasTextInHierarchy(node: android.view.accessibility.AccessibilityNodeInfo, keywords: List<String>): Boolean {
        try {
            val text = (node.text?.toString() ?: "").lowercase()
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            
            if (keywords.any { text.contains(it) || desc.contains(it) }) {
                return true
            }
            
            for (i in 0 until node.childCount.coerceAtMost(10)) {
                val child = node.getChild(i) ?: continue
                val found = hasTextInHierarchy(child, keywords)
                try { child.recycle() } catch (_: Exception) {}
                if (found) return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    // === STRICT MODE INSTANT BLOCKING ===
    private var strictOverlay: android.widget.TextView? = null
    private var lastStrictBlockTime = 0L
    
    /**
     * Extracts a quick hash of the page's top-level text content.
     * Used to detect actual page changes (different content) vs scroll/click (same content).
     * Only reads the first 8 text nodes for speed.
     */


    
    private fun showStrictLockOverlay(reason: String) {
        if (strictOverlay != null) return
        try {
            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.os.Build.VERSION.SDK_INT.let { if (it >= 26) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE },
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            
            strictOverlay = android.widget.TextView(this).apply {
                text = "$reason\n\nStrict Mode Active"
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#E6000000")) // 90% Black
                elevation = 100f
            }
            windowManager.addView(strictOverlay, params)
        } catch (e: Exception) {}
    }
    
    private fun removeStrictOverlay() {
        try {
            strictOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            strictOverlay = null
        } catch (e: Exception) { strictOverlay = null }
    }


    


    


    // Updated signature to control session persistence
    private fun handleBlock(packageName: String, reason: String? = null, addToSession: Boolean = true) {
        com.neubofy.reality.utils.TerminalLogger.log("ACTION: Blocking $packageName. Reason: ${reason ?: "N/A"}")
        
        // Event-driven Sleep Mode Enforcement (Android 15+ Fix on bypass attempt)
        systemStateManager.forceSleepModeEnforcement()

        // Kill Process for stronger block
        try {
             val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
             am.killBackgroundProcesses(packageName)
        } catch(e: Exception) {}
        
        lastBlockedPackage = packageName
        lastBlockTime = System.currentTimeMillis()
        
        // Launch Block Activity
        try {
            performGlobalAction(GLOBAL_ACTION_HOME) // Force app to background
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                     putExtra("pkg", packageName)
                     putExtra("reason", reason)
                }
                try {
                    startActivity(intent)
                } catch(e: Exception) {}
            }, 300)
        } catch (e: Exception) {
            // Fallback if activity fails
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
        
        lastPackage = ""
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Initialize Dynamic Browser Detection
        com.neubofy.reality.utils.UrlDetector.init(this)
        
        // Load BlockCache from disk (survives RAM cleanup)
        com.neubofy.reality.utils.BlockCache.loadFromDisk(this)
        
        // Initialize SettingsBox for military-grade settings protection
        com.neubofy.reality.utils.SettingsBox.rebuildBox(this)
        
        // Load learned settings pages
        learnedSettingsPages = savedPreferencesLoader.getLearnedSettingsPages()
        
        refreshSettings()
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            addAction(INTENT_ACTION_START_LEARNING)
            addAction(INTENT_ACTION_STOP_LEARNING)
            addAction(INTENT_ACTION_START_CUSTOM_PAGE_LEARNING)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            androidx.core.content.ContextCompat.registerReceiver(this, refreshReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        
        // Register minute-tick heartbeat for bedtime/schedule enforcement
        // This catches the case where a blocking mode activates while the user is already in a blocked app
        // (e.g., scrolling YouTube Shorts when Bedtime starts). Zero battery cost — OS already fires this.
        val tickFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(timeTickReceiver, tickFilter)
        
        // Log service startup
        com.neubofy.reality.utils.TerminalLogger.log("SERVICE: Accessibility Service Started")
        com.neubofy.reality.utils.TerminalLogger.log("STATUS: Blocking=$isBlockingActive, StrictMode=${blocker.strictModeData?.isEnabled ?: false}")
    }

    private fun refreshSettings() {
        // Universal Whitelist - These apps should NEVER be blocked
        val whitelist = hashSetOf<String>()
        whitelist.add(packageName) // Reality app
        whitelist.add("com.android.settings") // Settings app
        whitelist.add("com.android.systemui") // System UI
        whitelist.add("com.google.android.packageinstaller") // Package installer
        whitelist.add("com.android.packageinstaller") // Package installer (AOSP)
        whitelist.add("com.android.permissioncontroller") // Permission controller
        com.neubofy.reality.utils.getDefaultLauncherPackageName(packageManager)?.let { whitelist.add(it) }
        com.neubofy.reality.utils.getCurrentKeyboardPackageName(this)?.let { whitelist.add(it) }
        blocker.whitelistedPackages = whitelist
        
        warningConfig = savedPreferencesLoader.loadAppBlockerWarningInfo()

        blocker.usageLimitData = savedPreferencesLoader.getUsageLimitData()
        
        try {
            blocker.bedtimeData = savedPreferencesLoader.getBedtimeData()
        } catch (e: Exception) {
            blocker.bedtimeData = com.neubofy.reality.Constants.BedtimeData()
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: Bedtime data load failed")
        }
        
        try {
            blocker.emergencyData = savedPreferencesLoader.getEmergencyData()
            com.neubofy.reality.utils.BlockCache.emergencySessionEndTime = blocker.emergencyData.currentSessionEndTime
        } catch (e: Exception) {
            blocker.emergencyData = com.neubofy.reality.Constants.EmergencyModeData()
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: Emergency data load failed")
        }
        
        try {
            blocker.strictModeData = savedPreferencesLoader.getStrictModeData()
            com.neubofy.reality.utils.TerminalLogger.log("STRICT: Loaded - enabled=${blocker.strictModeData.isEnabled}, mode=${blocker.strictModeData.modeType}")
        } catch (e: Exception) {
            blocker.strictModeData = com.neubofy.reality.Constants.StrictModeData()
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: Strict mode data load failed - ${e.message}")
        }
        
        // === REBUILD THE BOX IMMEDIATELY ===
        serviceScope.launch(Dispatchers.IO) {
            try {
                com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val wasActive = isBlockingActive
                    
                    isBlockingActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive ||
                                       com.neubofy.reality.utils.BlockCache.getBlockedCount() > 0
                    
                    val isAnyModeActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
                    
                    // === NOTIFICATION MANAGEMENT ===
                    if (isAnyModeActive) {
                        // Focus/Schedule/Bedtime is running - show notification timer
                        // Timer is already managed elsewhere, but we ensure it's running
                    } else {
                        // No blocking mode active - stop notification timer
                        com.neubofy.reality.utils.NotificationTimerManager(this@AppBlockerService).stopTimer()
                    }
                    
                    // === DND & SLEEP MODE SYNC (Delegated to SystemStateManager) ===
                    systemStateManager.syncDndState(isAnyModeActive)
                    systemStateManager.syncSleepModeState()
                    
                    com.neubofy.reality.utils.TerminalLogger.log("BOX SYNC: Active=$isBlockingActive, DND=${com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive}")
                    
                    // === IMMEDIATE FOREGROUND CHECK ===
                    // After box rebuild, check if the currently active app is now blocked.
                    // This fixes the bypass where user is already inside a blocked app (e.g. scrolling 
                    // YouTube Shorts) when a mode activates (e.g. Bedtime starts at 10 PM).
                    // Without this, the block only triggers on the next TYPE_WINDOW_STATE_CHANGED event.
                    if (isBlockingActive) {
                        checkCurrentWindow()
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("REFRESH ERROR: ${e.message}")
            }
        }
        
        // Load focus mode for website blocking and other legacy checks
        val data = savedPreferencesLoader.getFocusModeData()
        blocker.focusModeData = RealityBlocker.FocusModeData(
            isTurnedOn = data.isTurnedOn,
            endTime = data.endTime,
            selectedApps = savedPreferencesLoader.getFocusModeSelectedApps().toHashSet(),
            modeType = data.modeType,
            blockedWebsites = data.blockedWebsites
        )
                           
        com.neubofy.reality.utils.TerminalLogger.log("KERNEL: Config Loaded. BlockCache will be rebuilt.")
        
        // Force check the current window
        checkCurrentWindow()
    }
    
    private fun checkCurrentWindow() {
        // Only run if blocking is active
        if (!isBlockingActive) return
        
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: return
            
            // Avoid self-blocking system apps
            if (pkg == packageName || pkg == "com.android.systemui") return
            
            // === CHECK THE BOX - SINGLE SOURCE OF TRUTH ===
            val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(pkg)
            if (shouldBlock) {
                val reason = reasons.joinToString(", ")
                com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Caught $pkg")
                handleBlock(pkg, reason)
            }
        } catch (e: Exception) {
            // Ignore - accessibility node might be invalid
        }
    }
    


    private fun setUpForcedRefreshChecker(packageName: String, endMillis: Long) {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = Runnable {
            if (rootInActiveWindow?.packageName == packageName) {
                handleBlock(packageName, "App time limit expired")
            }
        }
        handler.postAtTime(updateRunnable!!, endMillis)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            serviceScope.coroutineContext.cancelChildren()
        } catch (e: Exception) {}
        try {
            removeWarningOverlay()
        } catch (e: Exception) {}
        try {
            removeStrictOverlay()
        } catch (e: Exception) {}
        try {
            settingsLearningManager.removeLearnOverlay()
        } catch (e: Exception) {}
        try {
            settingsProtectionManager.removePenaltyOverlay()
        } catch (e: Exception) {}
        try {
            browserWatchdog.stopBrowserCheckTimer()
        } catch (e: Exception) {}
        try { unregisterReceiver(timeTickReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(refreshReceiver) } catch (e: Exception) {}
    }

    override fun onInterrupt() {}

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }


}
