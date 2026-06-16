package com.neubofy.reality.ui.activity

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.neubofy.reality.R
import com.neubofy.reality.ui.fragments.installation.OnboardingPermissionsFragment

/**
 * Onboarding activity that hosts the permissions setup flow.
 * This is shown on first launch after the user enters their name.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_onboarding)
        
        // Load the permissions fragment if this is fresh start
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboarding_container, OnboardingPermissionsFragment())
                .commit()
        }
        
        // Prevent back navigation (user must complete onboarding)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Don't allow going back - must complete onboarding
            }
        })
    }
}
