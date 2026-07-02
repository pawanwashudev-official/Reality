package com.neubofy.reality.ui.activity

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.R

class EncryptionSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_encryption_setup)

        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val btnSave = findViewById<Button>(R.id.btn_save_encryption)
        val btnClear = findViewById<Button>(R.id.btn_clear_encryption)
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_encryption_prefs")
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
            prefs.edit().remove("backup_password").apply()
            Toast.makeText(this, "Encryption password cleared. Default encryption active.", Toast.LENGTH_SHORT).show()
            updateUI()
            // finish() // Let user see the update instead of closing immediately
        }
    }

private fun updateUI() {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_encryption_prefs")
        val currentPass = prefs.getString("backup_password", null)
        val tvStatus = findViewById<android.widget.TextView>(R.id.tv_encryption_status)
        val btnClear = findViewById<Button>(R.id.btn_clear_encryption)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)

        if (!currentPass.isNullOrEmpty()) {
            tvStatus.text = "Custom Password Active"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnClear.visibility = android.view.View.VISIBLE
            etPassword.setText("")
            etConfirmPassword.setText("")
            etPassword.hint = "Update Password (min 6 chars)"
        } else {
            tvStatus.text = "Not Set (Default Encryption)"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            btnClear.visibility = android.view.View.GONE
            etPassword.hint = "New Password (min 6 chars)"
        }
    }
}
