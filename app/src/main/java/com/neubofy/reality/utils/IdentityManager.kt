package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.google.GoogleAuthManager
import kotlinx.coroutines.Dispatchers

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

        // Use applicationContext to prevent Activity leak from fire-and-forget coroutine
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { generateAndCacheIdentity(appContext) }
        return "Unknown"
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

        // Use applicationContext to prevent Activity leak from fire-and-forget coroutine
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { generateAndCacheIdentity(appContext) }
        return "Unknown"
    }

    suspend fun refreshIdentity(context: Context) {
        generateAndCacheIdentity(context)
    }

    private suspend fun generateAndCacheIdentity(context: Context) {
        withContext(Dispatchers.IO) {
            val email = GoogleAuthManager.getUserEmail(context) ?: ""
            val isSignedIn = GoogleAuthManager.isSignedIn(context) && email.isNotEmpty()
            val idToken = GoogleAuthManager.getIdToken(context)
            
            if (!isSignedIn || idToken.isNullOrBlank()) {
                // Auto remove all local subscription data if not signed in
                val featuresPrefs = SecurePreferences.get(context, "reality_features")
                val proPrefs = SecurePreferences.get(context, "reality_pro_prefs")
                
                val featureEditor = featuresPrefs.edit()
                featureEditor.putBoolean("feature_reality_pro", false)
                featuresPrefs.all.keys.filter { it.startsWith("feature_reality_pro_") }.forEach {
                    featureEditor.remove(it)
                }
                featureEditor.apply()
                
                val proEditor = proPrefs.edit()
                proPrefs.all.keys.filter { it.contains("pro_saved_verification_code_for_") || it.contains("is_registered_for_") }.forEach {
                    proEditor.remove(it)
                }
                proEditor.apply()
                
                clearIdentity(context)
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

                        val featuresPrefs = SecurePreferences.get(context, "reality_features")
                        val featuresEditor = featuresPrefs.edit()

                        val proPrefs = SecurePreferences.get(context, "reality_pro_prefs")
                        val proEditor = proPrefs.edit()
                        proEditor.putBoolean("is_registered_for_$userId", true)

                        // Parse status and update features locally to avoid multiple requests
                        val expiryDate = responseJson.optString("expiryDate")
                        val trialPlan = responseJson.optString("trial_plan")

                        if (status == "P" || status == "V") {
                            proEditor.putString("pro_saved_verification_code_for_$userId", "PENDING")
                        } else {
                            proEditor.remove("pro_saved_verification_code_for_$userId")
                        }

                        if (status == "V") {
                            // User is fully verified
                            featuresEditor.putBoolean("feature_reality_pro", true)

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

                                        featuresEditor.putLong("feature_reality_pro_start_time_$userId", startTime)
                                        featuresEditor.putLong("feature_reality_pro_verified_until_$userId", expiryUnix)
                                    }
                                } catch (e: Exception) {
                                    com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
                                }
                            }
                        } else {
                            // Reset local verification if not verified
                            featuresEditor.putBoolean("feature_reality_pro", false)
                            featuresEditor.remove("feature_reality_pro_start_time_$userId")
                            featuresEditor.remove("feature_reality_pro_verified_until_$userId")
                        }

                        if (trialPlan.isNotEmpty() && trialPlan != "null") {
                            try {
                                val parts = trialPlan.split("-")
                                if (parts.size >= 2) {
                                    val trialEndTime = parts[0].toLong()
                                    val days = parts[1].toLong()
                                    val trialStartTime = trialEndTime - (days * 24 * 60 * 60 * 1000)
                                    featuresEditor.putLong("trial_end_time_$userId", trialEndTime)
                                    featuresEditor.putLong("trial_start_time_$userId", trialStartTime)
                                }
                            } catch (e: Exception) {
                                com.neubofy.reality.utils.TerminalLogger.log("ERROR parsing trialPlan: ${e.message}")
                            }
                        }

                        featuresEditor.apply()
                        proEditor.apply()
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
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
