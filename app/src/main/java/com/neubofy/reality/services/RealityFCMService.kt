package com.neubofy.reality.services

import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * RealityFCMService
 *
 * Handles Firebase Cloud Messaging push notifications.
 *
 * Silent push from Notification Worker:
 *   data: { "action": "SYNC_CALENDAR" }
 *   → Triggers CalendarSyncWorker to fetch latest events from Google Calendar API
 *
 * Token lifecycle:
 *   - onNewToken() is called on first launch and whenever Firebase refreshes the token
 *   - Token is persisted in SharedPreferences and uploaded to the Notification Worker
 */
class RealityFCMService : FirebaseMessagingService() {

    companion object {
        const val PREF_FCM_TOKEN = "reality_fcm_token"
        private val httpClient = OkHttpClient()

        /**
         * Register the device's FCM token with the Reality Notification Worker.
         * Called after login (when userId/backupPassword are available) and on token refresh.
         */
        fun registerTokenWithWorker(
            notificationWorkerUrl: String,
            userId: String,
            backupPassword: String,
            fcmToken: String
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("backupPassword", backupPassword)
                        put("fcmToken", fcmToken)
                    }

                    val body = json.toString()
                        .toRequestBody("application/json".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url("$notificationWorkerUrl/api/register-fcm-token")
                        .post(body)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        TerminalLogger.log("FCM: Token registered with notification worker successfully")
                    } else {
                        TerminalLogger.log("FCM: Token registration failed (${response.code}): $responseBody")
                    }
                } catch (e: Exception) {
                    TerminalLogger.log("FCM: Error registering token: ${e.message}")
                }
            }
        }

        /**
         * Register the Google Calendar webhook with Google's API.
         * The userId is embedded as the channel token so the notification worker
         * can identify which user's calendar changed.
         *
         * Call this after the user enables "Calendar Auto-Sync" in settings.
         */
        fun registerCalendarWebhook(
            notificationWorkerUrl: String,
            userId: String,
            googleAccessToken: String
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val channelId = "reality-channel-$userId"
                    val channelToken = "reality-$userId"

                    val json = JSONObject().apply {
                        put("id", channelId)
                        put("type", "web_hook")
                        put("address", "$notificationWorkerUrl/webhook/calendar")
                        put("token", channelToken)
                    }

                    val body = json.toString()
                        .toRequestBody("application/json".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url("https://www.googleapis.com/calendar/v3/calendars/primary/events/watch")
                        .addHeader("Authorization", "Bearer $googleAccessToken")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        TerminalLogger.log("FCM: Google Calendar webhook registered. Channel: $channelId")
                    } else {
                        TerminalLogger.log("FCM: Webhook registration failed (${response.code}): $responseBody")
                    }
                } catch (e: Exception) {
                    TerminalLogger.log("FCM: Webhook registration error: ${e.message}")
                }
            }
        }
    }

    /**
     * Called when Firebase generates a new device token.
     * Stores it locally and re-registers with the notification worker if credentials exist.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        TerminalLogger.log("FCM: New token generated")

        // Save token locally
        val prefs: SharedPreferences = getSharedPreferences("reality_prefs", MODE_PRIVATE)
        prefs.edit().putString(PREF_FCM_TOKEN, token).apply()

        // Re-register with notification worker if user is already authenticated
        val userId = prefs.getString("reality_user_id", null)
        val backupPassword = prefs.getString("reality_backup_password", null)
        val workerUrl = com.neubofy.reality.BuildConfig.NOTIFICATION_WORKER_URL
        val isAutoSyncEnabled = prefs.getBoolean("calendar_sync_auto_enabled", false)

        if (!userId.isNullOrEmpty() && !backupPassword.isNullOrEmpty()
            && workerUrl.isNotEmpty() && isAutoSyncEnabled
        ) {
            registerTokenWithWorker(workerUrl, userId, backupPassword, token)
        }
    }

    /**
     * Called when a silent FCM message is received from the notification worker.
     * Triggers a one-time CalendarSyncWorker to fetch updated events.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val action = remoteMessage.data["action"]
        TerminalLogger.log("FCM: Message received. Action: $action")

        if (action == "SYNC_CALENDAR") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.CalendarSyncWorker>()
                        .addTag("fcm_triggered_calendar_sync")
                        .build()
                    androidx.work.WorkManager.getInstance(applicationContext).enqueue(syncRequest)
                    TerminalLogger.log("FCM: CalendarSyncWorker enqueued from FCM push")
                } catch (e: Exception) {
                    TerminalLogger.log("FCM: Failed to enqueue CalendarSyncWorker: ${e.message}")
                }
            }
        }
    }
}
