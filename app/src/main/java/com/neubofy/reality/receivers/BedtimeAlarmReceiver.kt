package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.BedtimeAlarmScheduler
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BedtimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        TerminalLogger.log("BEDTIME RECEIVER: Bedtime transition alarm triggered!")

        CoroutineScope(Dispatchers.IO).launch {
            // Rebuild the box immediately
            BlockCache.rebuildBox(context)
            
            // Notify AppBlockerService to reload/refresh
            val refreshIntent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(refreshIntent)

            // Schedule the next transition alarm (Bedtime alarm cycle)
            BedtimeAlarmScheduler.scheduleNextBedtimeAlarm(context)
        }
    }
}
