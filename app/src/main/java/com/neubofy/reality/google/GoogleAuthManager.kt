package com.neubofy.reality.google

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.tasks.TasksScopes
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized Google Authentication Manager.
 * 
 * Handles:
 * - Google Sign-In using OAuth
 * - OAuth token management
 * - API scopes for Tasks, Drive, Docs, Calendar
 */
object GoogleAuthManager {
    
    private const val PREF_NAME = "google_auth_prefs"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"
    private const val KEY_USER_PHOTO_URL = "user_photo_url"
    private const val KEY_IS_SIGNED_IN = "is_signed_in"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val APPLICATION_NAME = "com.neubofy.reality"
    
    // All scopes we need for full Google integration
    val ALL_SCOPES = listOf(
        CalendarScopes.CALENDAR,           // Full calendar access
        TasksScopes.TASKS,                 // Google Tasks
        DriveScopes.DRIVE_FILE,            // Drive files created by app
        DocsScopes.DOCUMENTS,              // Google Docs
        SheetsScopes.SPREADSHEETS          // Google Sheets
    )
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if user is signed in.
     */
    fun isSignedIn(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isSignedIn = prefs.getBoolean(KEY_IS_SIGNED_IN, false)
        val email = prefs.getString(KEY_USER_EMAIL, null)
        TerminalLogger.log("GOOGLE AUTH CHECK: isSignedIn=$isSignedIn, email=$email")
        return isSignedIn
    }
    
    /**
     * Get signed-in user's email.
     */
    fun getUserEmail(context: Context): String? {
        val email = getPrefs(context).getString(KEY_USER_EMAIL, null)
        return email
    }
    
    /**
     * Get signed-in user's display name.
     */
    fun getUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }
    
    /**
     * Get signed-in user's photo URL.
     */
    fun getUserPhotoUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_PHOTO_URL, null)
    }

    fun saveClientCredentials(context: Context, clientId: String, clientSecret: String) {
        getPrefs(context).edit().apply {
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_CLIENT_SECRET, clientSecret)
            apply()
        }
    }

    fun getClientId(context: Context): String? {
        return getPrefs(context).getString(KEY_CLIENT_ID, null)
    }

    fun getClientSecret(context: Context): String? {
        return getPrefs(context).getString(KEY_CLIENT_SECRET, null)
    }

    fun saveTokens(context: Context, accessToken: String, refreshToken: String?) {
        getPrefs(context).edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putBoolean(KEY_IS_SIGNED_IN, true)
            apply()
        }
    }

    fun getAccessToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Generates the OAuth authorization URL based on saved Client ID.
     */
    fun getAuthorizationUrl(context: Context): String? {
        val clientId = getClientId(context) ?: return null
        val redirectUri = "http://127.0.0.1" // Localhost flow (OOB is deprecated)
        val scopesStr = ALL_SCOPES.joinToString(" ")

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=code&" +
                "scope=${android.net.Uri.encode(scopesStr)}&" +
                "access_type=offline&" +
                "prompt=consent"
    }

    
    /**
     * Sign out the current user.
     */
    fun signOut(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_IS_SIGNED_IN)
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_PHOTO_URL)
            // Note: We intentionally do not remove KEY_CLIENT_ID and KEY_CLIENT_SECRET
            // so the user does not have to re-enter them if they sign out and back in.
            apply()
        }
        // Also clear connector prefs
        context.getSharedPreferences("google_connector_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        TerminalLogger.log("GOOGLE AUTH: Signed out and cleared connections")
    }
    
    /**
     * Get GoogleCredential for API calls using Desktop Client credentials.
     * Must be called after user is signed in and tokens are saved.
     */
    fun getGoogleAccountCredential(context: Context, accountEmail: String? = null): GoogleCredential? {
        val appContext = context.applicationContext
        
        val accessToken = getAccessToken(appContext)
        val refreshToken = getRefreshToken(appContext)
        val clientId = getClientId(appContext)
        val clientSecret = getClientSecret(appContext)

        if (accessToken.isNullOrEmpty() && refreshToken.isNullOrEmpty()) {
            TerminalLogger.log("GOOGLE AUTH: CRITICAL - No tokens found")
            return null
        }
        
        return try {
            val credential = GoogleCredential.Builder()
                .setTransport(getHttpTransport())
                .setJsonFactory(getJsonFactory())
                .setClientSecrets(clientId, clientSecret)
                .build()

            credential.accessToken = accessToken
            if (!refreshToken.isNullOrEmpty()) {
                credential.refreshToken = refreshToken
            }
            
            TerminalLogger.log("GOOGLE AUTH: Desktop Client Bridge created")
            credential
        } catch (t: Throwable) {
            TerminalLogger.log("GOOGLE AUTH: Bridge Failed - ${t.message}")
            null
        }
    }
    
    /**
     * Create HTTP transport for Google APIs.
     * Using AndroidHttp for maximum compatibility and to avoid initializer crashes.
     */
    fun getHttpTransport() = com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport()
    
    /**
     * Create JSON factory for Google APIs.
     */
    fun getJsonFactory() = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
    
    
    /**
     * Check if the current credential has all required scopes.
     * Note: This is an optimistic check. The actual token might still be rejected if revoked externally.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        if (!isSignedIn(context)) return false
        
        // In a real OAuth flow we'd check granted scopes from the token response.
        // For simple Google Sign-In + Account Manager, we assume yes if signed in,
        // but we rely on the Activity to trigger a re-auth if API calls fail with 401/403.
        // However, we can basic check if we have an email.
        return !getUserEmail(context).isNullOrEmpty()
    }
}
