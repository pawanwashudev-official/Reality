package com.neubofy.reality.utils

import android.view.accessibility.AccessibilityNodeInfo

/**
 * INTELLIGENT Keyword Extraction & Suggestions
 * 
 * Now extracts REAL keywords from actual page content using accessibility nodes.
 * No more hardcoded suggestions!
 * 
 * Smartness of Neubofy:
 * - Scans actual visible text on screen
 * - Filters common UI words (OK, Cancel, etc.)
 * - Suggests unique, meaningful keywords
 * - Adapts to any device/OEM
 */
object KeywordSuggestions {
    
    // Common words to exclude (UI noise)
    private val EXCLUDE_WORDS = setOf(
        "ok", "cancel", "yes", "no", "back", "next", "done", "save", "apply",
        "settings", "menu", "more", "options", "help", "about", "version",
        "tap", "click", "press", "hold", "swipe", "on", "off", "the", "and",
        "a", "an", "to", "from", "in", "out", "for", "with", "is", "are",
        "this", "that", "it", "be", "was", "were", "been", "being", "have",
        "has", "had", "do", "does", "did", "will", "would", "could", "should",
        "can", "may", "might", "must", "shall"
    )
    
    // Minimum word length for suggestions
    private const val MIN_WORD_LENGTH = 3
    
    // Maximum suggestions to return
    private const val MAX_SUGGESTIONS = 10
    
    /**
     * Extract intelligent keywords from AccessibilityNodeInfo tree
     * This scans the ACTUAL page content to suggest relevant keywords
     * 
     * @param rootNode The root accessibility node of the current window
     * @return List of unique, meaningful keywords found on the page
     */
    fun extractFromAccessibilityTree(rootNode: AccessibilityNodeInfo?): List<String> {
        if (rootNode == null) return emptyList()
        
        val allTexts = mutableListOf<String>()
        collectTextsFromNode(rootNode, allTexts, depth = 0, maxDepth = 15)
        
        // Process and filter
        return allTexts
            .flatMap { text -> 
                // Split by common separators
                text.lowercase()
                    .replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(" ", "\n", "\t")
            }
            .map { it.trim() }
            .filter { word ->
                word.length >= MIN_WORD_LENGTH &&
                word !in EXCLUDE_WORDS &&
                !word.all { it.isDigit() } &&
                !word.startsWith("com.") &&
                !word.contains(".")
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value } // Most frequent first
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }
    
    /**
     * Recursively collect text from accessibility nodes
     */
    private fun collectTextsFromNode(
        node: AccessibilityNodeInfo,
        result: MutableList<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        
        try {
            // Get text content
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            
            // Recurse into children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectTextsFromNode(child, result, depth + 1, maxDepth)
                }
            }
        } catch (e: Exception) {
            // Ignore errors from recycled nodes
        }
    }
    
    /**
     * Extract keywords from a list of text strings
     * Use this when you already have the text content
     */
    fun extractFromTextList(texts: List<String>): List<String> {
        return texts
            .flatMap { text -> 
                text.lowercase()
                    .replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(" ", "\n", "\t")
            }
            .map { it.trim() }
            .filter { word ->
                word.length >= MIN_WORD_LENGTH &&
                word !in EXCLUDE_WORDS &&
                !word.all { it.isDigit() }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }
    
    /**
     * Smart filter: Remove already-added keywords and rank by relevance
     */
    fun filterAndRank(
        suggestions: List<String>,
        alreadyAdded: List<String>,
        query: String = ""
    ): List<String> {
        val q = query.lowercase().trim()
        
        return suggestions
            .filter { it !in alreadyAdded.map { a -> a.lowercase() } }
            .filter { q.isEmpty() || it.contains(q) || q.contains(it) }
            .sortedBy { 
                when {
                    it.startsWith(q) -> 0
                    it.contains(q) -> 1
                    else -> 2
                }
            }
            .take(MAX_SUGGESTIONS)
    }
    
    /**
     * Get context-aware suggestions based on page type
     * Falls back to extracted content if available
     */
    fun getContextualSuggestions(
        pageType: com.neubofy.reality.Constants.PageType,
        extractedKeywords: List<String>
    ): List<String> {
        // If we have extracted keywords, use them (intelligent)
        if (extractedKeywords.isNotEmpty()) {
            return extractedKeywords.take(MAX_SUGGESTIONS)
        }
        
        // Fallback minimal hints only (not hardcoded full lists)
        return when (pageType) {
            com.neubofy.reality.Constants.PageType.TIME_SETTINGS -> 
                listOf("automatic", "network", "timezone")
            com.neubofy.reality.Constants.PageType.ACCESSIBILITY -> 
                listOf("service", "reality", "installed")
            com.neubofy.reality.Constants.PageType.DEVICE_ADMIN -> 
                listOf("admin", "deactivate", "reality")
            com.neubofy.reality.Constants.PageType.APP_INFO -> 
                listOf("uninstall", "force", "disable")
            com.neubofy.reality.Constants.PageType.DEVELOPER_OPTIONS -> 
                listOf("developer", "debugging", "usb")
        }
    }
}
