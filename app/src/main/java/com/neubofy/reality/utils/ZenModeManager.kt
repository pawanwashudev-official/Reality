package com.neubofy.reality.utils

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/**
 * ZenModeManager - Reality Sleep Mode for Android 15+
 *
 * Uses the Android 15 AutomaticZenRule API with ZenDeviceEffects
 * to enable grayscale, wallpaper dimming, and dark mode.
 *
 * KEY INSIGHT: TYPE_BEDTIME is restricted to Google's Wellbeing app only.
 * We use TYPE_OTHER which still supports ZenDeviceEffects (grayscale, dim, etc).
 * 
 * Additionally, setOwner() requires a ConditionProviderService. Since we don't
 * have one, we use setConfigurationActivity() which makes us the rule owner
 * through the package ownership model.
 */
object ZenModeManager {

    private const val RULE_NAME = "Reality Sleep"
    private const val PREF_NAME = "zen_mode_prefs"
    private const val PREF_RULE_ID = "zen_rule_id"

    // Using a fixed, simple condition URI
    private val CONDITION_ID: Uri = Uri.parse("reality://sleep_mode")

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 35

    /**
     * Enable or disable Reality Sleep Mode.
     */
    fun setZenState(context: Context, active: Boolean) {
        if (!isSupported()) return

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (!nm.isNotificationPolicyAccessGranted) {
                TerminalLogger.log("ZEN: No DND permission, skipping")
                return
            }

            if (active) {
                enableZenMode(context, nm)
            } else {
                disableZenMode(context, nm)
            }
        } catch (e: Exception) {
            TerminalLogger.log("ZEN ERROR: ${e.message}")
        }
    }

    @Suppress("NewApi")
    private fun enableZenMode(context: Context, nm: NotificationManager) {
        // Step 1: Clean up any old rule
        val oldRuleId = getRuleId(context)
        if (oldRuleId != null) {
            try {
                nm.removeAutomaticZenRule(oldRuleId)
                TerminalLogger.log("ZEN: Old rule removed")
            } catch (e: Exception) {
                TerminalLogger.log("ZEN: Old rule cleanup failed: ${e.message}")
            }
            clearRuleId(context)
        }

        // Step 2: Build fresh ZenDeviceEffects
        val effects = android.service.notification.ZenDeviceEffects.Builder()
            .setShouldDisplayGrayscale(true)
            .setShouldDimWallpaper(true)
            .setShouldUseNightMode(true)
            .setShouldSuppressAmbientDisplay(true)
            .build()

        // Step 3: Build a ZenPolicy (allow alarms/media, silence everything else)
        val zenPolicy = ZenPolicy.Builder()
            .allowAlarms(true)
            .allowMedia(true)
            .allowSystem(false)
            .allowReminders(false)
            .allowEvents(false)
            .build()

        // Step 4: Create a fresh rule using TYPE_OTHER (not TYPE_BEDTIME which is restricted
        //         to Google's Digital Wellbeing app) and configurationActivity (not owner
        //         which requires a ConditionProviderService)
        val configActivity = ComponentName(
            context,
            "com.neubofy.reality.ui.activity.SettingsActivity"
        )

        val rule = AutomaticZenRule.Builder(RULE_NAME, CONDITION_ID)
            .setConfigurationActivity(configActivity)
            .setDeviceEffects(effects)
            .setZenPolicy(zenPolicy)
            .setEnabled(true)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .build()

        // Step 5: Add the rule → get a system-assigned ID
        val ruleId = nm.addAutomaticZenRule(rule)
        saveRuleId(context, ruleId)
        TerminalLogger.log("ZEN: Rule created with ID: $ruleId")

        // Step 6: ACTIVATE the condition → triggers the effects
        val condition = Condition(
            CONDITION_ID,
            RULE_NAME,
            Condition.STATE_TRUE  // STATE_TRUE = active = effects ON
        )
        nm.setAutomaticZenRuleState(ruleId, condition)
        TerminalLogger.log("ZEN: Reality Sleep ACTIVATED (Grayscale + Dim + Dark)")
    }

    @Suppress("NewApi")
    private fun disableZenMode(context: Context, nm: NotificationManager) {
        val ruleId = getRuleId(context) ?: return

        try {
            // First deactivate the condition
            val condition = Condition(
                CONDITION_ID,
                RULE_NAME,
                Condition.STATE_FALSE  // STATE_FALSE = inactive = effects OFF
            )
            nm.setAutomaticZenRuleState(ruleId, condition)

            // Then remove the rule entirely for a clean state
            nm.removeAutomaticZenRule(ruleId)
            clearRuleId(context)
            TerminalLogger.log("ZEN: Reality Sleep DEACTIVATED + rule removed")
        } catch (e: Exception) {
            // Best effort cleanup
            try {
                nm.removeAutomaticZenRule(ruleId)
            } catch (_: Exception) {}
            clearRuleId(context)
            TerminalLogger.log("ZEN: Cleanup after error: ${e.message}")
        }
    }

    private fun getRuleId(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_RULE_ID, null)
    }

    private fun saveRuleId(context: Context, id: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_RULE_ID, id).apply()
    }

    private fun clearRuleId(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(PREF_RULE_ID).apply()
    }
}
