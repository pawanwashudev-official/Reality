package com.neubofy.reality.utils

import android.app.Activity
import android.content.Intent
import com.neubofy.reality.ui.activity.RealityEliteActivity

object RealityProManager {
    /**
     * Checks if Reality Elite Member is enabled. If not, redirects the user to the
     * Reality Elite Member activation page and finishes the calling activity.
     * @return true if access is allowed, false if blocked
     */

    /**
     * Checks if Reality Elite Member is verified, ignoring whether it is currently toggled on.
     * Used for features like Tapasya that only require verification but not the global toggle.
     */
    fun checkVerification(activity: Activity): Boolean {
        val featureManager = FeatureManager(activity)
        val isVerified = featureManager.isRealityProVerified()
        val isTrialActive = featureManager.isTrialActive()

        if (!(isVerified || isTrialActive)) {
            val intent = Intent(activity, RealityEliteActivity::class.java)
            activity.startActivity(intent)
            activity.finish()
            return false
        }
        return true
    }

    fun checkAccess(activity: Activity): Boolean {
        val featureManager = FeatureManager(activity)
        val isEnabled = featureManager.isRealityProEnabled()
        val isVerified = featureManager.isRealityProVerified()
        val isTrialActive = featureManager.isTrialActive()

        if (!(isEnabled && (isVerified || isTrialActive))) {
            val intent = Intent(activity, RealityEliteActivity::class.java)
            activity.startActivity(intent)
            activity.finish()
            return false
        }
        return true
    }
}
