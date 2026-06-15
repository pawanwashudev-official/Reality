package com.neubofy.reality.utils

import android.app.Activity
import android.content.Intent
import com.neubofy.reality.ui.activity.RealityProActivity

object RealityProManager {
    /**
     * Checks if Reality Pro is enabled. If not, redirects the user to the
     * Reality Pro activation page and finishes the calling activity.
     * @return true if access is allowed, false if blocked
     */
    fun checkAccess(activity: Activity): Boolean {
        val isEnabled = FeatureManager(activity).isRealityProEnabled()
        if (!isEnabled) {
            val intent = Intent(activity, RealityProActivity::class.java)
            activity.startActivity(intent)
            activity.finish()
            return false
        }
        return true
    }
}
