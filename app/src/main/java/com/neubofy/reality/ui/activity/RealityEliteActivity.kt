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
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSyncIdentity: MaterialButton
    private lateinit var btnEnrollRenew: MaterialButton
    private lateinit var tvRefreshPrompt: android.widget.TextView

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

        btnCancel = findViewById(R.id.btn_cancel)
        btnSyncIdentity = findViewById(R.id.btn_sync_identity)
        btnEnrollRenew = findViewById(R.id.btn_enroll_renew)
        tvRefreshPrompt = findViewById(R.id.tv_refresh_prompt)




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
                    if (e.message != "RATE_LIMIT") {
                        com.neubofy.reality.utils.TerminalLogger.log("Manual sync error: ${e.message}")
                    }
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

        btnCancel.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
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

        // Setup Enroll/Renew button text and visibility
        btnEnrollRenew.visibility = if (isSignedIn && userId != null) android.view.View.VISIBLE else android.view.View.GONE
        if (isProActive || isExpiredPaid) {
            btnEnrollRenew.text = "Renew Membership"
            // If they have an active plan but Identity says status is NOT 'V', show prompt
            val activeStatus = IdentityManager.getActiveStatus(this)
            if (activeStatus != "V") {
                tvRefreshPrompt.visibility = android.view.View.VISIBLE
            } else {
                tvRefreshPrompt.visibility = android.view.View.GONE
            }
        } else {
            btnEnrollRenew.text = "Enroll as Elite Member"
            tvRefreshPrompt.visibility = android.view.View.GONE
        }

        btnEnrollRenew.setOnClickListener {
            showPaymentPopup()
        }

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
    }

    private fun showPaymentPopup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_method, null)
        val spinnerDurationPopup = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.spinner_duration)
        val btnPayUpiPopup = dialogView.findViewById<MaterialButton>(R.id.btn_pay_upi)
        
        var popupSelectedMonths = 12
        val monthsOptions = (1..36).map { "$it Months" }.toTypedArray()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monthsOptions)
        spinnerDurationPopup.setAdapter(adapter)
        spinnerDurationPopup.setText("12 Months", false)

        val updateUpiPrice = {
            val price = ((99.0 / 12.0) * popupSelectedMonths).roundToInt()
            btnPayUpiPopup.text = "UPI (₹$price)"
        }
        updateUpiPrice()

        spinnerDurationPopup.setOnItemClickListener { _, _, position, _ ->
            popupSelectedMonths = position + 1
            updateUpiPrice()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Select Subscription Duration")
            .setView(dialogView)
            .show()

        btnPayUpiPopup.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, PaymentVerificationActivity::class.java)
            intent.putExtra("months", popupSelectedMonths)
            startActivity(intent)
        }
    }



}
