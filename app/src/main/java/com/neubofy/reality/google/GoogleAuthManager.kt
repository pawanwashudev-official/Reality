package com.neubofy.reality.google

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.tasks.TasksScopes
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized Google Authentication Manager.
 * 
 * Handles:
 * - Google Sign-In using Credential Manager
 * - OAuth token management
 * - API scopes for Tasks, Drive, Docs, Calendar
 */
object GoogleAuthManager {
    
    // Web Client ID from Google Cloud Console
    private const val WEB_CLIENT_ID = "163374197397-mkmhn9trpthu5f9imrkgtjo52rrnb1sh.apps.googleusercontent.com"
    
    private const val PREF_NAME = "google_auth_prefs"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"
    private const val KEY_USER_PHOTO_URL = "user_photo_url"
    private const val KEY_IS_SIGNED_IN = "is_signed_in"
    private const val APPLICATION_NAME = "com.neubofy.reality"
    
    // All scopes we need for full Google integration
    val ALL_SCOPES = listOf(
        CalendarScopes.CALENDAR,           // Full calendar access
        TasksScopes.TASKS,                 // Google Tasks
        DriveScopes.DRIVE_FILE,            // Drive files created by app
        DocsScopes.DOCUMENTS               // Google Docs
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
    
    /**
     * Sign in with Google using Credential Manager.
     * 
     * @return GoogleIdTokenCredential on success, null on failure
     */
    suspend fun signIn(activity: Activity): GoogleIdTokenCredential? {
        return withContext(Dispatchers.IO) {
            val credentialManager = CredentialManager.create(activity)
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            
            try {
                val result = credentialManager.getCredential(
                    context = activity,
                    request = request
                )
                handleSignInResult(activity, result)
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                TerminalLogger.log("GOOGLE AUTH: User cancelled sign-in")
                null 
            } catch (e: Exception) {
                TerminalLogger.log("GOOGLE AUTH: Sign-in failed - ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }
    
    private fun handleSignInResult(context: Context, result: GetCredentialResponse): GoogleIdTokenCredential? {
        val credential = result.credential
        
        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    
                    // In Credential Manager, the 'id' field is typically the email address
                    val email = googleIdTokenCredential.id?.trim()
                    val name = googleIdTokenCredential.displayName
                    val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    
                    TerminalLogger.log("GOOGLE AUTH: Sign-in Success! Email: '$email', Name: '$name'")
                    
                    if (email.isNullOrEmpty()) {
                        TerminalLogger.log("GOOGLE AUTH: ERROR - Email is totally empty! Google didn't return one.")
                        return null
                    }
                    
                    // Save user info
                    getPrefs(context).edit().apply {
                        putBoolean(KEY_IS_SIGNED_IN, true)
                        putString(KEY_USER_EMAIL, email)
                        putString(KEY_USER_NAME, name)
                        putString(KEY_USER_PHOTO_URL, photoUrl)
                        apply()
                    }
                    
                    TerminalLogger.log("GOOGLE AUTH: Saved to prefs - signed in as $email")
                    googleIdTokenCredential
                } else {
                    TerminalLogger.log("GOOGLE AUTH: Unexpected credential type: ${credential.type}")
                    null
                }
            }
            else -> {
                TerminalLogger.log("GOOGLE AUTH: Unexpected credential class: ${credential::class.java}")
                null
            }
        }
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut(context: Context) {
        getPrefs(context).edit().clear().apply()
        // Also clear connector prefs
        context.getSharedPreferences("google_connector_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        TerminalLogger.log("GOOGLE AUTH: Signed out and cleared connections")
    }
    
    /**
     * Get GoogleAccountCredential for API calls.
     * Must be called after user is signed in.
     */
    /**
     * Get GoogleAccountCredential for API calls.
     * Updated to manually create the Account object to bypass account visibility issues.
     */
    fun getGoogleAccountCredential(context: Context, accountEmail: String? = null): GoogleAccountCredential? {
        val appContext = context.applicationContext
        val email = (accountEmail ?: getUserEmail(appContext))?.trim()?.replace("\n", "")
        
        if (email.isNullOrEmpty()) {
            TerminalLogger.log("GOOGLE AUTH: CRITICAL - No email found for connection")
            return null
        }
        
        return try {
            // 1. Create the credential wrapper
            val credential = GoogleAccountCredential.usingOAuth2(appContext, ALL_SCOPES)
            
            // 2. CRITICAL FIX: Manually create the Account object.
            // This bypasses the library's attempt to 'look up' the account in settings.
            val account = android.accounts.Account(email, "com.google")
            
            // 3. Set the account object directly
            credential.selectedAccount = account
            
            TerminalLogger.log("GOOGLE AUTH: Bridge created for [$email]")
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
     * Update the Web Client ID.
     * Call this with your actual client ID.
     */
    fun setWebClientId(clientId: String) {
        // In production, this should be a build config or resource
        // For now, we'll use a placeholder
    }
}
