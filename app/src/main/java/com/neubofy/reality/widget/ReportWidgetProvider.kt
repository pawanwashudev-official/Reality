package com.neubofy.reality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.neubofy.reality.R
import com.neubofy.reality.data.repository.NightlyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Report Widget Provider
 * Displays the latest Nightly Report content in a scrollable list.
 */
class ReportWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.neubofy.reality.widget.ACTION_REFRESH"
        
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, ReportWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, ReportWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, ReportWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            // Re-update headers and content
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_report)
        
        // Background Data Check (Only to get key for Intent)
        GlobalScope.launch(Dispatchers.Main) {
            try {
                // Find latest session with report content
                val sessions = withContext(Dispatchers.IO) { 
                    NightlyRepository.getAllSessions(context).sortedByDescending { it.date }
                }
                val lastSession = sessions.firstOrNull { !it.reportContent.isNullOrEmpty() }
                
                // Create Intent to Launch Activity
                val launchIntent = Intent(context, com.neubofy.reality.ui.activity.NightlyReportActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (lastSession != null) {
                         putExtra("date", lastSession.date)
                    }
                }
                
                val pIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Bind Click to the Icon Container
                views.setOnClickPendingIntent(R.id.card_launcher, pIntent)
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                 e.printStackTrace()
            }
        }
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
