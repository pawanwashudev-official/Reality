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
import android.widget.Toast

object IdentityManager {

    data class IdentityResult(
        val userId: String,
        val backupPassword: String,
        val connectionSecretMasked: String,
        val activeExpiry: String,
        val activeDuration: String,
        val activeStatus: String,
        val planType: String
    )

    private const val PREFS_NAME = "reality_identity_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_CONNECTION_SECRET = "backup_password" // stored key unchanged for backward compat
    private const val KEY_BACKUP_PASSWORD = "backup_key" // stored key unchanged for backward compat
    private const val KEY_ACTIVE_EXPIRY = "active_expiry"
    private const val KEY_ACTIVE_DURATION = "active_duration"
    private const val KEY_ACTIVE_STATUS = "active_status"
    private const val KEY_ACTIVE_PLAN_TYPE = "active_plan_type"

    fun getActiveExpiry(context: Context): String {
        return SecurePreferences.get(context, PREFS_NAME).getString(KEY_ACTIVE_EXPIRY, "0") ?: "0"
    }

    fun getActiveDuration(context: Context): String {
        return SecurePreferences.get(context, PREFS_NAME).getString(KEY_ACTIVE_DURATION, "0") ?: "0"
    }

    fun getActiveStatus(context: Context): String {
        return SecurePreferences.get(context, PREFS_NAME).getString(KEY_ACTIVE_STATUS, "N") ?: "N"
    }

    fun getActivePlanType(context: Context): String {
        return SecurePreferences.get(context, PREFS_NAME).getString(KEY_ACTIVE_PLAN_TYPE, "none") ?: "none"
    }

    fun updateCredentials(
        context: Context,
        newConnectionSecret: String,
        newBackupPassword: String,
        activeExpiry: String,
        activeDuration: String,
        activeStatus: String,
        planType: String
    ) {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val editor = prefs.edit()
        editor.putString(KEY_CONNECTION_SECRET, newConnectionSecret)
        if (newBackupPassword.isNotEmpty()) {
            editor.putString(KEY_BACKUP_PASSWORD, newBackupPassword)
        }
        editor.putString(KEY_ACTIVE_EXPIRY, activeExpiry)
        editor.putString(KEY_ACTIVE_DURATION, activeDuration)
        editor.putString(KEY_ACTIVE_STATUS, activeStatus)
        editor.putString(KEY_ACTIVE_PLAN_TYPE, planType)
        editor.apply()
    }

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
        return ""
    }

    /**
     * Static backup password used for Google Drive encryption.
     * Unique per user, never changes. Falls back to connectionSecret for backward compat.
     */
    fun getBackupPassword(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cached = prefs.getString(KEY_BACKUP_PASSWORD, null)
        if (!cached.isNullOrEmpty()) {
            return cached
        }
        return getConnectionSecret(context)
    }

    /**
     * Rotating connection secret (HMAC hash) used for secure app-to-server auth.
     * Auto-rotates when membership state changes. Never expose to user.
     */
    fun getConnectionSecret(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cached = prefs.getString(KEY_CONNECTION_SECRET, null)
        if (cached != null) {
            return cached
        }

        // Use applicationContext to prevent Activity leak from fire-and-forget coroutine
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { generateAndCacheIdentity(appContext) }
        return ""
    }

    suspend fun refreshIdentity(context: Context, isManualTrigger: Boolean = false): IdentityResult? {
        if (isManualTrigger) {
            if (!checkRateLimit(context)) {
                throw Exception("RATE_LIMIT")
            }
        }
        return generateAndCacheIdentity(context)
    }

    private suspend fun checkRateLimit(context: Context): Boolean {
        val prefs = SecurePreferences.get(context, "rate_limit_prefs")
        val now = System.currentTimeMillis()
        
        val timestampsStr = prefs.getString("refresh_timestamps", "") ?: ""
        val timestamps = timestampsStr.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }.toMutableList()
        
        // Remove timestamps older than 1 hour
        timestamps.removeAll { now - it > 60 * 60 * 1000 }
        
        // Check limits
        // 1 min max 3
        val count1Min = timestamps.count { now - it <= 60_000 }
        if (count1Min >= 3) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please wait a minute before refreshing again.", Toast.LENGTH_LONG).show()
            }
            return false
        }
        
        // 5 min max 5
        val count5Min = timestamps.count { now - it <= 300_000 }
        if (count5Min >= 5) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please wait a few minutes before refreshing again.", Toast.LENGTH_LONG).show()
            }
            return false
        }
        
        // 1 hour max 10
        val count1Hour = timestamps.count { now - it <= 3_600_000 }
        if (count1Hour >= 10) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Rate limit exceeded. Try again in an hour.", Toast.LENGTH_LONG).show()
            }
            return false
        }
        
        timestamps.add(now)
        prefs.edit().putString("refresh_timestamps", timestamps.joinToString(",")).apply()
        return true
    }

    private suspend fun generateAndCacheIdentity(context: Context): IdentityResult? {
        return withContext(Dispatchers.IO) {
            val email = GoogleAuthManager.getUserEmail(context) ?: ""
            val isSignedIn = GoogleAuthManager.isSignedIn(context) && email.isNotEmpty()
            
            // Refresh token before fetching identity
            if (isSignedIn) {
                GoogleAuthManager.refreshTokenIfNeeded(context)
            }
            
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
                return@withContext null
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
                    // connectionSecret = rotating HMAC (server field: "connectionSecret" or legacy "backupPassword"/"password")
                    val connectionSecret = responseJson.optString("connectionSecret").takeIf { it.isNotEmpty() }
                        ?: responseJson.optString("backupPassword").takeIf { it.isNotEmpty() }
                        ?: responseJson.optString("password")
                    // backupPassword = static key (server field: "backupPassword" in new, "backupKey" in legacy)
                    val backupPassword = responseJson.optString("backupPassword").takeIf { it.isNotEmpty() }
                        ?: responseJson.optString("backupKey", "")
                    val status = responseJson.optString("status")
                    val activeExpiry = responseJson.optString("activeExpiry", "0")
                    val activeDuration = responseJson.optString("activeDuration", "0")
                    val activeStatus = responseJson.optString("activeStatus", "N")
                    val planType = responseJson.optString("planType", "none")

                    if (userId.isNotEmpty()) {
                        val prefs = SecurePreferences.get(context, PREFS_NAME)
                        val editor = prefs.edit()
                        editor.putString(KEY_USER_ID, userId)
                        if (connectionSecret.isNotEmpty()) {
                            editor.putString(KEY_CONNECTION_SECRET, connectionSecret)
                        }
                        if (backupPassword.isNotEmpty()) {
                            editor.putString(KEY_BACKUP_PASSWORD, backupPassword)
                        }
                        editor.putString(KEY_ACTIVE_EXPIRY, activeExpiry)
                        editor.putString(KEY_ACTIVE_DURATION, activeDuration)
                        editor.putString(KEY_ACTIVE_STATUS, activeStatus)
                        editor.putString(KEY_ACTIVE_PLAN_TYPE, planType)
                        editor.apply()

                        val featuresPrefs = SecurePreferences.get(context, "reality_features")
                        val featuresEditor = featuresPrefs.edit()

                        val proPrefs = SecurePreferences.get(context, "reality_pro_prefs")
                        val proEditor = proPrefs.edit()
                        proEditor.putBoolean("is_registered_for_$userId", true)

                        if (status == "P" || status == "V") {
                            proEditor.putString("pro_saved_verification_code_for_$userId", "PENDING")
                        } else {
                            proEditor.remove("pro_saved_verification_code_for_$userId")
                        }

                        // Clear prior settings to avoid caching obsolete state
                        featuresEditor.putBoolean("feature_reality_pro", false)
                        featuresEditor.remove("feature_reality_pro_start_time_$userId")
                        featuresEditor.remove("feature_reality_pro_verified_until_$userId")
                        featuresEditor.remove("trial_start_time_$userId")
                        featuresEditor.remove("trial_end_time_$userId")

                        if (activeStatus == "V") {
                            try {
                                val expiryUnix = activeExpiry.toLong()
                                if (expiryUnix > System.currentTimeMillis()) {
                                    featuresEditor.putBoolean("feature_reality_pro", true)
                                    val duration = activeDuration.toLong()
                                    if (planType == "paid") {
                                        // Paid subscription (duration is in months, e.g. 12 months)
                                        val durationMs = (365L / 12) * duration * 24 * 60 * 60 * 1000
                                        val startTime = expiryUnix - durationMs
                                        featuresEditor.putLong("feature_reality_pro_start_time_$userId", startTime)
                                        featuresEditor.putLong("feature_reality_pro_verified_until_$userId", expiryUnix)
                                    } else {
                                        // Trial subscription (duration is 3 days)
                                        val startTime = expiryUnix - (duration * 24 * 60 * 60 * 1000)
                                        featuresEditor.putLong("trial_end_time_$userId", expiryUnix)
                                        featuresEditor.putLong("trial_start_time_$userId", startTime)
                                    }
                                }
                            } catch (e: Exception) {
                                com.neubofy.reality.utils.TerminalLogger.log("ERROR parsing active subscription: ${e.message}")
                            }
                        }

                        featuresEditor.apply()
                        proEditor.apply()

                        val intent = android.content.Intent("com.neubofy.reality.IDENTITY_UPDATED").apply {
                            putExtra("userId", userId)
                            putExtra("backupPassword", backupPassword.ifEmpty { connectionSecret })
                            putExtra("activeExpiry", activeExpiry)
                            putExtra("activeDuration", activeDuration)
                            putExtra("activeStatus", activeStatus)
                            putExtra("planType", planType)
                        }
                        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

                        return@withContext IdentityResult(
                            userId = userId,
                            backupPassword = backupPassword.ifEmpty { connectionSecret },
                            connectionSecretMasked = "•".repeat(32),
                            activeExpiry = activeExpiry,
                            activeDuration = activeDuration,
                            activeStatus = activeStatus,
                            planType = planType
                        )
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
            }
            null
        }
    }

    fun clearIdentity(context: Context) {
        SecurePreferences.get(context, PREFS_NAME).edit().clear().apply()
    }
    fun getAndIncrementDailyAICount(context: Context): Int {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, "ai_prefs")
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
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
