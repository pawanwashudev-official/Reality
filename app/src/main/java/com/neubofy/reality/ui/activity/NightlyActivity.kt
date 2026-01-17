package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.neubofy.reality.databinding.ActivityNightlyBinding
import com.neubofy.reality.utils.ThemeManager

class NightlyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupInsets()
        setupListeners()
        updateSetupStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }
    
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                binding.header.paddingLeft,
                systemBars.top + 16,
                binding.header.paddingRight,
                binding.header.paddingBottom
            )
            insets
        }
    }
    
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, NightlySettingsActivity::class.java))
        }
        
        binding.btnStartNightly.setOnClickListener {
            // TODO: Start nightly review flow
            android.widget.Toast.makeText(this, "Coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateSetupStatus() {
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val realityFolderId = prefs.getString("reality_folder_id", null)
        val diaryFolderId = prefs.getString("diary_folder_id", null)
        val reportFolderId = prefs.getString("report_folder_id", null)
        val taskList1Id = prefs.getString("task_list_1_id", null)
        val taskList2Id = prefs.getString("task_list_2_id", null)
        
        val driveReady = !realityFolderId.isNullOrEmpty() && !diaryFolderId.isNullOrEmpty() && !reportFolderId.isNullOrEmpty()
        val tasksReady = !taskList1Id.isNullOrEmpty() && !taskList2Id.isNullOrEmpty()
        
        if (driveReady && tasksReady) {
            binding.tvSetupStatus.text = "✓ Drive folders configured\n✓ Task lists configured\n\nReady to start!"
            binding.btnStartNightly.isEnabled = true
        } else {
            val sb = StringBuilder()
            if (driveReady) {
                sb.append("✓ Drive folders configured\n")
            } else {
                sb.append("✗ Drive folders not configured\n")
            }
            if (tasksReady) {
                sb.append("✓ Task lists configured")
            } else {
                sb.append("✗ Task lists not configured")
            }
            sb.append("\n\nTap the gear icon to set up.")
            binding.tvSetupStatus.text = sb.toString()
            binding.btnStartNightly.isEnabled = false
        }
    }
}
