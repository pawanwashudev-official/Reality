package com.neubofy.reality.google

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.R
import com.neubofy.reality.utils.SecurePreferences
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

object GoogleSignInHelper {

    fun startSignInFlow(activity: AppCompatActivity, isAllConnected: Boolean = false, forceBasicScope: Boolean? = null, skipDialog: Boolean = false, onSuccess: () -> Unit) {
        // As per new requirements, completely bypass the "Sign In Option" dialog and scope selection.
        // If forceBasicScope is true (Elite page), use basic scopes. 
        // If forceBasicScope is false or null (Profile page), use full scopes.
        val fullScopes = (forceBasicScope != true)
        performSignIn(activity, fullScopes, onSuccess)
    }

    fun showCloudKeySettings(activity: AppCompatActivity) {
        showCustomKeyDialog(activity, null) {}
    }

    private fun showScopeSelectionDialog(activity: AppCompatActivity, onSuccess: () -> Unit, defaultFullScope: Boolean) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Sign-In Scope")
            .setMessage("Do you want to sign in only for verifying user identity to get user ID, or sign in with full connections for Google Workspace?")
            .setPositiveButton("Verify Identity") { _, _ ->
                performSignIn(activity, fullScopes = false, onSuccess)
            }
            .setNegativeButton("Full Connection") { _, _ ->
                performSignIn(activity, fullScopes = true, onSuccess)
            }
            .show()
    }

    private fun showCustomKeyDialog(activity: AppCompatActivity, forceBasicScope: Boolean?, onSuccess: () -> Unit) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_cloud_settings, null)
        val etClientId = dialogView.findViewById<EditText>(R.id.et_client_id)
        val etClientSecret = dialogView.findViewById<EditText>(R.id.et_client_secret)

        val customId = GoogleAuthManager.getCustomClientId(activity)
        val customSecret = GoogleAuthManager.getCustomClientSecret(activity)

        if (!customId.isNullOrBlank()) {
            etClientId.setText(customId)
        } else {
            etClientId.setText("")
            if (GoogleAuthManager.getClientId(activity) != null) {
                etClientId.hint = "Developer Default Key In Use"
            }
        }

        if (!customSecret.isNullOrBlank()) {
            etClientSecret.setText(customSecret)
        } else {
            etClientSecret.setText("")
            if (GoogleAuthManager.getClientSecret(activity) != null) {
                etClientSecret.hint = "Developer Default Key In Use"
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Google Cloud Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val clientId = etClientId.text.toString().trim()
                val clientSecret = etClientSecret.text.toString().trim()
                GoogleAuthManager.saveCloudCredentials(activity, clientId, clientSecret)
                Toast.makeText(activity, "Credentials saved", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
            .setNeutralButton("Clear") { _, _ ->
                GoogleAuthManager.saveCloudCredentials(activity, "", "")
                Toast.makeText(activity, "Credentials cleared", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSignIn(activity: AppCompatActivity, fullScopes: Boolean, onSuccess: () -> Unit) {
        val url = GoogleAuthManager.getAuthUrl(activity, basicOnly = !fullScopes)
        if (url != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)

            // Start local server to listen for redirect (Auto-catching)
            activity.lifecycleScope.launch {
                val autoCode = GoogleAuthManager.startLocalServerAndGetCode()

                var success = false
                if (autoCode != null) {
                    success = GoogleAuthManager.exchangeCodeForTokens(activity, autoCode)
                    if (success) {
                        withContext(Dispatchers.Main) {
                            SecurePreferences.get(activity, "reality_features").edit()
                                .putBoolean("reality_pro_basic_sign_in", !fullScopes).apply()
                            TerminalLogger.log("GOOGLE AUTH: Auto-Signed in successfully")
                            Toast.makeText(activity, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        }
                    } else {
                        TerminalLogger.log("GOOGLE AUTH: Auto-Sign-in token exchange failed. Falling back to manual entry.")
                    }
                }

                if (!success) {
                    // Fallback to manual entry if server timed out or failed to catch/exchange
                    withContext(Dispatchers.Main) {
                        showManualCodeDialog(activity, fullScopes, onSuccess)
                    }
                }
            }
        } else {
            Toast.makeText(activity, "Error generating Auth URL. Check Keys.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showManualCodeDialog(activity: AppCompatActivity, fullScopes: Boolean, onSuccess: () -> Unit) {
        val input = EditText(activity)
        input.hint = "Paste URL or Code here"

        MaterialAlertDialogBuilder(activity)
            .setTitle("Enter Auth Code or URL")
            .setMessage("Auto-catch timed out or failed. If the browser shows 'Site can't be reached', copy the entire URL from the address bar and paste it here.")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                var code = input.text.toString().trim()
                if (code.contains("code=")) {
                    try {
                        val uri = Uri.parse(code)
                        code = uri.getQueryParameter("code") ?: code
                    } catch (e: Exception) {
                        val match = Regex("code=([^&]+)").find(code)
                        if (match != null) {
                            code = match.groupValues[1]
                            try {
                                code = URLDecoder.decode(code, "UTF-8")
                            } catch (e2: Exception) {}
                        }
                    }
                }
                if (code.isNotEmpty()) {
                    activity.lifecycleScope.launch {
                        val manualSuccess = GoogleAuthManager.exchangeCodeForTokens(activity, code)
                        withContext(Dispatchers.Main) {
                            if (manualSuccess) {
                                SecurePreferences.get(activity, "reality_features").edit()
                                    .putBoolean("reality_pro_basic_sign_in", !fullScopes).apply()
                                Toast.makeText(activity, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            } else {
                                Toast.makeText(activity, "Sign-in failed. Check credentials.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
