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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.R
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.FeatureManager
import com.neubofy.reality.utils.MD5Utils
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RealityProActivity : BaseActivity() {

    private lateinit var btnStep1Signin: MaterialButton
    private lateinit var cardStep2: MaterialCardView
    private lateinit var btnPayUpi: MaterialButton
    private lateinit var cardStep3: MaterialCardView
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnCancel: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reality_pro)

        findViewById<android.view.View>(R.id.btn_view_pro_members)?.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://reality.neubofy.in/promembers"))
            startActivity(intent)
        }

        btnStep1Signin = findViewById(R.id.btn_step1_signin)
        cardStep2 = findViewById(R.id.card_step2)
        btnPayUpi = findViewById(R.id.btn_pay_upi)
        cardStep3 = findViewById(R.id.card_step3)
        btnVerify = findViewById(R.id.btn_verify)
        btnCancel = findViewById(R.id.btn_cancel)

        findViewById<android.view.View>(R.id.btn_trial_activation)?.setOnClickListener {
            val email = GoogleAuthManager.getUserEmail(this)
            if (email == null) {
                Toast.makeText(this, "Please sign in first (Step 1) to start trial", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val featureManager = FeatureManager(this)
            if (featureManager.hasUsedTrial()) {
                if (featureManager.isTrialActive()) {
                    Toast.makeText(this, "Your trial is already active!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Trial has expired. Please purchase Pro to continue.", Toast.LENGTH_LONG).show()
                }
            } else {
                featureManager.activateTrial()
                Toast.makeText(this, "3-Day Trial Activated! Enjoy Pro features.", Toast.LENGTH_LONG).show()
                featureManager.setRealityProEnabled(true)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }


        btnStep1Signin.setOnClickListener {
            showKeySelectionDialog()
        }

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


    private fun showKeySelectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign In Option")
            .setMessage("Use your own Google Cloud credentials or use Developer Default keys.")
            .setPositiveButton("Default Key") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Default Keys")
                    .setMessage("Using Developer Default Keys is completely safe. The developer cannot access your data remotely and it is not stored on our servers.")
                    .setPositiveButton("Continue") { _, _ ->
                        performSignIn()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Own Key") { _, _ ->
                showCustomKeyDialog()
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
                performSignIn()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSignIn() {
        val url = GoogleAuthManager.getAuthUrl(this, basicOnly = true)
        if (url != null) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)

            lifecycleScope.launch {
                val autoCode = GoogleAuthManager.startLocalServerAndGetCode()

                var success = false
                if (autoCode != null) {
                    success = GoogleAuthManager.exchangeCodeForTokens(this@RealityProActivity, autoCode)
                    if (success) {
                        withContext(Dispatchers.Main) {
                            com.neubofy.reality.utils.SecurePreferences.get(this@RealityProActivity, "reality_features").edit()
                                .putBoolean("reality_pro_basic_sign_in", true).apply()
                            Toast.makeText(this@RealityProActivity, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                            updateStateUI()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RealityProActivity, "Sign-in failed. Check credentials.", Toast.LENGTH_LONG).show()
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
        val userId = if (isSignedIn) MD5Utils.getUserIdFromEmail(email) else null

        // Step 1: Identity
        if (isSignedIn && userId != null) {
            btnStep1Signin.isEnabled = false
            btnStep1Signin.text = "Signed In"
            cardStep2.alpha = 1.0f
            btnPayUpi.isEnabled = true
        } else {
            btnStep1Signin.isEnabled = true
            btnStep1Signin.text = "Sign In with Google"
            cardStep2.alpha = 0.5f
            btnPayUpi.isEnabled = false
        }

        // Step 2: Payment
        var savedCode: String? = null
        if (userId != null) {
            val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_pro_prefs")
            savedCode = prefs.getString("pro_saved_verification_code_for_$userId", null)

            if (savedCode != null) {
                btnPayUpi.isEnabled = false
                btnPayUpi.text = "Submitted"
                cardStep3.alpha = 1.0f
                btnVerify.isEnabled = true
            } else {
                if (isSignedIn) {
                    btnPayUpi.isEnabled = true
                    btnPayUpi.text = "UPI (₹99)"
                }
                cardStep3.alpha = 0.5f
                btnVerify.isEnabled = false
            }
        } else {
            cardStep3.alpha = 0.5f
            btnVerify.isEnabled = false
        }

        // Step 3: Verification
        val featureManager = FeatureManager(this)
        if (featureManager.isRealityProVerified()) {
            btnVerify.isEnabled = false
            btnVerify.text = "Verified"
        } else if (savedCode != null) {
            btnVerify.isEnabled = true
            btnVerify.text = "Verify Status"
        }
    }

    private fun showUpiPaymentDialog() {
        val intent = Intent(this, PaymentVerificationActivity::class.java)
        startActivity(intent)
    }

    private fun showVerifyDialog() {
        val email = GoogleAuthManager.getUserEmail(this) ?: return
        val userId = MD5Utils.getUserIdFromEmail(email)
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_pro_prefs")
        val savedCode = prefs.getString("pro_saved_verification_code_for_$userId", null)

        if (savedCode != null) {
            verifyCode(savedCode)
        } else {
            Toast.makeText(this, "No verification code found. Please submit payment request first.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyCode(vCode: String) {
        val email = GoogleAuthManager.getUserEmail(this) ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "Please sign in with Google in the Profile page first.", Toast.LENGTH_LONG).show()
            return
        }

        val userId = MD5Utils.getUserIdFromEmail(email)
        val baseUrl = BuildConfig.REALITY_LICENSE_URL

        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "License URL not configured in build.", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<MaterialButton>(R.id.btn_verify).isEnabled = false
        findViewById<MaterialButton>(R.id.btn_verify).text = "Verifying..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Construct URL
                val requestUrl = "$baseUrl?userId=$userId&vCode=$vCode"
                var url = URL(requestUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false // Handle cross-domain manually
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

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
                        if (responseStr.contains("SUCCESS", ignoreCase = true)) {
                            // Activation Successful
                            val featureManager = FeatureManager(this@RealityProActivity)
                            featureManager.setRealityProVerified(true)
                            Toast.makeText(this@RealityProActivity, "Reality Pro Activated!", Toast.LENGTH_LONG).show()

                            // Go Home
                            val intent = Intent(this@RealityProActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@RealityProActivity, "We haven't verified your payment yet. Please check back later.", Toast.LENGTH_LONG).show()
                            resetVerifyButton()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RealityProActivity, "Server Error: $responseCode", Toast.LENGTH_LONG).show()
                        resetVerifyButton()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RealityProActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetVerifyButton()
                }
            }
        }
    }

    private fun resetVerifyButton() {
        findViewById<MaterialButton>(R.id.btn_verify).isEnabled = true
        findViewById<MaterialButton>(R.id.btn_verify).text = "Verify Status"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
