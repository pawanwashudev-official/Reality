package com.neubofy.reality.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.content.Intent
import com.neubofy.reality.R

/**
 * Popup version of AI Chat.
 * Inherits all logic from AIChatActivity, but designed for floating overlay usage.
 */
class PopupAIChatActivity : AIChatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure window for popup style
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, 
            (resources.displayMetrics.heightPixels * 0.7).toInt() // 70% height
        )
        window.setGravity(Gravity.BOTTOM)
        
        setupPopupUI()
    }
    
    private fun setupPopupUI() {
        // Show popup header
        binding.popupHeader.visibility = View.VISIBLE
        
        // Hide menu button in popup mode as drawer might be awkward
        binding.btnMenu.visibility = View.GONE
        
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
