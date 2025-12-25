package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivitySettingsBinding
import com.neubofy.reality.utils.SavedPreferencesLoader

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SavedPreferencesLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SavedPreferencesLoader(this)

        setupToolbar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.cardCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarSettingsActivity::class.java))
        }


        
        binding.cardBlocks.setOnClickListener {
            startActivity(Intent(this, UnifiedBlocklistActivity::class.java))
        }

        binding.cardBedtime.setOnClickListener {
            startActivity(Intent(this, BedtimeActivity::class.java))
        }
        
        // Strict Mode Navigation
        binding.cardStrictMode.setOnClickListener {
            startActivity(Intent(this, StrictModeActivity::class.java))
        }
        
        // About
        binding.cardAbout.setOnClickListener {
             startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun updateUI() {
        // Strict Mode
        val data = prefs.getStrictModeData()
        
        if (data.isEnabled) {
            val status = when (data.modeType) {
                Constants.StrictModeData.MODE_TIMER -> "Active (Timer)"
                Constants.StrictModeData.MODE_PASSWORD -> "Active (Password)"
                else -> "Active"
            }
            binding.tvStrictStatus.text = status
            // Use Holo Red for active warning state
            binding.tvStrictStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            binding.tvStrictStatus.text = "Inactive"
            binding.tvStrictStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
        
        // Bedtime
        val bedtime = prefs.getBedtimeData()
        if (bedtime.isEnabled) {
             val startStr = String.format("%02d:%02d", bedtime.startTimeInMins / 60, bedtime.startTimeInMins % 60)
             val endStr = String.format("%02d:%02d", bedtime.endTimeInMins / 60, bedtime.endTimeInMins % 60)
             binding.tvBedtimeStatus.text = "Active $startStr - $endStr"
             binding.tvBedtimeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
             binding.tvBedtimeStatus.text = "Not configured"
             binding.tvBedtimeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }
}
