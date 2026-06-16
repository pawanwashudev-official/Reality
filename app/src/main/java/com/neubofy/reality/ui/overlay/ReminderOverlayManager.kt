package com.neubofy.reality.ui.overlay

import android.content.Context
import com.neubofy.reality.utils.TerminalLogger

/**
 * DEPRECATED: This overlay manager is no longer used.
 * Reminders are now handled by AlarmService + AlarmActivity for better reliability.
 * Keeping as stub to prevent compilation errors if referenced elsewhere.
 */
class ReminderOverlayManager(private val context: Context) {
    
    @Deprecated("Use AlarmService + AlarmActivity instead")
    fun showOverlay(id: String?, title: String, mins: Int, url: String? = null) {
        TerminalLogger.log("DEPRECATED: ReminderOverlayManager.showOverlay called. Use AlarmService.")
        // No-op - Overlay functionality moved to AlarmActivity
    }

    fun dismissOverlay() {
        // No-op
    }
}
