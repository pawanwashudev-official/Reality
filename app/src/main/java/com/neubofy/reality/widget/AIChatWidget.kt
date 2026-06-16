package com.neubofy.reality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.neubofy.reality.R
import com.neubofy.reality.ui.activity.AIChatActivity
import android.widget.RemoteViews

/**
 * Quick Access Widget for Reality AI
 */
class AIChatWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Refresh preferences to ensure latest state is used
        // Helper method to update all widgets
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // Check if voice auto-trigger is enabled
            val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
            val voiceAuto = prefs.getBoolean("widget_voice_auto", false)
            
            // Intent to launch AI Chat (Popup Version)
            val intent = Intent(context, com.neubofy.reality.ui.activity.PopupAIChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("extra_mode", "pro") // Launch directly into Pro Mode
                putExtra("voice_auto", voiceAuto) // Auto-trigger voice if enabled
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_ai_chat)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
