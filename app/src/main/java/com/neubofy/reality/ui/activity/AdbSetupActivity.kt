package com.neubofy.reality.ui.activity

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.neubofy.reality.R

class AdbSetupActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkPermissions()
            handler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adb_setup)

        setupToolbar()
        setupButtons()
        
        statusText = findViewById(R.id.tv_status_result)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        handler.post(checkRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkRunnable)
    }

    private fun setupToolbar() {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupButtons() {
        // Open Settings
        findViewById<MaterialButton>(R.id.btn_open_dev_settings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // Copy Command 1
        findViewById<ImageView>(R.id.btn_copy_cmd1).setOnClickListener {
            copyToClipboard("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        }
        
        // Copy Command 2
        findViewById<ImageView>(R.id.btn_copy_cmd2).setOnClickListener {
             copyToClipboard("adb shell appops set $packageName PROJECT_MEDIA allow")
        }

        // Check Status Manual
        findViewById<MaterialButton>(R.id.btn_check_status).setOnClickListener {
            checkPermissions(true)
        }
    }

    private fun checkPermissions(showToast: Boolean = false) {
        val hasSecureSettings = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSecureSettings) {
            statusText.text = "Success: WRITE_SECURE_SETTINGS Granted!"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            
            if (showToast) {
                Toast.makeText(this, "Permissions Verified! You are all set.", Toast.LENGTH_SHORT).show()
            }
        } else {
            statusText.text = "Status: Permission NOT Granted yet"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            
            if (showToast) {
                Toast.makeText(this, "Permission not found. Please run the ADB command.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADB Command", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
