package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.neubofy.reality.Constants
import com.neubofy.reality.databinding.ActivityBedtimeBinding
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils

class BedtimeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBedtimeBinding
    private lateinit var prefsLoader: SavedPreferencesLoader
    private var bedtimeData: Constants.BedtimeData = Constants.BedtimeData()
    private var isLocked = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBedtimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsLoader = SavedPreferencesLoader(this)
        bedtimeData = prefsLoader.getBedtimeData()
        
        isLocked = !StrictLockUtils.isModificationAllowedFor(this, StrictLockUtils.FeatureType.BEDTIME)
        
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        updateUI()
        
        binding.switchBedtime.setOnCheckedChangeListener { _, isChecked ->
            if (isLocked) {
                // Should be disabled but if triggered somehow
                 binding.switchBedtime.isChecked = !isChecked
                 return@setOnCheckedChangeListener
            }
            bedtimeData.isEnabled = isChecked
            prefsLoader.saveBedtimeData(bedtimeData)
            updateUI()
        }
        
        binding.btnStartTime.setOnClickListener {
            if (isLocked) {
                showLockedToast()
                return@setOnClickListener
            }
            showTimePicker(true)
        }
        
        binding.btnEndTime.setOnClickListener {
            if (isLocked) {
                showLockedToast()
                return@setOnClickListener
            }
            showTimePicker(false)
        }
        
        binding.btnSelectApps.setOnClickListener {
            // Bedtime now uses the same blocklist as Focus Mode
            Toast.makeText(this, "Bedtime uses your Focus Mode blocklist", Toast.LENGTH_LONG).show()
        }

        if (isLocked) {
             binding.switchBedtime.isEnabled = false
             binding.btnStartTime.alpha = 0.5f
             binding.btnEndTime.alpha = 0.5f
             binding.tvLockedStatus.visibility = View.VISIBLE
        }
    }
    
    private fun showLockedToast() {
        Toast.makeText(this, "Locked by Strict Mode. Changes allowed 00:00-00:10.", Toast.LENGTH_SHORT).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // No longer handling app selection - using universal blocklist
    }
    
    private fun updateUI() {
        binding.switchBedtime.isChecked = bedtimeData.isEnabled
        
        val startHour = bedtimeData.startTimeInMins / 60
        val startMin = bedtimeData.startTimeInMins % 60
        binding.btnStartTime.text = String.format("%02d:%02d", startHour, startMin)
        
        val endHour = bedtimeData.endTimeInMins / 60
        val endMin = bedtimeData.endTimeInMins % 60
        binding.btnEndTime.text = String.format("%02d:%02d", endHour, endMin)
        
        // Show info about universal blocklist
        binding.tvAppsCount.text = "Uses Focus Mode blocklist"
    }
    
    private fun showTimePicker(isStartTime: Boolean) {
        val currentMins = if (isStartTime) bedtimeData.startTimeInMins else bedtimeData.endTimeInMins
        val hour = currentMins / 60
        val min = currentMins % 60
        
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(min)
            .setTitleText(if (isStartTime) "Select start time" else "Select end time")
            .build()
        
        picker.addOnPositiveButtonClickListener {
            val selectedMins = picker.hour * 60 + picker.minute
            if (isStartTime) {
                bedtimeData.startTimeInMins = selectedMins
            } else {
                bedtimeData.endTimeInMins = selectedMins
            }
            prefsLoader.saveBedtimeData(bedtimeData)
            updateUI()
        }
        
        picker.show(supportFragmentManager, "TIME_PICKER")
    }
}
