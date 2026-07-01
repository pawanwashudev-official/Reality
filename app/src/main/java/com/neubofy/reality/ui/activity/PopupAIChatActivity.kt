package com.neubofy.reality.ui.activity

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.neubofy.reality.R

/**
 * Popup version of AI Chat.
 * Inherits all logic from AIChatActivity, but designed for floating overlay usage.
 */
class PopupAIChatActivity : AIChatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.neubofy.reality.utils.RealityProManager.checkVerification(this)) return
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission is required for AI Chat.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PermissionManagerActivity::class.java))
            finish()
            return
        }

        // Configure window for popup style
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, 
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        setupPopupUI()
    }
    
    private fun setupPopupUI() {
        // Show popup header
        binding.popupHeader.visibility = View.VISIBLE
        
        // Hide menu button in popup mode as drawer might be awkward
        binding.btnMenu.visibility = View.GONE
        
        // Make the root constraint layout transparent in popup mode so home screen is visible
        binding.root.getChildAt(0).setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Set backgrounds of bottom container and toolbar to transparent for floating look
        binding.toolbar.setBackgroundResource(android.R.color.transparent)
        binding.bottomContainer.setBackgroundResource(android.R.color.transparent)
        binding.popupHeader.setBackgroundResource(android.R.color.transparent)

        binding.btnPopupClose.setOnClickListener {
            finish()
        }
        
        binding.btnPopupExpand.setOnClickListener {
            // Expand to full activity
            val intent = Intent(this, AIChatActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (getIntent().extras != null) {
                intent.putExtras(getIntent().extras!!)
            }
            startActivity(intent)
            finish()
        }
    }
}
