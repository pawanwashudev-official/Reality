package com.neubofy.reality.ui.fragments.anti_uninstall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.FragmentSetupTimedModeBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import android.content.Intent

class SetupTimedModeFragment : Fragment() {

    private var _binding: FragmentSetupTimedModeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupTimedModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide Calendar as we are using fixed 12h timer
        binding.calendarView.visibility = View.GONE
        
        // Update Text to reflect 12h policy
        // We assume binding has some text views, or we rely on the button text
        binding.turnOnTimed.text = "Lock for 12 Hours"
        
        binding.turnOnTimed.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Activiting Strict Mode")
                .setMessage("Strict Mode will be enabled for 12 HOURS. You cannot turn it off or change settings during this time (except in Maintenance Window 00:00-00:10).")
                .setPositiveButton("Enable Lock") { _, _ ->
                     enable12HourLock()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Block changes checkbox (keep if relevant, or force it)
        binding.blockChanges.isChecked = true
        binding.blockChanges.isEnabled = false // Force enabled for strict security
    }

    private fun enable12HourLock() {
        val loader = SavedPreferencesLoader(requireContext())
        val endTime = System.currentTimeMillis() + (12 * 60 * 60 * 1000L)
        
        val data = Constants.StrictModeData(
            isEnabled = true,
            modeType = Constants.StrictModeData.MODE_TIMER,
            timerEndTime = endTime
        )
        loader.saveStrictModeData(data)
        
        requireContext().sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL))
        Toast.makeText(requireContext(), "Strict Mode Enabled for 12 Hours", Toast.LENGTH_SHORT).show()
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}