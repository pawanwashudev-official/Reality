package com.neubofy.reality.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.R
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.utils.FeatureManager
import com.neubofy.reality.utils.MD5Utils
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.enableEdgeToEdge

class RealityProActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reality_pro)

        val etCode = findViewById<TextInputEditText>(R.id.et_activation_code)
        val btnVerify = findViewById<MaterialButton>(R.id.btn_verify)
        val btnGetCode = findViewById<MaterialButton>(R.id.btn_get_code)
        val btnCancel = findViewById<MaterialButton>(R.id.btn_cancel)
        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        btnVerify.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter an activation code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyCode(code)
        }

        btnGetCode.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://reality.neubofy.in/pro"))
            startActivity(intent)
        }

        btnCancel.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
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
                            featureManager.setRealityProEnabled(true)
                            featureManager.setRealityProVerified(true)
                            Toast.makeText(this@RealityProActivity, "Reality Pro Activated!", Toast.LENGTH_LONG).show()

                            // Go Home
                            val intent = Intent(this@RealityProActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@RealityProActivity, "Verification failed: Invalid code", Toast.LENGTH_LONG).show()
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
        findViewById<MaterialButton>(R.id.btn_verify).text = "Verify & Activate"
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
