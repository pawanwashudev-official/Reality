package com.neubofy.reality.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.R
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.FeatureManager
import com.neubofy.reality.utils.IdentityManager
import com.neubofy.reality.utils.QRUtils
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class PaymentVerificationActivity : BaseActivity() {

    private lateinit var btnUpiDeeplink: MaterialButton
    private lateinit var btnScanQr: MaterialButton
    private lateinit var btnCopyUpi: MaterialButton
    private lateinit var ivQrCode: ImageView
    private lateinit var etTransactionId: TextInputEditText
    private lateinit var etCustomNote: TextInputEditText
    private lateinit var btnSubmitRequest: MaterialButton

    private var userId: String = ""
    private var selectedMonths: Int = 12
    private var price: Int = 99

    private val upiPaymentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data: Intent? = result.data
        if (data != null) {
            val response = data.getStringExtra("response")
            if (response != null && (response.contains("Status=SUCCESS", ignoreCase = true) || response.contains("status=success", ignoreCase = true) || response.contains("txnRef"))) {
                Toast.makeText(this@PaymentVerificationActivity, "Payment successful! Please submit your transaction ID below.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Payment cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment_verification)

        // Setup Header
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = "Payment Verification"
        toolbar.setNavigationOnClickListener { finish() }

        val userIdCheck = IdentityManager.getUserId(this)
        if (userIdCheck.isBlank() || userIdCheck == "Unknown") {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userId = userIdCheck

        selectedMonths = intent.getIntExtra("months", 12)
        price = Math.round((99.0 / 12.0) * selectedMonths).toInt()


        btnUpiDeeplink = findViewById(R.id.btn_upi_deeplink)
        btnScanQr = findViewById(R.id.btn_scan_qr)
        btnCopyUpi = findViewById(R.id.btn_copy_upi)
        ivQrCode = findViewById(R.id.iv_qr_code)
        etTransactionId = findViewById(R.id.et_transaction_id)
        etCustomNote = findViewById(R.id.et_custom_note)
        btnSubmitRequest = findViewById(R.id.btn_submit_request)

        btnUpiDeeplink.setOnClickListener {
            val uri = android.net.Uri.parse("upi://pay?pa=neubofy@pnb&pn=Reality&am=$price&cu=INR&tn=$userId")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                upiPaymentLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No UPI app found on this device.", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopyUpi.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UPI ID", "neubofy@pnb")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "UPI ID Copied!", Toast.LENGTH_SHORT).show()
        }

        btnScanQr.setOnClickListener {
            if (ivQrCode.visibility == View.GONE) {
                val bitmap = QRUtils.generateQRCode("upi://pay?pa=neubofy@pnb&pn=Reality&am=$price&cu=INR&tn=$userId", 512)
                ivQrCode.setImageBitmap(bitmap)
                ivQrCode.visibility = View.VISIBLE
                btnScanQr.text = "Hide QR Code"
            } else {
                ivQrCode.visibility = View.GONE
                btnScanQr.text = "Show QR Code"
            }
        }

        etTransactionId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnSubmitRequest.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmitRequest.setOnClickListener {
            val txnId = etTransactionId.text.toString().trim()
            val note = etCustomNote.text.toString().trim()
            if (txnId.isNotEmpty()) {
                submitPaymentRequest(txnId, note)
            }
        }
    }

    private fun submitPaymentRequest(transactionId: String, customNote: String) {

        val workerUrl = BuildConfig.WORKER_URL

        if (workerUrl.isEmpty()) {
            Toast.makeText(this, "Worker URL not configured in build.", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanWorkerUrl = workerUrl.removeSuffix("/")
        val baseUrl = "$cleanWorkerUrl/license"

        btnSubmitRequest.isEnabled = false
        btnSubmitRequest.text = "Submitting..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(baseUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val jsonBody = JSONObject()
                jsonBody.put("userId", userId)
                jsonBody.put("connectionSecret", IdentityManager.getConnectionSecret(this@PaymentVerificationActivity))
                jsonBody.put("activeExpiry", IdentityManager.getActiveExpiry(this@PaymentVerificationActivity))
                jsonBody.put("activeDuration", IdentityManager.getActiveDuration(this@PaymentVerificationActivity))
                jsonBody.put("activeStatus", IdentityManager.getActiveStatus(this@PaymentVerificationActivity))
                jsonBody.put("planType", IdentityManager.getActivePlanType(this@PaymentVerificationActivity))
                jsonBody.put("transactionId", transactionId)
                jsonBody.put("durationDays", (selectedMonths * 30.416).toInt())
                jsonBody.put("months", selectedMonths) // keep for backwards compatibility if needed
                if (customNote.isNotEmpty()) {
                    jsonBody.put("customNote", customNote)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
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
                                // Auto-trigger refresh identity in background to load new subscription details
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        IdentityManager.refreshIdentity(this@PaymentVerificationActivity.applicationContext)
                                    } catch (e: Exception) {
                                        com.neubofy.reality.utils.TerminalLogger.log("PaymentVerification: Auto-refresh failed: ${e.message}")
                                    }
                                }

                                Toast.makeText(this@PaymentVerificationActivity, "Subscription Purchased/Extended Successfully!", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                val errorMsg = jsonResponse.optString("error", "Unknown error")
                                Toast.makeText(this@PaymentVerificationActivity, "Submission failed: $errorMsg", Toast.LENGTH_LONG).show()
                                resetSubmitButton()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@PaymentVerificationActivity, "Invalid response from server", Toast.LENGTH_LONG).show()
                            resetSubmitButton()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PaymentVerificationActivity, "Server Error: $responseCode", Toast.LENGTH_LONG).show()
                        resetSubmitButton()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PaymentVerificationActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetSubmitButton()
                }
            }
        }
    }

    private fun resetSubmitButton() {
        btnSubmitRequest.isEnabled = true
        btnSubmitRequest.text = "Submit Request"
    }
}
