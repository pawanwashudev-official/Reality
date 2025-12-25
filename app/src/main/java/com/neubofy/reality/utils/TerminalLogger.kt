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

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "> [$time] $msg"
        
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
