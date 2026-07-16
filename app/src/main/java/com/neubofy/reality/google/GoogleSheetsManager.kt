package com.neubofy.reality.google

import android.content.Context
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleSheetsManager {

    private fun getService(context: Context): Sheets? {
        val credential = GoogleAuthManager.getGoogleCredential(context) ?: return null
        return Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Reality").build()
    }

    val REQUIRED_HEADERS = listOf(
        "Date",
        "Tasks Completed",
        "Total Tasks",
        "Planned Sessions",
        "Tapasya Sessions",
        "Steps",
        "Sleep Info",
        "XP Tapasya",
        "XP Task",
        "XP Session",
        "XP Distraction",
        "XP Reflection",
        "XP Total",
        "Level",
        "Streak",
        "Diary Feedback"
    )

    suspend fun getHeaders(context: Context, spreadsheetId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val service = getService(context) ?: return@withContext emptyList()
            val range = "Sheet1!1:1"
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()
            if (values != null && values.isNotEmpty()) {
                values[0].map { it.toString() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            TerminalLogger.log("Sheets Error: Failed to get headers - ${e.message}")
            emptyList()
        }
    }

    suspend fun verifyAndCreateColumns(context: Context, spreadsheetId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getService(context) ?: return@withContext false

            // Get sheet values for the first row (headers)
            val range = "Sheet1!1:1"
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()

            var needsUpdate = false
            val currentHeaders = if (values != null && values.isNotEmpty()) {
                values[0].map { it.toString() }.toMutableList()
            } else {
                needsUpdate = true
                mutableListOf()
            }

            // Check missing headers
            for (header in REQUIRED_HEADERS) {
                if (!currentHeaders.contains(header)) {
                    currentHeaders.add(header)
                    needsUpdate = true
                }
            }

            if (needsUpdate) {
                val body = ValueRange().setValues(listOf(currentHeaders.toList() as List<Any>))
                service.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
                TerminalLogger.log("Sheets: Verified and updated headers")
            } else {
                TerminalLogger.log("Sheets: Headers already present")
            }

            true
        } catch (e: Exception) {
            TerminalLogger.log("Sheets Error: Failed to verify columns - ${e.message}")
            false
        }
    }
}
