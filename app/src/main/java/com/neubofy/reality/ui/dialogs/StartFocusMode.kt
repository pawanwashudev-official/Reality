package com.neubofy.reality.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.Constants
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.databinding.DialogFocusModeBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.NotificationTimerManager
import com.neubofy.reality.utils.SavedPreferencesLoader

class StartFocusMode(private val loader: SavedPreferencesLoader, private val onPositiveButtonPressed: () -> Unit) : BaseDialog(loader) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogFocusModeBinding.inflate(layoutInflater)
        val data = loader.getFocusModeData()

        binding.focusModeHoursPicker.apply { minValue = 0; maxValue = 99; setValue(0); setUnit("hours") }
        binding.focusModeMinsPicker.apply { minValue = 0; maxValue = 59; setValue(25); setUnit("mins") }

        // Enforce Universal Blocklist (Block Selected)
        var selectedMode = Constants.FOCUS_MODE_BLOCK_SELECTED
        binding.blockSelected.isChecked = true
        binding.blockAll.visibility = android.view.View.GONE
        binding.blockSelected.visibility = android.view.View.GONE
        binding.modeType.visibility = android.view.View.GONE

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Start") { _, _ ->
                // Validation: Check if blocklist is empty when mode is Block Selected
                if (selectedMode == Constants.FOCUS_MODE_BLOCK_SELECTED && data.selectedApps.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "Blocklist is empty! No apps will be blocked.", android.widget.Toast.LENGTH_LONG).show()
                }

                val totalMs = (binding.focusModeHoursPicker.getValue() * 3600000L) + (binding.focusModeMinsPicker.getValue() * 60000L)
                val newData = RealityBlocker.FocusModeData(
                    isTurnedOn = true,
                    endTime = System.currentTimeMillis() + totalMs,
                    modeType = selectedMode,
                    selectedApps = data.selectedApps,
                    blockedWebsites = data.blockedWebsites
                )
                loader.saveFocusModeData(newData)
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                
                NotificationTimerManager(requireContext()).startTimer(totalMs)
                onPositiveButtonPressed()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
