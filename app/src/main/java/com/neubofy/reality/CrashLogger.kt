package com.neubofy.reality

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val logFile = File(context.filesDir, "crash_log.txt")
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    // Basic redaction for sensitive keys in crash logs
    private fun redact(msg: String?): String {
        if (msg == null) return "null"
        var redacted = msg
        redacted = redacted.replace(Regex("Bearer [a-zA-Z0-9\\-_\\.]+"), "Bearer [REDACTED]")
        redacted = redacted.replace(Regex("sk-[a-zA-Z0-9]{20,}"), "sk-[REDACTED]")
        redacted = redacted.replace(Regex("(?i)(token|key)=([a-zA-Z0-9\\-_]+)"), "$1=[REDACTED]")
        return redacted
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val safeMessage = redact(throwable.message)
            // Log to in-app terminal first (TerminalLogger also redacts, but we send safe message)
            com.neubofy.reality.utils.TerminalLogger.log("FATAL CRASH: $safeMessage")
            com.neubofy.reality.utils.TerminalLogger.log("Thread: ${thread.name}")
            
            val timeStamp =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val writer = FileWriter(logFile, true) // Append mode
            writer.append("\n--- Crash at $timeStamp ---\n")
            // Crash logs are strictly local
            writer.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")

            // We cannot easily intercept printStackTrace to stream, but throwable message is the most likely leak
            // For rigorous redaction we would intercept the writer stream, but this is a good start.

            val stringWriter = java.io.StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val safeStackTrace = redact(stringWriter.toString())

            writer.append(safeStackTrace)
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
        }

        defaultHandler?.uncaughtException(thread, throwable) // Let the system handle the crash
    }
}
