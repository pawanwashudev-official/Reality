package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.R
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.FeatureManager
import com.neubofy.reality.utils.IdentityManager
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RealityEliteActivity : BaseActivity() {

    private lateinit var btnUnifiedSignin: MaterialButton
    private lateinit var cardStep2: MaterialCardView
    private lateinit var btnPayUpi: MaterialButton
    private lateinit var cardStep3: MaterialCardView
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSyncIdentity: MaterialButton
    private lateinit var spinnerDuration: android.widget.AutoCompleteTextView
    private var selectedMonths = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reality_elite)

        findViewById<android.view.View>(R.id.btn_view_pro_members)?.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://reality.neubofy.in/promembers"))
            startActivity(intent)
        }

        btnUnifiedSignin = findViewById(R.id.btn_unified_signin)
        
        findViewById<android.view.View>(R.id.btn_why_google)?.setOnClickListener {
            startActivity(android.content.Intent(this, WhyGooglePermissionActivity::class.java))
        }

        cardStep2 = findViewById(R.id.card_step2)
        btnPayUpi = findViewById(R.id.btn_pay_upi)
        cardStep3 = findViewById(R.id.card_step3)
        btnVerify = findViewById(R.id.btn_verify)
        btnCancel = findViewById(R.id.btn_cancel)
        btnSyncIdentity = findViewById(R.id.btn_sync_identity)
        spinnerDuration = findViewById(R.id.spinner_duration)

        val monthsOptions = (1..36).map { "$it Months" }.toTypedArray()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monthsOptions)
        spinnerDuration.setAdapter(adapter)

        spinnerDuration.setOnItemClickListener { _, _, position, _ ->
            selectedMonths = position + 1
            updateUpiButtonText()
        }




        btnUnifiedSignin.setOnClickListener {
            showKeySelectionDialog()
        }

        btnSyncIdentity.setOnClickListener {
            btnSyncIdentity.text = "Syncing..."
            btnSyncIdentity.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    IdentityManager.refreshIdentity(this@RealityEliteActivity)
                } catch (e: Exception) {
                    com.neubofy.reality.utils.TerminalLogger.log("Manual sync error: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    btnSyncIdentity.text = "Sync Identity"
                    btnSyncIdentity.isEnabled = true
                    updateStateUI()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@RealityEliteActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        })

        btnPayUpi.setOnClickListener {
            showUpiPaymentDialog()
        }

        btnVerify.setOnClickListener {
            showVerifyDialog()
        }

        btnCancel.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }



    private fun updateUpiButtonText() {
        if (!GoogleAuthManager.isSignedIn(this)) return
        val price = ((99.0 / 12.0) * selectedMonths).roundToInt()
        btnPayUpi.text = "UPI (₹$price)"
    }

    private fun showKeySelectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign In Option")
            .setMessage("Use your own Google Cloud credentials or use Developer Default keys.")
            .setPositiveButton("Default Key") { _, _ ->
                showScopeSelectionDialog()
            }
            .setNeutralButton("Own Key") { _, _ ->
                showCustomKeyDialog()
            }
            .show()
    }

    private fun showScopeSelectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign-In Scope")
            .setMessage("Do you want to sign in only for verifying user identity to get user ID, or sign in with full connections for Google Workspace?")
            .setPositiveButton("Verify Identity") { _, _ ->
                performSignIn(fullScopes = false)
            }
            .setNegativeButton("Full Connection") { _, _ ->
                performSignIn(fullScopes = true)
            }
            .show()
    }

    private fun showCustomKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cloud_settings, null)
        val etClientId = dialogView.findViewById<android.widget.EditText>(R.id.et_client_id)
        val etClientSecret = dialogView.findViewById<android.widget.EditText>(R.id.et_client_secret)

        val customId = GoogleAuthManager.getCustomClientId(this)
        val customSecret = GoogleAuthManager.getCustomClientSecret(this)

        if (!customId.isNullOrBlank()) {
            etClientId.setText(customId)
        } else {
            etClientId.setText("")
            if (GoogleAuthManager.getClientId(this) != null) {
                etClientId.hint = "Developer Default Key In Use"
            }
        }

        if (!customSecret.isNullOrBlank()) {
            etClientSecret.setText(customSecret)
        } else {
            etClientSecret.setText("")
            if (GoogleAuthManager.getClientSecret(this) != null) {
                etClientSecret.hint = "Developer Default Key In Use"
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Google Cloud Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val clientId = etClientId.text.toString().trim()
                val clientSecret = etClientSecret.text.toString().trim()
                GoogleAuthManager.saveCloudCredentials(this, clientId, clientSecret)
                Toast.makeText(this, "Credentials saved", Toast.LENGTH_SHORT).show()
                showScopeSelectionDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSignIn(fullScopes: Boolean = false) {
        val url = GoogleAuthManager.getAuthUrl(this, basicOnly = !fullScopes)
        if (url != null) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)

            lifecycleScope.launch {
                val autoCode = GoogleAuthManager.startLocalServerAndGetCode()

                if (autoCode != null) {
                    val success = GoogleAuthManager.exchangeCodeForTokens(this@RealityEliteActivity, autoCode)
                    if (success) {
                        withContext(Dispatchers.Main) {
                            com.neubofy.reality.utils.SecurePreferences.get(this@RealityEliteActivity, "reality_features").edit()
                                .putBoolean("reality_pro_basic_sign_in", true).apply()
                            Toast.makeText(this@RealityEliteActivity, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                            updateStateUI()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RealityEliteActivity, "Sign-in failed. Check credentials.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "Error generating Auth URL. Check Keys.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStateUI()
    }

    private fun updateStateUI() {
        val email = GoogleAuthManager.getUserEmail(this) ?: ""
        val isSignedIn = GoogleAuthManager.isSignedIn(this) && email.isNotEmpty()
        val userIdString = IdentityManager.getUserId(this)
        val userId = if (isSignedIn && userIdString.isNotEmpty()) userIdString else null

        val featureManager = FeatureManager(this)
        val isExpiredPaid = if (userId != null) {
            val endTime = featureManager.getRealityProEndTime()
            endTime > 0 && com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this) > endTime
        } else false

        val isExpiredTrial = if (userId != null && !isExpiredPaid && !featureManager.isRealityProVerified()) {
            val trialEnd = featureManager.getTrialEndTime()
            trialEnd > 0 && com.neubofy.reality.utils.SecureTimeProvider.currentTimeMillis(this) > trialEnd
        } else false

        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())

        // UI Elements
        val cardPaidPlanActive = findViewById<android.view.View>(R.id.card_paid_plan_active)
        val tvPaidPlanHeader = findViewById<android.widget.TextView>(R.id.tv_paid_plan_header)
        val tvPaidStart = findViewById<android.widget.TextView>(R.id.tv_paid_start_date)
        val tvPaidExpiry = findViewById<android.widget.TextView>(R.id.tv_paid_expiry_date)
        val cardStep2 = findViewById<android.view.View>(R.id.card_step2)
        val cardStep3 = findViewById<android.view.View>(R.id.card_step3)

        // Unified Sign In Logic
        if (isSignedIn && userId != null) {
            btnUnifiedSignin.text = "Signed In"
            btnUnifiedSignin.isEnabled = false
            btnSyncIdentity.visibility = android.view.View.GONE
        } else if (isSignedIn && userIdString.isEmpty()) {
            btnUnifiedSignin.text = "Signed In (Identity Missing)"
            btnUnifiedSignin.isEnabled = false
            btnSyncIdentity.visibility = android.view.View.VISIBLE
        } else {
            btnUnifiedSignin.text = "Sign In with Google"
            btnUnifiedSignin.isEnabled = true
            btnSyncIdentity.visibility = android.view.View.GONE
        }

        // --- Active Plan Card Visibility Logic ---
        val isProActive = featureManager.isRealityProVerified()
        val isTrialActive = featureManager.isTrialActive()

        // Prioritize Paid Plan details (active or expired)
        if (isProActive || isExpiredPaid) {
            cardPaidPlanActive?.visibility = android.view.View.VISIBLE
            if (isProActive) {
                tvPaidPlanHeader?.text = "Paid Plan Active"
                tvPaidExpiry?.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0))
            } else {
                tvPaidPlanHeader?.text = "Paid Plan Expired"
                tvPaidExpiry?.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0))
            }
            val start = featureManager.getRealityProStartTime()
            val end = featureManager.getRealityProEndTime()
            tvPaidStart?.text = "Activated: " + if (start > 0) dateFormat.format(java.util.Date(start)) else "Unknown"
            tvPaidExpiry?.text = "Expires: " + if (end > 0) dateFormat.format(java.util.Date(end)) else "Unknown"
        } else if (isTrialActive || isExpiredTrial) {
            cardPaidPlanActive?.visibility = android.view.View.VISIBLE
            if (isTrialActive) {
                tvPaidPlanHeader?.text = "Trial Active"
                tvPaidExpiry?.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0))
            } else {
                tvPaidPlanHeader?.text = "Trial Expired"
                tvPaidExpiry?.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0))
            }
            val start = featureManager.getTrialStartTime()
            val end = featureManager.getTrialEndTime()
            tvPaidStart?.text = "Started: " + if (start > 0) dateFormat.format(java.util.Date(start)) else "Unknown"
            tvPaidExpiry?.text = "Expires: " + if (end > 0) dateFormat.format(java.util.Date(end)) else "Unknown"
        } else {
            cardPaidPlanActive?.visibility = android.view.View.GONE
        }

        // --- Buying and Verification Card State (Always visible/unlocked when signed in) ---
        if (isSignedIn && userId != null) {
            cardStep2?.visibility = android.view.View.VISIBLE
            cardStep3?.visibility = android.view.View.VISIBLE
            cardStep2?.alpha = 1.0f
            cardStep3?.alpha = 1.0f
            btnPayUpi.isEnabled = true
            btnVerify.isEnabled = true
            updateUpiButtonText()
        } else {
            cardStep2?.visibility = android.view.View.VISIBLE
            cardStep3?.visibility = android.view.View.VISIBLE
            cardStep2?.alpha = 0.5f
            cardStep3?.alpha = 0.5f
            btnPayUpi.isEnabled = false
            btnVerify.isEnabled = false
        }
    }



    private fun showUpiPaymentDialog() {
        val intent = Intent(this, PaymentVerificationActivity::class.java)
        intent.putExtra("months", selectedMonths)
        startActivity(intent)
    }

    private fun showVerifyDialog() {
        val email = GoogleAuthManager.getUserEmail(this) ?: return
        val userId = IdentityManager.getUserId(this)
        verifyCode(false)
    }
    private fun verifyCode(isSilentCheck: Boolean = false) {
        val email = GoogleAuthManager.getUserEmail(this) ?: ""
        if (email.isEmpty()) {
            if (!isSilentCheck) Toast.makeText(this, "Please sign in with Google in the Profile page first.", Toast.LENGTH_LONG).show()
            return
        }

        val userId = IdentityManager.getUserId(this)
        if (userId.isEmpty()) {
            if (!isSilentCheck) Toast.makeText(this, "Identity is still syncing. Please wait or press Sync Identity.", Toast.LENGTH_LONG).show()
            return
        }
        val workerUrl = BuildConfig.WORKER_URL

        if (workerUrl.isEmpty()) {
            if (!isSilentCheck) Toast.makeText(this, "Worker URL not configured in build.", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanWorkerUrl = workerUrl.removeSuffix("/")
        val baseUrl = "$cleanWorkerUrl/license"

        if (!isSilentCheck) {
            findViewById<MaterialButton>(R.id.btn_verify).isEnabled = false
            findViewById<MaterialButton>(R.id.btn_verify).text = "Verifying..."
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use POST instead of GET to avoid leaking password in URL (server logs, caches)
                val url = URL(baseUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val jsonBody = org.json.JSONObject()
                jsonBody.put("userId", userId)
                jsonBody.put("password", IdentityManager.getBackupPassword(this@RealityEliteActivity))
                jsonBody.put("activeExpiry", IdentityManager.getActiveExpiry(this@RealityEliteActivity))
                jsonBody.put("activeDuration", IdentityManager.getActiveDuration(this@RealityEliteActivity))
                jsonBody.put("activeStatus", IdentityManager.getActiveStatus(this@RealityEliteActivity))
                jsonBody.put("planType", IdentityManager.getActivePlanType(this@RealityEliteActivity))
                jsonBody.put("action", "verify")

                java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                var responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    val newUrl = conn.getHeaderField("Location")
                    conn = URL(newUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    responseCode = conn.responseCode
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }.trim()

                    withContext(Dispatchers.Main) {
                        try {
                            val jsonResponse = JSONObject(responseStr)
                            val status = jsonResponse.optString("status", "")
                            if (status.equals("SUCCESS", ignoreCase = true)) {
                                val newPassword = jsonResponse.optString("password", "")
                                val newBackupKey = jsonResponse.optString("backupKey", "")
                                val newActiveExpiry = jsonResponse.optString("activeExpiry", "0")
                                val newActiveDuration = jsonResponse.optString("activeDuration", "0")
                                val newActiveStatus = jsonResponse.optString("activeStatus", "N")
                                val newPlanType = jsonResponse.optString("planType", "none")

                                if (newPassword.isNotEmpty()) {
                                    IdentityManager.updateCredentials(
                                        this@RealityEliteActivity,
                                        newPassword,
                                        newBackupKey,
                                        newActiveExpiry,
                                        newActiveDuration,
                                        newActiveStatus,
                                        newPlanType
                                    )
                                }

                                val featuresPrefs = SecurePreferences.get(this@RealityEliteActivity, "reality_features")
                                val featuresEditor = featuresPrefs.edit()

                                // Clear prior settings to avoid caching obsolete state
                                featuresEditor.putBoolean("feature_reality_pro", false)
                                featuresEditor.remove("feature_reality_pro_start_time_$userId")
                                featuresEditor.remove("feature_reality_pro_verified_until_$userId")
                                featuresEditor.remove("trial_start_time_$userId")
                                featuresEditor.remove("trial_end_time_$userId")

                                if (newActiveStatus == "V") {
                                    try {
                                        val expiryUnix = newActiveExpiry.toLong()
                                        if (expiryUnix > System.currentTimeMillis()) {
                                            featuresEditor.putBoolean("feature_reality_pro", true)
                                            val duration = newActiveDuration.toLong()
                                            if (newPlanType == "paid") {
                                                // Paid subscription
                                                val durationMs = (365L / 12) * duration * 24 * 60 * 60 * 1000
                                                val startTime = expiryUnix - durationMs
                                                featuresEditor.putLong("feature_reality_pro_start_time_$userId", startTime)
                                                featuresEditor.putLong("feature_reality_pro_verified_until_$userId", expiryUnix)
                                            } else {
                                                // Trial
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

                                Toast.makeText(this@RealityEliteActivity, "Active Reality Elite Member License Found and Restored!", Toast.LENGTH_LONG).show()

                                val intent = Intent(this@RealityEliteActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                                finish()
                            } else if (status.equals("EXPIRED", ignoreCase = true)) {
                                if (isSilentCheck) {
                                    handleSilentCheckFallback(userId)
                                } else {
                                    Toast.makeText(this@RealityEliteActivity, "Your subscription has expired.", Toast.LENGTH_LONG).show()
                                    resetVerifyButton()
                                }
                            } else {
                                if (isSilentCheck) {
                                    handleSilentCheckFallback(userId)
                                } else {
                                    Toast.makeText(this@RealityEliteActivity, "We haven't verified your payment yet. Please check back later.", Toast.LENGTH_LONG).show()
                                    resetVerifyButton()
                                }
                            }
                        } catch (e: Exception) {
                            if (isSilentCheck) {
                                handleSilentCheckFallback(userId)
                            } else {
                                Toast.makeText(this@RealityEliteActivity, "Invalid server response.", Toast.LENGTH_LONG).show()
                                resetVerifyButton()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isSilentCheck) {
                            handleSilentCheckFallback(userId)
                        } else {
                            Toast.makeText(this@RealityEliteActivity, "Server Error: $responseCode", Toast.LENGTH_LONG).show()
                            resetVerifyButton()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isSilentCheck) {
                        handleSilentCheckFallback(userId)
                    } else {
                        Toast.makeText(this@RealityEliteActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                        resetVerifyButton()
                    }
                }
            }
        }
    }

    private fun handleSilentCheckFallback(userId: String) {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_pro_prefs")
        prefs.edit().remove("pro_saved_verification_code_for_$userId").apply()

        cardStep2.alpha = 1.0f
        btnPayUpi.isEnabled = true
        updateUpiButtonText()

        cardStep3.alpha = 0.5f
        btnVerify.isEnabled = false
    }

    private fun resetVerifyButton() {
        findViewById<MaterialButton>(R.id.btn_verify).isEnabled = true
        findViewById<MaterialButton>(R.id.btn_verify).text = "Verify Status"
    }

}
