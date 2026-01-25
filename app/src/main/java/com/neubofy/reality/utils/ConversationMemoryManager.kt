package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conversation Memory Manager
 * 
 * Industry-standard memory optimization for AI chat:
 * 1. Sliding Window - Only recent messages sent to API
 * 2. Token Counting - Prevents hitting API limits
 * 3. Auto Summarization - Compresses old context
 */
object ConversationMemoryManager {

    // Configuration
    private const val MAX_RECENT_MESSAGES = 15  // Last N messages always included
    private const val MAX_TOKENS_ESTIMATE = 6000  // Leave room for response
    private const val TOKENS_PER_CHAR_ESTIMATE = 0.25  // ~4 chars per token (English)
    private const val SUMMARIZE_THRESHOLD = 20  // Summarize when history exceeds this
    
    // Prefs for persistent memory
    private const val PREFS_NAME = "ai_memory"
    private const val KEY_SESSION_SUMMARY_PREFIX = "session_summary_"
    private const val KEY_USER_FACTS = "user_facts"

    /**
     * Estimates token count for a string.
     * Uses ~4 chars per token rule (works for most languages).
     */
    fun estimateTokens(text: String): Int {
        return (text.length * TOKENS_PER_CHAR_ESTIMATE).toInt()
    }

    /**
     * Estimates total tokens for a list of messages.
     */
    fun estimateTotalTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { estimateTokens(it.message) + 10 } // +10 for role/formatting
    }

    /**
     * Builds an optimized message array for API calls.
     * 
     * Strategy:
     * 1. If history is small (< MAX_RECENT_MESSAGES), send all
     * 2. If history is large, use sliding window + summary of older messages
     * 3. Always respect token limits
     */
    fun buildOptimizedHistory(
        context: Context,
        fullHistory: List<ChatMessage>,
        sessionId: Long?,
        systemPrompt: String
    ): OptimizedContext {
        
        val result = mutableListOf<MessageSlot>()
        var tokenCount = estimateTokens(systemPrompt) + 20
        
        // Case 1: Small history - send all
        if (fullHistory.size <= MAX_RECENT_MESSAGES) {
            fullHistory.forEach { msg ->
                result.add(MessageSlot(msg.isUser, msg.message))
                tokenCount += estimateTokens(msg.message) + 10
            }
            return OptimizedContext(result, tokenCount, null)
        }
        
        // Case 2: Large history - use sliding window
        val recentMessages = fullHistory.takeLast(MAX_RECENT_MESSAGES)
        val olderMessages = fullHistory.dropLast(MAX_RECENT_MESSAGES)
        
        // Check if we have a cached summary for older messages
        val cachedSummary = getSummary(context, sessionId)
        
        // If we have older messages but no summary, we should request summarization
        val needsSummarization = olderMessages.isNotEmpty() && cachedSummary == null
        
        // Add summary context if available
        if (cachedSummary != null) {
            val summaryText = "[Previous conversation summary: $cachedSummary]"
            result.add(MessageSlot(false, summaryText))
            tokenCount += estimateTokens(summaryText) + 10
        }
        
        // Add recent messages
        for (msg in recentMessages) {
            val msgTokens = estimateTokens(msg.message) + 10
            
            // Token limit check - truncate early messages if needed
            if (tokenCount + msgTokens > MAX_TOKENS_ESTIMATE && result.size > 2) {
                break
            }
            
            result.add(MessageSlot(msg.isUser, msg.message))
            tokenCount += msgTokens
        }
        
        return OptimizedContext(
            messages = result,
            estimatedTokens = tokenCount,
            messagesToSummarize = if (needsSummarization) olderMessages else null
        )
    }

    /**
     * Converts optimized context to JSONArray for API.
     */
    fun toJsonMessages(systemPrompt: String, optimizedContext: OptimizedContext): JSONArray {
        val arr = JSONArray()
        
        // System prompt first
        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        
        // Add all messages
        optimizedContext.messages.forEach { slot ->
            arr.put(JSONObject().apply {
                put("role", if (slot.isUser) "user" else "assistant")
                put("content", slot.content)
            })
        }
        
        return arr
    }

    /**
     * Generates a summarization prompt for older messages.
     */
    fun getSummarizationPrompt(messages: List<ChatMessage>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            "$role: ${msg.message.take(200)}" // Truncate long messages for summary
        }
        
        return """Summarize this conversation in 2-3 sentences. Focus on:
1. User's main questions or goals
2. Key information the AI provided
3. Any decisions or conclusions reached

Conversation:
$conversationText

Summary:"""
    }

    /**
     * Saves a summary for a session.
     */
    fun saveSummary(context: Context, sessionId: Long?, summary: String) {
        if (sessionId == null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_SESSION_SUMMARY_PREFIX$sessionId", summary).apply()
    }

    /**
     * Gets cached summary for a session.
     */
    fun getSummary(context: Context, sessionId: Long?): String? {
        if (sessionId == null) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_SESSION_SUMMARY_PREFIX$sessionId", null)
    }

    /**
     * Clears summary when session is deleted.
     */
    fun clearSummary(context: Context, sessionId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_SESSION_SUMMARY_PREFIX$sessionId").apply()
    }

    // --- User Facts (Persistent Memory) ---
    
    /**
     * Adds a fact about the user that persists across sessions.
     */
    fun addUserFact(context: Context, fact: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_USER_FACTS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        existing.add(fact)
        prefs.edit().putStringSet(KEY_USER_FACTS, existing).apply()
    }

    /**
     * Gets all persistent user facts.
     */
    fun getUserFacts(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_USER_FACTS, emptySet()) ?: emptySet()
    }

    /**
     * Builds a context string from user facts.
     */
    fun getUserFactsContext(context: Context): String? {
        val facts = getUserFacts(context)
        if (facts.isEmpty()) return null
        return "Known facts about user: ${facts.joinToString("; ")}"
    }

    // --- Data Classes ---
    
    data class MessageSlot(
        val isUser: Boolean,
        val content: String
    )

    data class OptimizedContext(
        val messages: List<MessageSlot>,
        val estimatedTokens: Int,
        val messagesToSummarize: List<ChatMessage>? = null
    )
}
