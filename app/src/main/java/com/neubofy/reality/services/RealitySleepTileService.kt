package com.neubofy.reality.services

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.ZenModeManager

/**
 * RealitySleepTileService - Quick Settings tile for Reality Sleep Mode.
 *
 * This tile is a DIRECT toggle for sleep mode (Grayscale, Dim, Dark).
 * It works independently from the Settings toggle (which controls automatic bedtime sync).
 *
 * Rules:
 * - If bedtime is currently RUNNING: tile is locked ON, user cannot turn off.
 * - If bedtime is NOT running: user can freely toggle ON/OFF.
 * - Only available on Android 15+ (SDK 35).
 */
class RealitySleepTileService : TileService() {

    companion object {
        private const val PREF_NAME = "reality_sleep_tile"
        private const val KEY_TILE_ACTIVE = "tile_manually_active"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!ZenModeManager.isSupported()) {
            TerminalLogger.log("TILE: Not supported (requires Android 15)")
            return
        }

        // Check DND permission
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            TerminalLogger.log("TILE: No DND permission")
            // Try to open settings for permission
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityAndCollapse(intent)
            } catch (e: Exception) {
                TerminalLogger.log("TILE: Could not open DND settings: ${e.message}")
            }
            return
        }

        val isBedtime = BlockCache.isBedtimeCurrentlyActive

        if (isBedtime) {
            // Bedtime is running → cannot turn off, self-heal to ON
            TerminalLogger.log("TILE: Bedtime active — locked ON")
            ZenModeManager.setZenState(this, true)
            setTileManualState(true)
        } else {
            // Bedtime is not running → free toggle
            val isCurrentlyActive = isTileManuallyActive()
            if (isCurrentlyActive) {
                ZenModeManager.setZenState(this, false)
                setTileManualState(false)
                TerminalLogger.log("TILE: Sleep mode OFF (manual)")
            } else {
                ZenModeManager.setZenState(this, true)
                setTileManualState(true)
                TerminalLogger.log("TILE: Sleep mode ON (manual)")
            }
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        if (!ZenModeManager.isSupported()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Reality Sleep"
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "Android 15+ only"
            }
            tile.updateTile()
            return
        }

        val isBedtime = BlockCache.isBedtimeCurrentlyActive
        val isManuallyActive = isTileManuallyActive()

        tile.label = "Reality Sleep"

        if (isBedtime) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "Bedtime 🔒"
            }
        } else if (isManuallyActive) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "On"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "Off"
            }
        }

        tile.updateTile()
    }

    /**
     * Track tile's manual state in SharedPreferences.
     * This is more reliable than querying the Zen rule state.
     */
    private fun isTileManuallyActive(): Boolean {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TILE_ACTIVE, false)
    }

    private fun setTileManualState(active: Boolean) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_TILE_ACTIVE, active).apply()
    }
}
