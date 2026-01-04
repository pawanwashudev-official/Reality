package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.R

/**
 * First-launch activity that introduces the app and collects user name.
 * After user continues, navigates to OnboardingActivity for permission setup.
 */
class SecurityIntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_intro)

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            val nameInput = findViewById<EditText>(R.id.etUserName)
            val userName = nameInput.text.toString().trim()
            
            if (userName.isEmpty()) {
                nameInput.error = "Please enter your name"
                return@setOnClickListener
            }

            // Save Name & Mark Intro as Shown
            val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
            prefs.edit()
                .putString("user_name", userName)
                .putBoolean("intro_shown", true)
                .apply()

            // Navigate to Onboarding Permissions Activity
            val intent = Intent(this, OnboardingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}
