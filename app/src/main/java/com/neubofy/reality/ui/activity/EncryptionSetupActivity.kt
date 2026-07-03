package com.neubofy.reality.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.R
import com.neubofy.reality.utils.RealityProManager
import com.neubofy.reality.google.GoogleAuthManager

class EncryptionSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!RealityProManager.checkVerification(this)) return

        setContentView(R.layout.activity_encryption_setup)

        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val etVerifyPassword = findViewById<TextInputEditText>(R.id.et_verify_password)
        val etUsername = findViewById<TextInputEditText>(R.id.et_username)
        val btnSave = findViewById<Button>(R.id.btn_save_encryption)
        val btnClear = findViewById<Button>(R.id.btn_clear_encryption)
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_encryption_prefs")

        val email = GoogleAuthManager.getUserEmail(this) ?: "user"
        etUsername.setText(email)

        updateUI()

        btnSave.setOnClickListener {
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("backup_password", password).apply()

            Toast.makeText(this, "Encryption password saved successfully", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        btnClear.setOnClickListener {
            val verifyPassword = etVerifyPassword.text.toString()
            val currentPass = prefs.getString("backup_password", null)
            if (verifyPassword == currentPass) {
                prefs.edit().remove("backup_password").apply()
                Toast.makeText(this, "Encryption password cleared. Default encryption active.", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_encryption_prefs")
        val currentPass = prefs.getString("backup_password", null)
        val tvStatus = findViewById<android.widget.TextView>(R.id.tv_encryption_status)
        val llSetupMode = findViewById<LinearLayout>(R.id.ll_setup_mode)
        val llActiveMode = findViewById<LinearLayout>(R.id.ll_active_mode)
        val etVerifyPassword = findViewById<TextInputEditText>(R.id.et_verify_password)

        if (!currentPass.isNullOrEmpty()) {
            tvStatus.text = "Custom Password Active"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            llSetupMode.visibility = View.GONE
            llActiveMode.visibility = View.VISIBLE
            etVerifyPassword.setText("")
        } else {
            tvStatus.text = "Not Set (Default Encryption)"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            llSetupMode.visibility = View.VISIBLE
            llActiveMode.visibility = View.GONE
        }
    }
}
