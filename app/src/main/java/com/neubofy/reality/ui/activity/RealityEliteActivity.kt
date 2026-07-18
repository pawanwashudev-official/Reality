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
        btnSyncIdentity = findViewById(R.id.btn_sync_identity)
        
        findViewById<android.view.View>(R.id.btn_why_google)?.setOnClickListener {
            startActivity(android.content.Intent(this, WhyGooglePermissionActivity::class.java))
        }

        btnCancel = findViewById(R.id.btn_cancel)
        btnEnrollRenew = findViewById(R.id.btn_enroll_renew)
        tvRefreshPrompt = findViewById(R.id.tv_refresh_prompt)




        btnUnifiedSignin.setOnClickListener {
            if (GoogleAuthManager.isSignedIn(this)) {
                GoogleAuthManager.signOut(this)
                SecurePreferences.get(this, "reality_features").edit()
                    .putBoolean("reality_pro_basic_sign_in", false).apply()
                updateStateUI()
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
            } else {
                com.neubofy.reality.google.GoogleSignInHelper.startSignInFlow(this, false, forceBasicScope = true) {
                    updateStateUI()
                }
            }
        }

        btnSyncIdentity.setOnClickListener {
            btnSyncIdentity.text = "Syncing..."
            btnSyncIdentity.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = IdentityManager.refreshIdentity(this@RealityEliteActivity, isManualTrigger = true)
                    withContext(Dispatchers.Main) {
                        btnSyncIdentity.text = "Refresh Identity & Subscription"
                        btnSyncIdentity.isEnabled = true
                        updateStateUI()
                        if (result != null) {
                            showIdentityResultDialog(result)
                        } else {
                            Toast.makeText(this@RealityEliteActivity, "Identity sync failed. Please sign in first.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnSyncIdentity.text = "Refresh Identity & Subscription"
                        btnSyncIdentity.isEnabled = true
                        if (e.message != "RATE_LIMIT") {
                            Toast.makeText(this@RealityEliteActivity, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                            com.neubofy.reality.utils.TerminalLogger.log("Manual sync error: ${e.message}")
                        }
                    }
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

        findViewById<android.widget.ImageButton>(R.id.btn_cloud_settings)?.setOnClickListener {
            com.neubofy.reality.google.GoogleSignInHelper.showCloudKeySettings(this)
        }

        btnCancel.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }



    override fun onResume() {
        super.onResume()
        updateStateUI()
    }

    override fun onIdentityUpdated() {
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
            btnUnifiedSignin.text = "Sign Out"
            btnUnifiedSignin.isEnabled = true
            btnSyncIdentity.visibility = android.view.View.VISIBLE
            btnSyncIdentity.text = "Refresh Identity & Subscription"
        } else if (isSignedIn && userIdString.isEmpty()) {
            btnUnifiedSignin.text = "Sign Out (Identity Missing)"
            btnUnifiedSignin.isEnabled = true
            btnSyncIdentity.visibility = android.view.View.VISIBLE
            btnSyncIdentity.text = "Sync Identity"
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

    private fun showIdentityResultDialog(result: IdentityManager.IdentityResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_identity_result, null)
        
        // User profile (already available from Google sign-in)
        val ivPhoto = dialogView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.iv_user_photo)
        val tvName = dialogView.findViewById<android.widget.TextView>(R.id.tv_user_name)
        val tvEmail = dialogView.findViewById<android.widget.TextView>(R.id.tv_user_email)
        val ivStatusIcon = dialogView.findViewById<android.widget.ImageView>(R.id.iv_status_icon)
        
        tvName.text = GoogleAuthManager.getUserName(this) ?: "User"
        tvEmail.text = GoogleAuthManager.getUserEmail(this) ?: ""
        
        val photoUrl = GoogleAuthManager.getUserPhotoUrl(this)
        if (!photoUrl.isNullOrEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeStream(java.net.URL(photoUrl).openStream())
                    withContext(Dispatchers.Main) { ivPhoto.setImageBitmap(bmp) }
                } catch (_: Exception) { }
            }
        }
        
        if (result.activeStatus == "V") {
            ivStatusIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            ivStatusIcon.setColorFilter(android.graphics.Color.parseColor("#FF9800"))
            ivStatusIcon.setImageResource(R.drawable.baseline_error_outline_24)
        }
        
        // Backup Password (static, visible, copyable)
        val tvBackupPassword = dialogView.findViewById<android.widget.TextView>(R.id.tv_backup_password)
        tvBackupPassword.text = result.backupPassword
        
        dialogView.findViewById<android.widget.ImageView>(R.id.btn_copy_backup_password).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Backup Password", result.backupPassword))
            Toast.makeText(this, "Backup password copied", Toast.LENGTH_SHORT).show()
        }
        
        // Connection Secret (rotating, fully masked — never exposed)
        dialogView.findViewById<android.widget.TextView>(R.id.tv_connection_secret).text = result.connectionSecretMasked
        
        // Membership details
        val tvPlanType = dialogView.findViewById<android.widget.TextView>(R.id.tv_plan_type)
        val tvSubStatus = dialogView.findViewById<android.widget.TextView>(R.id.tv_sub_status)
        val tvSubDuration = dialogView.findViewById<android.widget.TextView>(R.id.tv_sub_duration)
        val tvSubStart = dialogView.findViewById<android.widget.TextView>(R.id.tv_sub_start)
        val tvSubExpiry = dialogView.findViewById<android.widget.TextView>(R.id.tv_sub_expiry)
        
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        
        tvPlanType.text = when (result.planType) {
            "paid" -> "Elite Paid"
            "trial" -> "Elite Trial"
            else -> "No Active Plan"
        }
        
        tvSubStatus.text = when (result.activeStatus) {
            "V" -> "✅ Active"
            "P" -> "⏳ Pending"
            else -> "❌ Inactive"
        }
        
        val durationVal = result.activeDuration.toLongOrNull() ?: 0
        tvSubDuration.text = if (result.planType == "paid" && durationVal > 0) {
            "$durationVal months"
        } else if (result.planType == "trial" && durationVal > 0) {
            "$durationVal days"
        } else {
            "—"
        }
        
        val expiryMs = result.activeExpiry.toLongOrNull() ?: 0
        if (expiryMs > 0) {
            tvSubExpiry.text = dateFormat.format(java.util.Date(expiryMs))
            val startMs = if (result.planType == "paid" && durationVal > 0) {
                expiryMs - ((365L / 12) * durationVal * 24 * 60 * 60 * 1000)
            } else if (result.planType == "trial" && durationVal > 0) {
                expiryMs - (durationVal * 24 * 60 * 60 * 1000)
            } else 0
            tvSubStart.text = if (startMs > 0) dateFormat.format(java.util.Date(startMs)) else "—"
        } else {
            tvSubExpiry.text = "—"
            tvSubStart.text = "—"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Identity Synced Successfully")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }

}
