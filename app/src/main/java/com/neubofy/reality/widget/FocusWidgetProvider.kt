package com.neubofy.reality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.neubofy.reality.R
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.FocusStatusManager
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Focus Mode Widget - Quick toggle from home screen
 * Shows current status with remaining time and allows 1-tap toggle
 */
class FocusWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_FOCUS = "com.neubofy.reality.widget.TOGGLE_FOCUS"
        
        // Update all widgets - call this from anywhere to sync widget state
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, FocusWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, FocusWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_FOCUS -> {
                toggleFocusMode(context)
                
                // Update all widgets
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, FocusWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                
                for (widgetId in widgetIds) {
                    updateWidget(context, appWidgetManager, widgetId)
                }
                
                // Notify the service to refresh
                val refreshIntent = Intent("com.neubofy.reality.refresh.focus_mode")
                refreshIntent.setPackage(context.packageName)
                context.sendBroadcast(refreshIntent)
            }
            // Also update on screen on/off to keep widget in sync
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_focus_mode)
        
        // Get comprehensive focus status (includes schedules, bedtime, etc.)
        val status = runBlocking {
            try {
                FocusStatusManager(context).getCurrentStatus()
            } catch (e: Exception) {
                null
            }
        }
        
        val loader = SavedPreferencesLoader(context)
        val focusData = loader.getFocusModeData()
        val isManualFocusActive = focusData.isTurnedOn && focusData.endTime > System.currentTimeMillis()
        
        // Check if any focus mode is active (manual, schedule, bedtime)
        val isAnyFocusActive = status?.isActive == true || isManualFocusActive
        
        // Update UI based on status
        if (isAnyFocusActive) {
            val endTime = status?.endTime ?: focusData.endTime
            val remaining = endTime - System.currentTimeMillis()
            
            if (remaining > 0) {
                val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                val mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                views.setTextViewText(R.id.widget_status, timeStr)
            } else {
                views.setTextViewText(R.id.widget_status, "Active")
            }
            
            // Show source of focus
            val source = status?.title ?: "Focus Mode"
            if (source.contains("Bedtime", true)) {
                views.setTextViewText(R.id.widget_action, "BEDTIME")
            } else if (source.contains("Schedule", true)) {
                views.setTextViewText(R.id.widget_action, "SCHEDULED")
            } else {
                views.setTextViewText(R.id.widget_action, "TAP TO STOP")
            }
            views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background_active)
        } else {
            views.setTextViewText(R.id.widget_status, "OFF")
            views.setTextViewText(R.id.widget_action, "TAP TO START")
            views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background)
        }
        
        // Set click action
        val toggleIntent = Intent(context, FocusWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_FOCUS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun toggleFocusMode(context: Context) {
        val loader = SavedPreferencesLoader(context)
        val focusData = loader.getFocusModeData()
        
        // Check if schedule/bedtime is active - can't toggle those
        val status = runBlocking {
            try {
                FocusStatusManager(context).getCurrentStatus()
            } catch (e: Exception) {
                null
            }
        }
        
        if (status?.isActive == true && status.type != com.neubofy.reality.utils.FocusType.MANUAL_FOCUS) {
            // Can't stop scheduled focus - just return
            return
        }
        
        if (focusData.isTurnedOn && focusData.endTime > System.currentTimeMillis()) {
            // Currently ON -> Turn OFF
            focusData.isTurnedOn = false
            focusData.endTime = -1
            loader.saveFocusModeData(focusData)
        } else {
            // Currently OFF -> Turn ON for 1 hour (default)
            focusData.isTurnedOn = true
            focusData.endTime = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour
            loader.saveFocusModeData(focusData)
        }
    }
}
