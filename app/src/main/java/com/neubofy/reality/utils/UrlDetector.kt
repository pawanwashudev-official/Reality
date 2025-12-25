package com.neubofy.reality.utils

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList
import java.util.Queue

object UrlDetector {
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
        "com.android.browser"
    )

    fun isBrowser(packageName: String): Boolean {
        return BROWSER_PACKAGES.contains(packageName)
    }

    fun getUrl(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null
        
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
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = extractValidUrl(node)
                    if (text != null) {
                        com.neubofy.reality.utils.TerminalLogger.log("URL (ID): $text")
                        return text
                    }
                }
            }
        }

        // 2. Deep Search Scanner (BFS)
        // If ID lookup failed, scan visible text nodes in the TOP AREA of the screen.
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(rootNode)
        var count = 0
        
        // Relaxed regex - matches domain.xxx patterns
        val domainRegex = Regex(".*[a-zA-Z0-9-]+\\.[a-z]{2,}.*")
        
        // Known domains to detect even without full URL format
        val knownDomains = listOf("youtube", "facebook", "instagram", "twitter", "tiktok", "reddit", "twitch")
        
        while (!queue.isEmpty() && count < 300) {
            val node = queue.poll()
            if (node == null) continue
            count++
            
            // Check bounds: URL bar is almost always at the TOP
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            
            // Check text if it's in the top portion of screen
            if (rect.top < 500 && rect.bottom > 0) {
                val text = node.text?.toString() ?: node.contentDescription?.toString()
                
                if (!text.isNullOrBlank()) {
                     val cleanText = text.trim().lowercase()
                     
                     // Skip common placeholders
                     if (cleanText == "search or type web address" || 
                         cleanText == "search or type url" ||
                         cleanText.startsWith("search with")) {
                         continue
                     }
                     
                     // Check if it looks like a URL or contains a known domain
                     if (domainRegex.matches(cleanText)) {
                         com.neubofy.reality.utils.TerminalLogger.log("URL (Scan): $cleanText")
                         return cleanText
                     }
                     
                     // Fallback: Check for known domain keywords
                     for (domain in knownDomains) {
                         if (cleanText.contains(domain)) {
                             com.neubofy.reality.utils.TerminalLogger.log("URL (Keyword): $cleanText")
                             return cleanText
                         }
                     }
                }
            }
            
            for (i in 0 until node.childCount) {
                 node.getChild(i)?.let { queue.add(it) }
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
