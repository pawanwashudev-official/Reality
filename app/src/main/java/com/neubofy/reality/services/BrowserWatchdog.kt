package com.neubofy.reality.services

import android.content.Intent
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.SecureTimeProvider
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.UrlDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserWatchdog(
    private val service: AppBlockerService,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {
    private var lastUrlCheckTime = 0L
    
    // Adaptive Polling Variables
    private val POLL_STEPS = listOf(15_000L, 30_000L, 60_000L, 90_000L)
    private var currentPollInterval = 15_000L // Start aggressive
    private var pollStepIndex = 0
    private var browserCheckRunnable: Runnable? = null
    var currentBrowserPackage: String? = null
        private set

    fun isWebsiteBlockActive(): Boolean {
        // GLOBAL EMERGENCY BYPASS
        if (BlockCache.emergencySessionEndTime > SecureTimeProvider.currentTimeMillis(service)) {
            return false
        }

        // Optimized: Check Cached List directly
        // If no websites are blocked, don't run checks (saving battery)
        if (BlockCache.blockedWebsites.isEmpty()) {
            return false
        }
        
        // Check if any blocking mode is active using BlockCache
        return BlockCache.isAnyBlockingModeActive
    }

    private fun extractText(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null) return ""
        if (depth > 10) return ""
        val text = java.lang.StringBuilder()
        if (node.text != null) text.append(node.text).append(" ")
        if (node.contentDescription != null) text.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount.coerceAtMost(15)) {
            val child = node.getChild(i) ?: continue
            text.append(extractText(child, depth + 1))
            child.recycle()
        }
        return text.toString()
    }

    suspend fun checkUrl(packageName: String, root: AccessibilityNodeInfo?) {
        lastUrlCheckTime = System.currentTimeMillis()
        try {
            if (root != null) {
                val url = UrlDetector.getUrl(root, packageName)
                
                if (url != null && url.contains(".")) {
                    val cleanUrl = url.lowercase()
                    
                    // Optimized Check: Use BlockCache (validates session, schedule, blocklist)
                    val blockedItem = BlockCache.shouldBlockWebsite(cleanUrl)
                    
                    if (blockedItem != null) {
                        TerminalLogger.log("BLOCKED SITE: $cleanUrl") 
                        
                        // 1. Redirect browser to blank IMMEDIATELY
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("about:blank")).apply {
                                setPackage(packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            service.startActivity(intent)
                        } catch (e: Exception) {}
                        
                        // 1.5 Go home to close the app properly
                        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)

                        handler.postDelayed({
                            // 2. Launch Block Activity Over Everything (Inescapable)
                            val blockIntent = Intent(service, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                putExtra("pkg", packageName)
                                putExtra("reason", "Website Blocked: $blockedItem")
                            }
                            
                            // FIX: UI Interactions on Main Thread
                            serviceScope.launch(Dispatchers.Main) {
                                try {
                                    service.startActivity(blockIntent)
                                } catch (e: Exception) {}

                                // 3. Accessibility Back (Just in case)
                                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                            }
                        }, 300)
                        
                        return
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun startBrowserCheckTimer() {
        if (browserCheckRunnable != null) return // Already running
        
        // Reset to aggressive start
        resetWatchdogRampUp()
        
        TerminalLogger.log("WATCHDOG: Started")
        browserCheckRunnable = object : Runnable {
            override fun run() {
                // STOP CONDITION: Only if the entire Blocking Session Ends
                if (!isWebsiteBlockActive()) {
                    TerminalLogger.log("WATCHDOG: Stopped (Session Ended)")
                    stopBrowserCheckTimer()
                    return
                }
                
                // Check if browser is currently the active window
                val activeWindow = try { service.rootInActiveWindow } catch (e: Exception) { null }
                val activePackage = activeWindow?.packageName?.toString()
                val isBrowserActive = activePackage != null && UrlDetector.isBrowser(activePackage)
                
                if (isBrowserActive) {
                    // ACTIVE MODE: Browser is in foreground - do full URL check
                    currentBrowserPackage = activePackage
                    // Capture node on Main Thread
                    val rootNode = try { service.rootInActiveWindow } catch (e: Exception) { null }
                    if (rootNode != null) {
                        serviceScope.launch {
                            try {
                                checkUrl(currentBrowserPackage!!, rootNode)
                            } finally {
                                try { rootNode.recycle() } catch (e: Exception) {}
                            }
                        }
                    }
                    
                    // Use adaptive polling when browser is active (15s -> 30s -> 60s = 90s)
                    if (pollStepIndex < POLL_STEPS.size - 1) {
                        pollStepIndex++
                    }
                    currentPollInterval = POLL_STEPS[pollStepIndex]
                    
                    // Re-run with calculated interval
                    handler.postDelayed(this, currentPollInterval)
                } else {
                    // Browser is no longer in foreground - stop the watchdog entirely!
                    // Accessibility event will wake it up when user returns to a browser.
                    TerminalLogger.log("WATCHDOG: Stopped (Browser Left)")
                    stopBrowserCheckTimer()
                }
            }
        }
        handler.post(browserCheckRunnable!!) // Run immediately first
    }
    
    fun resetWatchdogRampUp() {
        pollStepIndex = 0
        currentPollInterval = POLL_STEPS[0]
    }
    
    fun stopBrowserCheckTimer() {
        browserCheckRunnable?.let { 
            handler.removeCallbacks(it) 
        }
        browserCheckRunnable = null
        currentBrowserPackage = null
    }
}
