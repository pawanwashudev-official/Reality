package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.databinding.ActivityBlockBinding
import com.neubofy.reality.utils.SavedPreferencesLoader

class BlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockBinding
    private lateinit var prefs: SavedPreferencesLoader
    private var blockedPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        prefs = SavedPreferencesLoader(this)
        
        blockedPackage = intent.getStringExtra("pkg") ?: ""
        val reason = intent.getStringExtra("reason") ?: "Blocked by Reality"
        
        binding.tvReason.text = reason
        
        // Custom Message Logic
        val messages = prefs.getBlockMessages()
        val tag = when {
            reason.contains("Focus", ignoreCase = true) -> "FOCUS"
            reason.contains("Bedtime", ignoreCase = true) -> "BEDTIME"
            reason.contains("Limit", ignoreCase = true) -> "LIMIT"
            else -> "ALL"
        }
        
        val validMessages = messages.filter { it.tags.contains("ALL") || it.tags.contains(tag) }
        
        if (validMessages.isNotEmpty()) {
            binding.tvMessage.text = validMessages.random().message
        } else {
            // Default fallbacks if list is empty (shouldn't be if prefs loads defaults, but just in case)
            binding.tvMessage.text = "Stay Focused."
        }
        
        binding.btnHome.setOnClickListener {
            goHome()
        }
        
        binding.btnEmergency.setOnClickListener {
            handleEmergencyAccess()
        }
    }
    
    private fun handleEmergencyAccess() {
        val strictData = prefs.getStrictModeData()
        
        // Strict Lock check removed as per user request ("remove enable/disable by strict mode")
        
        if (strictData.isEnabled && strictData.modeType != com.neubofy.reality.Constants.StrictModeData.MODE_NONE) {
             // Strict Mode (Unlocked): 60 sec wait.
             showEmergencyDialog(60)
        } else {
             // Normal Mode: 15 sec wait.
             showEmergencyDialog(15)
        }
    }
    
    private fun showEmergencyDialog(seconds: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Emergency Access")
            .setMessage("Wait $seconds seconds to unblock for 5 minutes.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open App") { _, _ -> 
                // Placeholder - button disabled initially
            }
            .create()
            
        dialog.show()
        val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false
        
        object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                btn.text = "Wait ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                btn.text = "Open App"
                btn.isEnabled = true
                btn.setOnClickListener {
                    unblockFor5Mins()
                    dialog.dismiss()
                }
            }
        }.start()
    }
    
    private fun unblockFor5Mins() {
        val emergencyData = prefs.getEmergencyData()
        emergencyData.currentSessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000) // 5 mins
        prefs.saveEmergencyData(emergencyData)
        
        val refreshIntent = Intent("com.neubofy.reality.refresh.focus_mode")
        refreshIntent.setPackage(packageName)
        sendBroadcast(refreshIntent)
        
        Toast.makeText(this, "Emergency access: 5 minutes", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finishAffinity()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        goHome()
    }
}
