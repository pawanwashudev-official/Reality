package com.neubofy.reality.utils

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList
import java.util.Queue

object UrlDetector {
    @Suppress("DEPRECATION")
    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (node != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            try { node.recycle() } catch(e: Exception){}
        }
    }
    val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.google.android.apps.searchlite",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.android.browser",
        // Modern browsers added
        "ai.perplexity.app.android",    // Perplexity AI (Comet browser)
        "com.perplexity.comet",          // Comet alternate package
        "com.kiwibrowser.browser",       // Kiwi Browser
        "org.chromium.chrome",           // Chromium
        "com.yandex.browser",            // Yandex Browser
        "com.uc.browser.en",             // UC Browser
        "mark.via.gp",                   // Via Browser
        "com.lemur.browser",             // Lemur
        "com.cloudmosa.puffinFree",      // Puffin
        "org.torproject.torbrowser",     // Tor
        "com.pure.browser",              // Pure Browser
        "acr.browser.barebones"          // Lightning Browser
    )

    // Dynamic list populated at runtime
    private var dynamicBrowserPackages: Set<String> = emptySet()
    
    // Cache for successful View IDs per package to avoid scanning all IDs every time
    private val successfulViewIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun init(context: android.content.Context) {
        try {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
            val list = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
            
            val detected = mutableSetOf<String>()
            for (resolveInfo in list) {
                detected.add(resolveInfo.activityInfo.packageName)
            }
            
            // Add known browsers that might not catch the intent for some reason (Fallbacks)
            detected.addAll(BROWSER_PACKAGES)
            
            dynamicBrowserPackages = detected
            com.neubofy.reality.utils.TerminalLogger.log("UrlDetector: Discovered ${detected.size} browser apps.")
        } catch (e: Exception) {
            // Fallback to static list if query fails
            dynamicBrowserPackages = BROWSER_PACKAGES
        }
    }

    fun isBrowser(packageName: String): Boolean {
        return if (dynamicBrowserPackages.isNotEmpty()) {
            dynamicBrowserPackages.contains(packageName)
        } else {
            BROWSER_PACKAGES.contains(packageName)
        }
    }

    // Pre-compiled Regex for performance
    private val DOMAIN_REGEX = Regex(".*[a-zA-Z0-9-]+\\.[a-z]{2,}.*")
    
    // Static list to avoid allocation on every scan
    private val KNOWN_DOMAINS = listOf("youtube", "facebook", "instagram", "twitter", "tiktok", "reddit", "twitch")

    fun getUrl(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null
        
        // 0. Try Cached ID first (Optimization)
        val cachedId = successfulViewIdCache[packageName]
        if (cachedId != null) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(cachedId)
            if (!nodes.isNullOrEmpty()) {
                try {
                    for (node in nodes) {
                        val text = extractValidUrl(node)
                        if (text != null) {
                            // Cache Match!
                            return text
                        }
                    }
                } finally {
                    nodes.forEach { safeRecycle(it) }
                }
            }
        }
        
        // 1. Try View ID lookup (Fastest & Most Accurate)
        val urlBarIds = listOf(
            // Chrome
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.google.android.apps.chrome:id/url_bar",
            "com.google.android.apps.chrome:id/search_box_text",
            // Edge - Multiple IDs because Edge UI varies
            "com.microsoft.emmx:id/url_bar",
            "com.microsoft.emmx:id/url_bar_title",
            "com.microsoft.emmx:id/search_box_text",
            "com.microsoft.emmx:id/search_box",
            "com.microsoft.emmx:id/omnibox_text",
            // Samsung
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/url_bar",
            // Firefox
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar",
            // Generic
            "$packageName:id/url_bar",
            "$packageName:id/address_bar",
            "$packageName:id/omnibox",
            "$packageName:id/url_field",
            "$packageName:id/url_bar_title"
        )

        for (id in urlBarIds) {
            // Skip cached ID check since we already did it (unless it failed, then we retry here essentially)
            if (id == cachedId) continue 
            
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                try {
                    for (node in nodes) {
                        val text = extractValidUrl(node)
                        if (text != null) {
                            com.neubofy.reality.utils.TerminalLogger.log("URL (ID): $text")
                            
                            // Success! Cache this ID for next time
                            successfulViewIdCache[packageName] = id
                            
                            return text
                        }
                    }
                } finally {
                    // IMPORTANT: Recycle nodes to prevent memory leaks
                    nodes.forEach { safeRecycle(it) }
                }
            }
        }

        // 2. Deep Search Scanner (BFS)
        // If ID lookup failed, scan visible text nodes in the TOP AREA of the screen.
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        
        // Add children of root to queue (we handle their lifecycle)
        for (i in 0 until rootNode.childCount) {
             rootNode.getChild(i)?.let { queue.add(it) }
        }
        
        var count = 0
        
        try {
            while (!queue.isEmpty() && count < 300) {
                val node = queue.poll() ?: continue
                count++
                
                try {
                    // Check bounds: URL bar is almost always at the TOP
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    
                    // Check text if it's in the top portion of screen
                    if (rect.top < 500 && rect.bottom > 0) {
                        val text = node.text?.toString() ?: node.contentDescription?.toString()
                        
                        if (!text.isNullOrBlank()) {
                             val cleanText = text.trim().lowercase()
                             
                             // Skip common placeholders
                             if (cleanText != "search or type web address" && 
                                 cleanText != "search or type url" &&
                                 !cleanText.startsWith("search with")) {
                                     
                                 // Check if it looks like a URL or contains a known domain
                                 if (DOMAIN_REGEX.matches(cleanText)) {
                                     com.neubofy.reality.utils.TerminalLogger.log("URL (Scan): $cleanText")
                                     return cleanText
                                 }
                                 
                                 // Fallback: Check for known domain keywords
                                 for (domain in KNOWN_DOMAINS) {
                                     if (cleanText.contains(domain)) {
                                         com.neubofy.reality.utils.TerminalLogger.log("URL (Keyword): $cleanText")
                                         return cleanText
                                     }
                                 }
                             }
                        }
                    }
                    
                    for (i in 0 until node.childCount) {
                         node.getChild(i)?.let { queue.add(it) }
                    }
                } finally {
                    // Recycle processed node
                    safeRecycle(node)
                }
            }
        } finally {
             // Recycle remaining in queue
             while(!queue.isEmpty()) {
                 safeRecycle(queue.poll())
             }
        }

        return null
    }

    private fun extractValidUrl(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return null
        if (text.isBlank()) return null
        
        val cleanText = text.trim().lowercase()
        
        // CRITICAL: Only return text that looks like a real URL
        // Must contain a dot (e.g., youtube.com, google.com, m.facebook.com)
        // This prevents returning partial text like "you" or "face"
        if (!cleanText.contains(".")) {
            return null  // Not a valid URL - skip
        }
        
        // Skip placeholders
        if (cleanText == "search or type web address" || 
            cleanText == "search or type url" ||
            cleanText.startsWith("search")) {
            return null
        }
        
        return cleanText
    }
}
