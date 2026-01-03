package com.neubofy.reality.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.neubofy.reality.Constants
import java.util.Locale

class GeneralFeaturesService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_ANTI_UNINSTALL = "com.neubofy.reality.refresh.anti_uninstall"
    }


    private var lastPackageName: String? = null // Store the last active app's package name

    private var isAntiUninstallOn = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        super.onAccessibilityEvent(event)

        if (isAntiUninstallOn) {
            if (event?.packageName == "com.android.settings") {
                traverseNodesForKeywords(rootInActiveWindow)
            }
        }

        try {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val currentPackageName = event.packageName?.toString()
                // Check if the app has changed
                if (currentPackageName != null && currentPackageName != lastPackageName) {
                    lastPackageName = currentPackageName // Update the last package name
                }
            }
        } catch (_: Exception) {
        }


    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
        setupAntiUninstall()
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent != null) {
                when (intent.action) {
                    INTENT_ACTION_REFRESH_ANTI_UNINSTALL -> setupAntiUninstall()
                }
            }
        }
    }


    fun setupAntiUninstall() {
        val info = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = info.getBoolean("is_anti_uninstall_on", false)

    }


    private fun traverseNodesForKeywords(
        node: AccessibilityNodeInfo?
    ) {
        if (node == null) {
            return
        }
        if (node.className != null && node.className == "android.widget.TextView") {
            val nodeText = node.text
            if (nodeText != null) {
                val editTextContent = nodeText.toString().lowercase(Locale.getDefault())
                // Check for 'reality' instead of 'digipaws' as per rebranding
                if (editTextContent.lowercase(Locale.getDefault()).contains("reality")) {
                    pressHome()
                }
            }
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            traverseNodesForKeywords(childNode)
        }
    }
}