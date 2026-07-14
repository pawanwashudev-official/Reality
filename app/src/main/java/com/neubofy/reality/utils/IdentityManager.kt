package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.google.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object IdentityManager {

    private const val PREFS_NAME = "reality_identity_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BACKUP_PASSWORD = "backup_password"

    /**
     * Fetches the cached userId. If it doesn't exist, synchronously calls the API to generate and cache it.
     */
    fun getUserId(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cachedId = prefs.getString(KEY_USER_ID, null)
        if (cachedId != null) {
            return cachedId
        }

        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { generateAndCacheIdentity(context); prefs.getString(KEY_USER_ID, "Unknown") ?: "Unknown" }
    }

    /**
     * Fetches the cached backupPassword. If it doesn't exist, synchronously calls the API to generate and cache it.
     */
    fun getBackupPassword(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cachedPassword = prefs.getString(KEY_BACKUP_PASSWORD, null)
        if (cachedPassword != null) {
            return cachedPassword
        }

        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { generateAndCacheIdentity(context); prefs.getString(KEY_BACKUP_PASSWORD, "Unknown") ?: "Unknown" }
    }

    suspend fun refreshIdentity(context: Context) {
        generateAndCacheIdentity(context)
    }

    private suspend fun generateAndCacheIdentity(context: Context) {
        withContext(Dispatchers.IO) {
            val idToken = GoogleAuthManager.getIdToken(context)
            if (idToken.isNullOrBlank()) {
                return@withContext
            }

            try {
                val workerUrl = BuildConfig.WORKER_URL.removeSuffix("/")
                val url = URL("$workerUrl/api/generate-identity")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonInputString = JSONObject().apply {
                    put("idToken", idToken)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseStr)

                    val userId = responseJson.optString("userId")
                    val backupPassword = responseJson.optString("backupPassword")
                    val status = responseJson.optString("status")

                    if (userId.isNotEmpty() && backupPassword.isNotEmpty()) {
                        SecurePreferences.get(context, PREFS_NAME).edit().apply {
                            putString(KEY_USER_ID, userId)
                            putString(KEY_BACKUP_PASSWORD, backupPassword)
                            apply()
                        }

                        val proPrefs = SecurePreferences.get(context, "reality_pro_prefs")
                        val editor = proPrefs.edit()
                        editor.putBoolean("is_registered_for_$userId", true)

                        // Parse status and update features locally to avoid multiple requests
                        val status = responseJson.optString("status")
                        val expiryDate = responseJson.optString("expiryDate")

                        if (status == "V") {
                            // User is fully verified
                            FeatureManager(context).setRealityProEnabled(true)

                            // If we have expiryDate, try to parse it
                            if (expiryDate.isNotEmpty() && expiryDate != "null") {
                                try {
                                    val parts = expiryDate.split("-")
                                    if (parts.size >= 2) {
                                        val expiryUnix = parts[0].toLong()
                                        val months = parts[1].toLong()

                                        // App uses durationMs = (365L / 12) * months * 24 * 60 * 60 * 1000
                                        val durationMs = (365L / 12) * months * 24 * 60 * 60 * 1000
                                        val startTime = expiryUnix - durationMs

                                        editor.putLong("feature_reality_pro_start_time_$userId", startTime)
                                        editor.putLong("feature_reality_pro_end_time_$userId", expiryUnix)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            // Reset local verification if not verified
                            FeatureManager(context).setRealityProEnabled(false)
                        }

                        editor.apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearIdentity(context: Context) {
        SecurePreferences.get(context, PREFS_NAME).edit().clear().apply()
    }
    fun getAndIncrementDailyAICount(context: Context): Int {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()
        val lastDate = prefs.getString("ai_request_date", "")

        var count = prefs.getInt("ai_request_count", 0)

        if (today != lastDate) {
            count = 0
            prefs.edit().putString("ai_request_date", today).apply()
        }

        count++
        prefs.edit().putInt("ai_request_count", count).apply()

        return count
    }
}
