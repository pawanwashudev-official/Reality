package com.neubofy.reality.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedList

object TerminalLogger {
    private val _logs = MutableStateFlow<String>("> System Initialized...")
    val logs = _logs.asStateFlow()
    
    // Keep last 15 lines
    private val logList = LinkedList<String>()

    init {
        logList.add("> System Initialized...")
    }

    // Basic redaction for sensitive keys in logs
    private fun redact(msg: String): String {
        var redacted = msg
        // Redact Bearer tokens
        redacted = redacted.replace(Regex("Bearer [a-zA-Z0-9\\-_\\.]+"), "Bearer [REDACTED]")
        // Redact API keys (sk-...)
        redacted = redacted.replace(Regex("sk-[a-zA-Z0-9]{20,}"), "sk-[REDACTED]")
        // Redact generic tokens if labeled
        redacted = redacted.replace(Regex("(?i)(token|key)=([a-zA-Z0-9\\-_]+)"), "$1=[REDACTED]")
        return redacted
    }

    fun log(msg: String) {
        val safeMsg = redact(msg)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "> [$time] $safeMsg"
        
        // Logs are strictly local
        android.util.Log.d("RealityDebug", safeMsg)
        
        synchronized(logList) {
            logList.add(entry)
            if (logList.size > 15) logList.removeFirst()
            _logs.value = logList.joinToString("\n")
        }
    }
    
    fun clear() {
        synchronized(logList) {
            logList.clear()
            _logs.value = ""
        }
    }
}
