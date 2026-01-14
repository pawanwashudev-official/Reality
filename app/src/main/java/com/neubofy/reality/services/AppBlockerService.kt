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
import com.neubofy.reality.data.ScheduleManager
import com.neubofy.reality.ui.overlay.ReminderOverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val blocker = RealityBlocker()
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var lastPackage = ""
    
    // === THE BOX (Cached Blocking Strategy) ===
    private data class BoxStrategy(
        val isActive: Boolean = false,
        val isAllowListMode: Boolean = false, // If true, we BLOCK everything EXCEPT packages
        val packages: Set<String> = emptySet(), // The "Box" contents
        val limitBlockedPackages: Set<String> = emptySet() // Specific apps blocked by usage limits
    )
    private var boxStrategy = BoxStrategy()
    
    // Expanded Whitelist (Hardcoded Safety Net) - COPIED from RealityBlocker for fast path
    private val expandedWhitelist = setOf(
        "com.android.calculator2", "com.google.android.calculator",
        "com.android.dialer", "com.google.android.dialer",
        "com.android.contacts", "com.google.android.contacts",
        "com.android.deskclock", "com.google.android.deskclock",
        "com.android.systemui", 
        "com.google.android.packageinstaller", "com.android.packageinstaller"
    )
    
    // Timers
    private var lastUrlCheckTime = 0L
    private var lastBackgroundUpdate = 0L
    private var scanEventsCount = 0
    
    // Battey Optimized 60s Watchdog
    private val BROWSER_CHECK_INTERVAL = 60_000L 
    private var browserCheckRunnable: Runnable? = null
    private var currentBrowserPackage: String? = null
    
    // Anti-bypass tracking
    private var lastBlockedPackage: String = ""
    private var lastBlockTime: Long = 0L
    
    // PERSISTENT blocked packages for current session (prevents 2-min bypass)
    private val sessionBlockedPackages = mutableSetOf<String>()
    
    // === SETTINGS PAGE LEARNING ===
    private var isLearningMode = false
    private var isCustomPageLearning = false      // NEW: Learning a custom (user-defined) page/button
    private var currentLearningPageType: Constants.PageType? = null
    private var learnOverlay: android.view.View? = null
    private var penaltyOverlay: android.view.View? = null
    private var penaltyTimer: android.os.CountDownTimer? = null
    
    // SMART: Keywords selected during learning (from actual page content)
    private val selectedLearningKeywords = mutableListOf<String>()
    private var lastWindowClassName: String = ""
    private var lastWindowPackage: String = ""
    var learnedSettingsPages = Constants.LearnedSettingsPages()
    private var currentCustomPageName: String = ""


    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_FOCUS_MODE -> {
                    refreshSettings()
                    // Reload Strict Mode data efficiently on background thread
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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
                            currentLearningPageType = Constants.PageType.valueOf(pageTypeName)
                            isLearningMode = true
                            showLearnConfirmOverlay()
                            com.neubofy.reality.utils.TerminalLogger.log("LEARN: Started for $pageTypeName")
                        } catch (e: Exception) {
                            com.neubofy.reality.utils.TerminalLogger.log("LEARN: Invalid page type - $pageTypeName")
                        }
                    }
                }
                INTENT_ACTION_START_CUSTOM_PAGE_LEARNING -> {
                    val customName = intent.getStringExtra("custom_name") ?: "Custom Page"
                    currentCustomPageName = customName
                    currentLearningPageType = null
                    isLearningMode = true
                    isCustomPageLearning = true
                    showLearnConfirmOverlay()
                    com.neubofy.reality.utils.TerminalLogger.log("LEARN CUSTOM PAGE: Started - $customName")
                }
                INTENT_ACTION_STOP_LEARNING -> {
                    isLearningMode = false
                    isCustomPageLearning = false
                    currentLearningPageType = null
                    removeLearnOverlay()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopBrowserCheckTimer() // Save battery when screen off
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    lastBackgroundUpdate = 0 
                    
                    // Refresh cache on screen unlock (event-driven update)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
                    }
                    
                    // Resume checking if needed
                    if (isWebsiteBlockActive()) {
                         startBrowserCheckTimer()
                    }
                }
            }
        }
    }
    
    private var isScreenOn = true
    private var isBlockingActive = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScreenOn) return
        
        // Capture window class for learning mode and strict mode
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val pkg = event.packageName?.toString() ?: ""
            
            // === SMART SETTINGS PROTECTION ===
            // Only wake protection system when:
            // 1. It's a settings package
            // 2. Page just changed (not same page)
            // 3. Strict mode protection is active (early exit if not!)
            // 4. Not in learning mode
            val isSettingsPackage = pkg.contains("settings") || pkg.contains("securitycenter")
            val isNewPage = className != lastWindowClassName || pkg != lastWindowPackage
            
            lastWindowClassName = className
            lastWindowPackage = pkg
            
            // === EARLY EXIT: Skip if no protection is active ===
            // This is the key optimization - don't do ANY work unless protection is ON
            if (isSettingsPackage && isNewPage && !isLearningMode && 
                com.neubofy.reality.utils.SettingsBox.isAnyProtectionActive()) {
                // Small delay to let content load for ambiguous pages
                handler.postDelayed({
                    handleStrictSettingsProtection()
                }, 50) // 50ms - fast enough to feel instant, slow enough for content to load
            }
            
            // Log only Settings-related packages (only if protection active)
            if (isSettingsPackage && com.neubofy.reality.utils.SettingsBox.isAnyProtectionActive()) {
                com.neubofy.reality.utils.TerminalLogger.log("SETTINGS: ${className.substringAfterLast(".")}")
            }
            
            // Update learning overlay when user navigates
            if (isLearningMode && learnOverlay != null) {
                handler.post { updateLearnOverlayText() }
            }
        }
        
        // === TOGGLE GUARDIAN REMOVED ===
        // Button interception logic removed as requested.
        // We now rely on robust page blocking.

        
        // REMOVED: Over-aggressive content change checks for settings
        // The WINDOW_STATE_CHANGED check above is sufficient and catches recents too
        
        if (!isBlockingActive) {
            // Check occasionally (every 30s)
            val now = System.currentTimeMillis()
            if (now - lastBackgroundUpdate > 120_000) {
                lastBackgroundUpdate = now
                performBackgroundUpdates()
            }
        }
        scanEventsCount++
        
        if (event == null) return
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        
        // REMOVED: Duplicate settings check here - now handled above with smart caching

        // 1. Browser Special Handling (Watchdog Trigger)
        val isBrowser = com.neubofy.reality.utils.UrlDetector.isBrowser(packageName)
        
        // Check if any blocking mode is active (Focus/Schedule/Calendar/Bedtime)
        val isAnyBlockingModeActive = isWebsiteBlockActive()
        
        if (isBrowser) {
             // Browser is active - start/keep watchdog running if blocking is needed
             if (isAnyBlockingModeActive) {
                 currentBrowserPackage = packageName 
                 startBrowserCheckTimer()
                 resetWatchdogRampUp()
                 
                 // FIX: IMMEDIATE URL check on any browser event (catches URL/tab changes)
                 // Check on content changes, text changes, focus changes, window state changes
                 if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                     eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                     checkUrl(packageName)
                 }
             }
        } else {
             // Left the browser
             currentBrowserPackage = null
             // FIX: Watchdog stays alive (Hibernating) ONLY if session is active.
             // If session ends, the watchdog stops itself in the Runnable check.
             // We do NOT explicitly stop it here to prevent "Recent Apps" loophole.
        }
        
        // Strict Optimization: Ignore high-frequency events below this line
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
             if (!sessionBlockedPackages.contains(packageName)) return
        }
        
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        
        // Session Block Check (Fast path)
        if (sessionBlockedPackages.contains(packageName)) {
             handleBlock(packageName, "Session Block")
             return
        }
        
        // NOTE: Removed 30-second polling for battery optimization.
        // Box rebuilds via: BlockCacheWorker (3 min), Screen ON, Settings changed.
        // The O(1) Box check below runs on EVERY app switch.

        
        // Anti-bypass for apps
        if (packageName == lastBlockedPackage && System.currentTimeMillis() - lastBlockTime < 3000) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        lastPackage = packageName
        
        // === THE BOX CHECK - SINGLE SOURCE OF TRUTH ===
        // No fallback. No calculations. Just check the box.
        val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(packageName)
        
        if (shouldBlock) {
            val reason = reasons.joinToString(", ")
            
            if (sessionBlockedPackages.contains(packageName)) {
                 handleBlock(packageName, reason)
                 return
            }
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
            if (currentWarnedPackage == packageName) {
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
            e.printStackTrace()
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


    private fun performBackgroundUpdates() {
        if (!hasUsageStatsPermission()) return
        
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // === REBUILD THE BOX - SINGLE SOURCE OF TRUTH ===
                com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    // Update blocking status based on BlockCache
                    isBlockingActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive ||
                                       com.neubofy.reality.utils.BlockCache.getBlockedCount() > 0
                    
                    val isAnyModeActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
                    
                    // === NOTIFICATION MANAGEMENT ===
                    if (!isAnyModeActive) {
                        com.neubofy.reality.utils.NotificationTimerManager(this@AppBlockerService).stopTimer()
                        sessionBlockedPackages.clear()
                    }
                    
                    // === DND SYNC ===
                    if (savedPreferencesLoader.isAutoDndEnabled()) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val currentFilter = notificationManager.getCurrentInterruptionFilter()
                        val isDndOn = currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        
                        if (isAnyModeActive && !isDndOn) {
                            toggleDnd(true)
                        } else if (!isAnyModeActive && isDndOn) {
                            toggleDnd(false)
                        }
                    }
                    
                    // === GRAYSCALE REMOVED - feature requires ADB ===
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("BG UPDATE ERROR: ${e.message}")
            }
        }
    }

    // toggleGrayscale REMOVED - feature requires ADB

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
     * SETTINGS BOX POWERED PROTECTION
     * 
     * Uses the new SettingsBox for O(1) page detection:
     * 1. Get package + className from accessibility event (instant)
     * 2. O(1) lookup in SettingsBox
     * 3. If ambiguous class (SubSettings) → quick keyword scan (top 15 nodes only)
     * 4. Block or allow
     */
    private fun handleStrictSettingsProtection() {
        try {
            val strictData = blocker.strictModeData
            
            if (!strictData.isEnabled) {
                return
            }
            
            if (packageManager.isSafeMode) {
                return
            }
            
            // === SETTINGS BOX LOOKUP (O(1)) ===
            val currentPackage = lastWindowPackage
            val currentClass = lastWindowClassName
            
            // Skip if no data
            if (currentClass.isEmpty()) return
            
            // Get root node for content verification (lazy - only if needed)
            val rootNode = rootInActiveWindow
            
            // === THE BOX CHECK ===
            val blockResult = com.neubofy.reality.utils.SettingsBox.shouldBlockPage(
                packageName = currentPackage,
                className = currentClass,
                rootNode = rootNode
            )
            
            if (blockResult.shouldBlock) {
                com.neubofy.reality.utils.TerminalLogger.log("SETTINGS_BOX: BLOCKING ${currentClass.substringAfterLast(".")} - ${blockResult.reason}")
                
                val penaltyDuration = calculatePenaltyDuration()
                showPenaltyOverlay(blockResult.reason, penaltyDuration)
            }
            
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("STRICT ERROR: ${e.message}")
        }
    }

    
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

    // === SETTINGS PAGE LEARNING OVERLAY (DRAGGABLE) ===
    private var learnOverlayParams: android.view.WindowManager.LayoutParams? = null
    
    private fun showLearnConfirmOverlay() {
        if (learnOverlay != null) return
        selectedLearningKeywords.clear() // Reset keywords for new learning session
        
        try {
            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 
                    else android.view.WindowManager.LayoutParams.TYPE_PHONE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = 50
                y = 200
            }
            learnOverlayParams = params
            
            val inflater = android.view.LayoutInflater.from(this)
            learnOverlay = inflater.inflate(R.layout.overlay_learn_confirm, null)
            
            learnOverlay?.findViewById<android.widget.TextView>(R.id.tvCurrentPage)?.text = 
                "Current: ${lastWindowClassName.substringAfterLast(".")}"
            
            // SCAN BUTTON - Extract keywords from actual page content
            learnOverlay?.findViewById<android.widget.Button>(R.id.btnScan)?.setOnClickListener {
                try {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        val keywords = com.neubofy.reality.utils.KeywordSuggestions.extractFromAccessibilityTree(rootNode)
                        rootNode.recycle()
                        
                        if (keywords.isNotEmpty()) {
                            // Show keywords section
                            learnOverlay?.findViewById<android.view.View>(R.id.keywordsSection)?.visibility = android.view.View.VISIBLE
                            
                            // Add keyword chips
                            val chipsContainer = learnOverlay?.findViewById<android.widget.LinearLayout>(R.id.chipsContainer)
                            chipsContainer?.removeAllViews()
                            
                            keywords.take(8).forEach { keyword ->
                                val chip = android.widget.TextView(this).apply {
                                    text = keyword
                                    setBackgroundColor(0x4400FF88.toInt())
                                    setTextColor(0xFFFFFFFF.toInt())
                                    textSize = 11f
                                    setPadding(12, 6, 12, 6)
                                    setOnClickListener {
                                        if (keyword !in selectedLearningKeywords) {
                                            selectedLearningKeywords.add(keyword)
                                            setBackgroundColor(0xFF00AA44.toInt()) // Green when selected
                                            updateSelectedKeywordsDisplay()
                                        } else {
                                            selectedLearningKeywords.remove(keyword)
                                            setBackgroundColor(0x4400FF88.toInt()) // Reset
                                            updateSelectedKeywordsDisplay()
                                        }
                                    }
                                }
                                val chipParams = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { marginEnd = 8 }
                                chipsContainer?.addView(chip, chipParams)
                            }
                            
                            android.widget.Toast.makeText(this, "Found ${keywords.size} keywords! Tap to add.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(this, "No keywords found on this page", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(this, "Cannot scan - navigate to page first", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Scan error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            
            learnOverlay?.findViewById<android.widget.Button>(R.id.btnConfirm)?.setOnClickListener {
                saveLearnedPage()
                removeLearnOverlay()
                val keywordCount = selectedLearningKeywords.size
                val msg = if (keywordCount > 0) "✓ Page + $keywordCount keywords saved!" else "✓ Page recorded!"
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                isLearningMode = false
                isCustomPageLearning = false
                currentLearningPageType = null
                selectedLearningKeywords.clear()
                
                // Navigate back to StrictModeActivity
                val intent = Intent(this, com.neubofy.reality.ui.activity.StrictModeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            
            learnOverlay?.findViewById<android.widget.Button>(R.id.btnCancel)?.setOnClickListener {
                removeLearnOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
                isLearningMode = false
                currentLearningPageType = null
                selectedLearningKeywords.clear()
            }
            
            // === MAKE DRAGGABLE ===
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            
            learnOverlay?.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(learnOverlay, params)
                        } catch (e: Exception) {}
                        true
                    }
                    else -> false
                }
            }
            
            windowManager.addView(learnOverlay, params)
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("LEARN: Overlay error - ${e.message}")
        }
    }
    
    private fun updateSelectedKeywordsDisplay() {
        val tvSelected = learnOverlay?.findViewById<android.widget.TextView>(R.id.tvSelectedKeywords)
        if (selectedLearningKeywords.isNotEmpty()) {
            tvSelected?.visibility = android.view.View.VISIBLE
            tvSelected?.text = "Selected: ${selectedLearningKeywords.joinToString(", ")}"
        } else {
            tvSelected?.visibility = android.view.View.GONE
        }
    }
    
    private fun updateLearnOverlayText() {
        try {
            learnOverlay?.findViewById<android.widget.TextView>(R.id.tvCurrentPage)?.text = 
                "Current: ${lastWindowClassName.substringAfterLast(".")}"
        } catch (e: Exception) {}
    }
    
    private fun removeLearnOverlay() {
        try {
            learnOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            learnOverlay = null
        } catch (e: Exception) { learnOverlay = null }
    }
    
    private fun saveLearnedPage() {
        val className = lastWindowClassName
        if (className.isEmpty()) return
        
        // CRITICAL: Reload fresh data from preferences to avoid overwriting deletions!
        learnedSettingsPages = savedPreferencesLoader.getLearnedSettingsPages()
        
        // If custom page learning, add to custom list
        if (isCustomPageLearning && currentLearningPageType == null) {
            val name = currentCustomPageName.ifEmpty { "Custom Page" }
            val pageKey = "1|$name|$lastWindowPackage|$className"
            learnedSettingsPages.customBlockedPages.add(pageKey)
            savedPreferencesLoader.saveLearnedSettingsPages(learnedSettingsPages)
            com.neubofy.reality.utils.TerminalLogger.log("LEARN CUSTOM: Saved page = $pageKey")
            return
        }
        
        val pageType = currentLearningPageType ?: return
        
        when (pageType) {
            Constants.PageType.ACCESSIBILITY -> {
                learnedSettingsPages.accessibilityPageClass = className
                learnedSettingsPages.accessibilityPagePackage = lastWindowPackage
                // Add selected keywords if any
                if (selectedLearningKeywords.isNotEmpty()) {
                    learnedSettingsPages.accessibilityKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.DEVICE_ADMIN -> {
                learnedSettingsPages.deviceAdminPageClass = className
                learnedSettingsPages.deviceAdminPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    learnedSettingsPages.deviceAdminKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.APP_INFO -> {
                learnedSettingsPages.appInfoPageClass = className
                learnedSettingsPages.appInfoPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    learnedSettingsPages.appInfoKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.TIME_SETTINGS -> {
                learnedSettingsPages.timeSettingsPageClass = className
                learnedSettingsPages.timeSettingsPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    learnedSettingsPages.timeSettingsKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.DEVELOPER_OPTIONS -> {
                learnedSettingsPages.developerOptionsPageClass = className
                learnedSettingsPages.developerOptionsPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    learnedSettingsPages.developerOptionsKeywords.addAll(selectedLearningKeywords)
                }
            }
        }
        
        savedPreferencesLoader.saveLearnedSettingsPages(learnedSettingsPages)
        com.neubofy.reality.utils.SettingsBox.rebuildBox(applicationContext)
        val keywordInfo = if (selectedLearningKeywords.isNotEmpty()) " + ${selectedLearningKeywords.size} keywords" else ""
        com.neubofy.reality.utils.TerminalLogger.log("LEARN: Saved $pageType = $lastWindowPackage|$className$keywordInfo")
    }
    


    
    // === PENALTY OVERLAY ===
    private fun showPenaltyOverlay(reason: String, durationSecs: Int = 30) {
        if (penaltyOverlay != null) return
        try {
            // IMMEDIATELY kill Settings and go HOME
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses("com.android.settings")
                am.killBackgroundProcesses(lastWindowPackage) // Kill whatever settings package
            } catch (e: Exception) {}
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 
                    else android.view.WindowManager.LayoutParams.TYPE_PHONE,
                // CRITICAL: Do NOT use FLAG_NOT_TOUCHABLE - it lets touches pass through!
                // We want the overlay to CONSUME all touches
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
                android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.OPAQUE  // Fully opaque
            )
            
            val inflater = android.view.LayoutInflater.from(this)
            penaltyOverlay = inflater.inflate(R.layout.overlay_penalty, null)
            
            // Consume ALL touch events - do not let them pass through
            penaltyOverlay?.setOnTouchListener { _, _ -> true }
            
            val tvTimer = penaltyOverlay?.findViewById<android.widget.TextView>(R.id.tvPenaltyTimer)
            val tvReason = penaltyOverlay?.findViewById<android.widget.TextView>(R.id.tvPenaltyReason)
            
            tvReason?.text = "Reason: $reason"
            
            penaltyTimer = object : android.os.CountDownTimer(durationSecs * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secs = millisUntilFinished / 1000
                    tvTimer?.text = String.format("%02d:%02d", secs / 60, secs % 60)
                }
                
                override fun onFinish() {
                    removePenaltyOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }.start()
            
            windowManager.addView(penaltyOverlay, params)
            com.neubofy.reality.utils.TerminalLogger.log("PENALTY: Showing ${durationSecs}s penalty for $reason")
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("PENALTY: Overlay error - ${e.message}")
        }
    }
    
    private fun removePenaltyOverlay() {
        try {
            penaltyTimer?.cancel()
            penaltyTimer = null
            penaltyOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            penaltyOverlay = null
        } catch (e: Exception) { penaltyOverlay = null }
    }
    
    private fun calculatePenaltyDuration(): Int {
        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000L
        
        if (now - learnedSettingsPages.lastPenaltyTime < fiveMinutes) {
            learnedSettingsPages.consecutiveAttempts++
        } else {
            learnedSettingsPages.consecutiveAttempts = 1
        }
        learnedSettingsPages.lastPenaltyTime = now
        savedPreferencesLoader.saveLearnedSettingsPages(learnedSettingsPages)
        
        // Escalating penalties: 30s → 60s → 120s → 180s → 300s (5 min max)
        return when (learnedSettingsPages.consecutiveAttempts) {
            1 -> 30      // 30 seconds
            2 -> 60      // 1 minute
            3 -> 120     // 2 minutes
            4 -> 180     // 3 minutes
            else -> 300  // 5 minutes max
        }
    }

    // Updated signature to control session persistence
    private fun handleBlock(packageName: String, reason: String? = null, addToSession: Boolean = true) {
        com.neubofy.reality.utils.TerminalLogger.log("ACTION: Blocking $packageName. Reason: ${reason ?: "N/A"}")
        // Kill Process for stronger block
        try {
             val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
             am.killBackgroundProcesses(packageName)
        } catch(e: Exception) {}

        // Add to session blocked set - prevents 2-min bypass
        if (addToSession) {
            sessionBlockedPackages.add(packageName)
        }
        
        lastBlockedPackage = packageName
        lastBlockTime = System.currentTimeMillis()
        
        // Launch Block Activity
        try {
            val intent = Intent(this, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                 putExtra("pkg", packageName)
                 putExtra("reason", reason)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if activity fails
            pressHome()
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
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
        
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
        blocker.refreshSchedules(savedPreferencesLoader.loadAutoFocusHoursList())
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
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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
                        sessionBlockedPackages.clear()
                    }
                    
                    // === DND SYNC ===
                    if (savedPreferencesLoader.isAutoDndEnabled()) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val currentFilter = notificationManager.getCurrentInterruptionFilter()
                        val isDndOn = currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        
                        if (isAnyModeActive && !isDndOn) {
                            // Mode started, turn DND on
                            toggleDnd(true)
                        } else if (!isAnyModeActive && isDndOn) {
                            // Mode ended, turn DND off
                            toggleDnd(false)
                        }
                    }
                    
                    com.neubofy.reality.utils.TerminalLogger.log("BOX SYNC: Active=$isBlockingActive, DND=${com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive}")
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
        
        scheduleNextAlarm()
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
    
    private fun toggleDnd(enable: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (enable) {
                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                com.neubofy.reality.utils.TerminalLogger.log("DND: Enabled (Mode Active)")
            } else {
                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                com.neubofy.reality.utils.TerminalLogger.log("DND: Disabled (Mode Ended)")
            }
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

    // ==========================================
    // BROWSER SMART BLOCKING Logic
    // ==========================================

    private fun isWebsiteBlockActive(): Boolean {
        // GLOBAL EMERGENCY BYPASS
        if (com.neubofy.reality.utils.BlockCache.emergencySessionEndTime > System.currentTimeMillis()) {
             return false
        }

        // Get blocked websites from saved preferences
        val blockedWebsites = savedPreferencesLoader.getFocusModeData().blockedWebsites
        
        // If no websites are blocked, don't run checks
        if (blockedWebsites.isEmpty()) {
            return false
        }
        
        // Check if any blocking mode is active using BlockCache
        return com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
    }

    private fun isBedtime(): Boolean {
        val cal = java.util.Calendar.getInstance()
        val currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = blocker.bedtimeData.startTimeInMins
        val end = blocker.bedtimeData.endTimeInMins
        
        return if (start < end) {
            currentMins in start until end
        } else {
            currentMins >= start || currentMins < end
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val text = StringBuilder()
        if (node.text != null) text.append(node.text).append(" ")
        if (node.contentDescription != null) text.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            text.append(extractText(child))
            child?.recycle()
        }
        return text.toString()
    }
    private fun checkUrl(packageName: String) {
        lastUrlCheckTime = System.currentTimeMillis()
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val url = com.neubofy.reality.utils.UrlDetector.getUrl(root, packageName)
                
                if (url != null && url.contains(".")) {
                    val cleanUrl = url.lowercase()
                    
                    // Get blocked websites from saved preferences (works for schedules too)
                    val blockedWebsites = savedPreferencesLoader.getFocusModeData().blockedWebsites
                    
                    // Check against blocklist (case-insensitive)
                    val blockedItem = blockedWebsites.find { it.isNotEmpty() && cleanUrl.contains(it.lowercase()) }
                    
                    if (blockedItem != null) {
                        com.neubofy.reality.utils.TerminalLogger.log("BLOCKED SITE: $cleanUrl") 
                        
                        // 1. Redirect browser to blank IMMEDIATELY
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("about:blank"))
                            intent.setPackage(packageName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {}
                        
                        // 2. Launch Block Activity Over Everything (Inescapable)
                        val blockIntent = Intent(this, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                             addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) 
                             putExtra("pkg", packageName)
                             putExtra("reason", "Website Blocked: $blockedItem")
                        }
                        startActivity(blockIntent)
                        
                        // 3. Accessibility Back (Just in case)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        
                        return
                    }
                }
            }
        } catch (e: Exception) {}
    }
    
    // THE WATCHDOG: Runs continuously while Session is Active
    // Adaptive Polling Variables
    private var currentPollInterval = 15_000L // Start aggressive
    private val POLL_STEPS = listOf(15_000L, 30_000L, 60_000L, 90_000L)
    private var pollStepIndex = 0

    private fun startBrowserCheckTimer() {
        if (browserCheckRunnable != null) return // Already running
        
        // Reset to aggressive start
        resetWatchdogRampUp()
        
        com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Started (Smart Hibernation)")
        browserCheckRunnable = object : Runnable {
            override fun run() {
                // STOP CONDITION: Only if the entire Blocking Session Ends
                if (!isWebsiteBlockActive()) {
                    com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Stopped (Session Ended)")
                    stopBrowserCheckTimer()
                    return
                }
                
                // Check if browser is currently the active window
                val activeWindow = rootInActiveWindow
                val activePackage = activeWindow?.packageName?.toString()
                val isBrowserActive = activePackage != null && com.neubofy.reality.utils.UrlDetector.isBrowser(activePackage)
                
                if (isBrowserActive) {
                    // ACTIVE MODE: Browser is in foreground - do full URL check
                    currentBrowserPackage = activePackage
                    checkUrl(currentBrowserPackage!!)
                    
                    // Use adaptive polling when browser is active (15s -> 30s -> 60s -> 90s)
                    if (pollStepIndex < POLL_STEPS.size - 1) {
                        pollStepIndex++
                    }
                    currentPollInterval = POLL_STEPS[pollStepIndex]
                    
                } else {
                    // HIBERNATE MODE: Browser is NOT active - check less frequently
                    // Just check if browser appeared (from recents, etc.)
                    currentBrowserPackage = null
                    
                    // Use longer interval when hibernating (5 seconds - just to detect browser opening)
                    // This saves battery vs constantly checking URL when not in browser
                    currentPollInterval = 5000L  // 5 second hibernate check
                    
                    // If browser detected from recents, wake up immediately next cycle
                    // (handled above when isBrowserActive becomes true)
                }
                
                // Re-run with calculated interval
                handler.postDelayed(this, currentPollInterval)
            }
        }
        handler.post(browserCheckRunnable!!) // Run immediately first
    }
    
    private fun resetWatchdogRampUp() {
        pollStepIndex = 0
        currentPollInterval = POLL_STEPS[0]
        // If runnable is running, we don't restart it, but the next loop will use the reset index/interval
        // However, to be instant, we can verify if we need to force a quick check? 
        // For now, next cycle catches it. To be purely instant on touch, we rely on the immediate post in start()
        // or the fact that this is called from onAccessibilityEvent.
    }
    
    private fun stopBrowserCheckTimer() {
        browserCheckRunnable?.let { 
            handler.removeCallbacks(it) 
        }
        browserCheckRunnable = null
    }

    // ==========================================
    // REMINDER SYSTEM (FULL SCREEN ALARM)
    // ==========================================
    
    private val reminderManager by lazy { com.neubofy.reality.ui.overlay.ReminderOverlayManager(this) }
    
    // Reliable Alarm Scheduling
    private fun scheduleNextAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(this, com.neubofy.reality.receivers.ReminderReceiver::class.java)
            
            // Cancel old
            val pIntentCheck = android.app.PendingIntent.getBroadcast(this, 1001, intent, android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
            if (pIntentCheck != null) {
                alarmManager.cancel(pIntentCheck)
            }

            val list = savedPreferencesLoader.loadCustomReminders()
            val now = java.util.Calendar.getInstance()
            val currentDay = now.get(java.util.Calendar.DAY_OF_WEEK)
            val currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            
            var nextTriggerMillis = Long.MAX_VALUE
            var nextReminder: com.neubofy.reality.data.CustomReminder? = null
            
            // Global offset
            val globalOffset = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE).getInt("reminder_offset_minutes", 1)

            for (r in list) {
                if (!r.isEnabled) continue
                if (r.repeatDays.isNotEmpty() && !r.repeatDays.contains(currentDay)) continue
                
                val startMins = r.hour * 60 + r.minute
                val effectiveOffset = r.customOffsetMins ?: globalOffset
                val triggerMins = startMins - effectiveOffset
                
                // If trigger time is in future today
                if (triggerMins > currentMins) {
                    val triggerCal = java.util.Calendar.getInstance()
                    triggerCal.set(java.util.Calendar.HOUR_OF_DAY, triggerMins / 60)
                    triggerCal.set(java.util.Calendar.MINUTE, triggerMins % 60)
                    triggerCal.set(java.util.Calendar.SECOND, 0)
                    triggerCal.set(java.util.Calendar.MILLISECOND, 0)
                    
                    if (triggerCal.timeInMillis < nextTriggerMillis) {
                        nextTriggerMillis = triggerCal.timeInMillis
                        nextReminder = r
                    }
                }
            }
            
            if (nextReminder != null && nextTriggerMillis != Long.MAX_VALUE) {
                val pIntent = android.app.PendingIntent.getBroadcast(this, 1001, intent.apply {
                    putExtra("id", nextReminder.id)
                    putExtra("title", nextReminder.title)
                    putExtra("url", nextReminder.url)
                    putExtra("mins", nextReminder.customOffsetMins ?: globalOffset)
                }, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // GOLD STANDARD: setAlarmClock guarantees wake-up even in Doze
                    val acInfo = android.app.AlarmManager.AlarmClockInfo(nextTriggerMillis, pIntent)
                    alarmManager.setAlarmClock(acInfo, pIntent)
                } else {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, nextTriggerMillis, pIntent)
                }
                com.neubofy.reality.utils.TerminalLogger.log("ALARM: Scheduled (AlarmClock) for ${java.util.Date(nextTriggerMillis)}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkUpcomingSchedules() {
        // Delegate all alarm scheduling to unified AlarmScheduler
        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
    }

    private fun sendReminderNotification(title: String, mins: Int) {
        val channelId = "reality_reminders"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Reminders", android.app.NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, com.neubofy.reality.ui.activity.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 101, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) 
            .setContentTitle("Upcoming Focus Session")
            .setContentText("$title starts in $mins minute.") // "1 minute"
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            
        manager.notify(title.hashCode(), builder.build())
    }
}
