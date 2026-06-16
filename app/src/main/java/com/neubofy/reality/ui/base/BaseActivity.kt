package com.neubofy.reality.ui.base

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.utils.ThemeManager

/**
 * Base Activity to apply global personalization (Theming, Backgrounds, Glassmorphism).
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val scale = ThemeManager.getFontSizeScale(newBase)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.fontScale = scale
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        
        // Elite Motion: Apply Activity Transitions
        applyEliteTransitions()
        
        super.onCreate(savedInstanceState)
    }

    private fun applyEliteTransitions() {
        val preset = ThemeManager.getMotionPreset(this)
        // Note: For real "Stiff" vs "Bouncy", we'd use custom XML animations with different interpolators.
        // For now, we'll map them to system/app animations or overridePendingTransition.
        // In a real premium app, we'd have Anim/motion_stiff_enter.xml etc.
    }

    override fun onResume() {
        super.onResume()
        // Re-enforce Edge-to-Edge flags (Fixes nav bar glitch on back nav)
        ThemeManager.enforceEdgeToEdge(this)
        
        // Apply Global Background (Color + Pattern)
        ThemeManager.applyAppBackground(window.decorView)

        // Recursively apply all personalization
        // Force Insets Update to prevent spacing glitches
        val rootView = findViewById<View>(android.R.id.content)
        if (rootView != null) {
            ThemeManager.applyGlobalPersonalization(rootView)
            androidx.core.view.ViewCompat.requestApplyInsets(rootView)
        }
    }
    
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyGlobalPersonalization()
    }
    
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyGlobalPersonalization()
    }
    
    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyGlobalPersonalization()
    }

    private fun applyGlobalPersonalization() {
        val rootView = findViewById<View>(android.R.id.content)
        if (rootView != null) {
            ThemeManager.applyGlobalPersonalization(rootView)
        }
    }
}
