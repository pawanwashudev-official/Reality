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
        val credential = GoogleAuthManager.getGoogleAccountCredential(context) ?: return null
        return Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Reality").build()
    }

    val REQUIRED_HEADERS = listOf(
        "Date",
        "Step1_Tasks",
        "Step2_SessionsCount",
        "Step2_TotalMins",
        "Step3_ScreenTime",
        "Step3_PhoneTotal",
        "Step3_Steps",
        "Step3_SleepMins",
        "Step3_RealityRatio",
        "Step6_Feedback",
        "XP_Tapasya",
        "XP_Task",
        "XP_Session",
        "XP_Distraction",
        "XP_Reflection",
        "XP_Total",
        "Level",
        "Streak",
        "Plan_Doc_Link",
        "Report_PDF_Link"
    )

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

            // Remove old Q/A headers
            val oldHeaders = listOf("Q1", "A1", "Q2", "A2", "Q3", "A3", "Q4", "A4", "Q5", "A5", "Q6", "A6")
            var hasOldHeaders = false
            for (header in oldHeaders) {
                if (currentHeaders.contains(header)) {
                    hasOldHeaders = true
                    break
                }
            }

            if (hasOldHeaders) {
                currentHeaders.clear()
                needsUpdate = true
                for (header in REQUIRED_HEADERS) {
                    currentHeaders.add(header)
                }
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
