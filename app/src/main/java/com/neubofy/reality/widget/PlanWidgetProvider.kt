package com.neubofy.reality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.neubofy.reality.R
import com.neubofy.reality.data.repository.NightlyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Plan Widget Provider
 * Displays the AI-generated Plan (Tasks & Events) from Step 9.
 */
class PlanWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.neubofy.reality.widget.PLAN_REFRESH"
        private const val STEP_GENERATE_PLAN = 9 
        
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, PlanWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, PlanWidgetProvider::class.java).apply {
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
            val widgetComponent = ComponentName(context, PlanWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_plan)

        // Find valid session
        GlobalScope.launch(Dispatchers.Main) {
            try {
                // Logic: Find latest session where Step 9 has resultJson with items
                val sessions = withContext(Dispatchers.IO) { 
                    NightlyRepository.getAllSessions(context).sortedByDescending { it.date }
                }
                
                var foundDateString: String? = null
                
                withContext(Dispatchers.IO) {
                    for (session in sessions) {
                        try {
                            val date = LocalDate.parse(session.date)
                            val stepData = NightlyRepository.getStepResultJson(context, date, STEP_GENERATE_PLAN)
                            if (!stepData.isNullOrEmpty() && stepData.length > 50) {
                                foundDateString = session.date
                                break
                            }
                        } catch (e: Exception) {/* ignore */}
                    }
                }
                
                // Create Intent to Launch Activity
                val launchIntent = Intent(context, com.neubofy.reality.ui.activity.NightlyPlanActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (foundDateString != null) {
                         putExtra("date", java.time.LocalDate.parse(foundDateString))
                    }
                }
                
                val pIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                views.setOnClickPendingIntent(R.id.card_launcher, pIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                 e.printStackTrace()
            }
        }
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
