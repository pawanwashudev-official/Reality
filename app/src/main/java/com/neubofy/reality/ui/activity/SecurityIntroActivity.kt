package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.R


class SecurityIntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_intro)

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            val nameInput = findViewById<android.widget.EditText>(R.id.etUserName)
            val userName = nameInput.text.toString().trim()
            
            if (userName.isEmpty()) {
                nameInput.error = "Please enter your name"
                return@setOnClickListener
            }

            // Save Name & Mark intro as shown
            val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
            prefs.edit()
                .putString("user_name", userName)
                .putBoolean("intro_shown", true)
                .apply()

            // Navigate to Main Activity
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}
