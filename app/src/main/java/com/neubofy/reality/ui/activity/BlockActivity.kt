package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.databinding.ActivityBlockBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils

class BlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockBinding
    private lateinit var prefs: SavedPreferencesLoader
    private var blockedPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = SavedPreferencesLoader(this)
        
        blockedPackage = intent.getStringExtra("pkg") ?: ""
        val reason = intent.getStringExtra("reason") ?: "Blocked by Reality"
        
        binding.tvReason.text = reason
        
        binding.btnHome.setOnClickListener {
            goHome()
        }
        
        binding.btnEmergency.setOnClickListener {
            handleEmergencyAccess()
        }
    }
    
    private fun handleEmergencyAccess() {
        val strictData = prefs.getStrictModeData()

        if (strictData.isEnabled && strictData.isEmergencyLocked) {
             MaterialAlertDialogBuilder(this)
                .setTitle("Emergency Access Locked")
                .setMessage("Emergency access is disabled because Strict Mode is active with the Lock enabled.")
                .setPositiveButton("OK", null)
                .show()
             return
        }
        
        if (strictData.isEnabled && strictData.modeType != com.neubofy.reality.Constants.StrictModeData.MODE_NONE) {
            // Strict Mode (Unloacked): 60 sec wait.
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
        if (blockedPackage.isNotEmpty()) {
            val intent = Intent(AppBlockerService.INTENT_ACTION_BLOCK_APP_COOLDOWN).apply {
                putExtra("result_id", blockedPackage)
                putExtra("selected_time", 5 * 60 * 1000) // 5 Mins
                setPackage(packageName)
            }
            sendBroadcast(intent)
            Toast.makeText(this, "Unblocked for 5 minutes", Toast.LENGTH_SHORT).show()
            finish()
        }
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
