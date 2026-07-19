package com.neubofy.reality.ui.activity

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.neubofy.reality.R
import com.neubofy.reality.services.TapasyaManager
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.*
import java.text.SimpleDateFormat
import java.util.*

class BlockerDetailsActivity : BaseActivity() {

    private lateinit var layoutActiveBlockers: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var tvStatusDnd: TextView
    private lateinit var tvStatusDarkMode: TextView
    private lateinit var tvStatusDimming: TextView
    
    private val adapter = TargetAppAdapter()
    private val savedPreferencesLoader by lazy { SavedPreferencesLoader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocker_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = "Blocker Details"

        layoutActiveBlockers = findViewById(R.id.layout_active_blockers_container)
        recyclerView = findViewById(R.id.recyclerBlocked)
        emptyView = findViewById(R.id.emptyView)
        tvStatusDnd = findViewById(R.id.tv_status_dnd)
        tvStatusDarkMode = findViewById(R.id.tv_status_dark_mode)
        tvStatusDimming = findViewById(R.id.tv_status_dimming)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        // 1. Update System Features Status
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val interruptionFilter = nm.currentInterruptionFilter
        val isDndActive = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        tvStatusDnd.text = if (isDndActive) "Active" else "Inactive"
        tvStatusDnd.setTextColor(if (isDndActive) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))

        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        tvStatusDarkMode.text = if (isDarkMode) "Active" else "Inactive"
        tvStatusDarkMode.setTextColor(if (isDarkMode) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))

        val bedtimeData = savedPreferencesLoader.getBedtimeData()
        val isBedtimeActive = isBedtimeActiveNow(bedtimeData)
        tvStatusDimming.text = if (isBedtimeActive) "Active (Reality Sleep)" else "Inactive"
        tvStatusDimming.setTextColor(if (isBedtimeActive) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))

        // 2. Load Active Blocker Sessions
        layoutActiveBlockers.removeAllViews()
        val now = SecureTimeProvider.currentTimeMillis(this)
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)

        var hasAnyBlocker = false

        // Tapasya Mode
        val tapasyaState = TapasyaManager.getCurrentState(this)
        if (tapasyaState.isSessionActive) {
            addActiveBlockerCard(
                title = "Tapasya Mode",
                reason = "All target apps blocked to maximize focus & build discipline.",
                endTimeStr = "Active until stopped",
                colorHex = "#9C27B0" // Deep Purple
            )
            hasAnyBlocker = true
        }

        // Bedtime Mode
        if (isBedtimeActive) {
            val endMins = bedtimeData.endTimeInMins
            val endStr = formatMinsToTime(endMins)
            addActiveBlockerCard(
                title = "Bedtime Mode",
                reason = "Bedtime schedule is active. Sleeping hours protected.",
                endTimeStr = "Ends at $endStr",
                colorHex = "#E91E63" // Pink/Rose
            )
            hasAnyBlocker = true
        }

        // Manual Blocker (Omit duplicate if started by Tapasya)
        val manualBlocker = savedPreferencesLoader.getFocusModeData()
        if (manualBlocker.isTurnedOn && manualBlocker.endTime > now && !manualBlocker.isTapasyaTriggered) {
            val df = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val endStr = df.format(Date(manualBlocker.endTime))
            addActiveBlockerCard(
                title = "Manual Blocker Session",
                reason = "You started a manual blocking session to prevent distraction.",
                endTimeStr = "Ends at $endStr",
                colorHex = "#2196F3" // Blue
            )
            hasAnyBlocker = true
        }

        if (!hasAnyBlocker) {
            val noBlockerText = TextView(this).apply {
                text = "No active blocker sessions right now."
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            layoutActiveBlockers.addView(noBlockerText)
        }

        // 3. Load Target Apps
        val activeBlockedMap = BlockCache.getAllBlockedApps()
        val list = mutableListOf<AppItem>()
        
        if (activeBlockedMap.isNotEmpty()) {
            for ((pkg, reasons) in activeBlockedMap) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    list.add(AppItem(pkg, label, reasons.joinToString(", ")))
                } catch (e: PackageManager.NameNotFoundException) {
                    // App uninstalled
                }
            }
        } else {
            // Fallback: show configured Focus Mode apps with "Inactive" status
            val focusApps = savedPreferencesLoader.getFocusModeSelectedApps()
            for (pkg in focusApps) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    list.add(AppItem(pkg, label, "Focus Mode List (Inactive)"))
                } catch (e: PackageManager.NameNotFoundException) {
                    // App uninstalled
                }
            }
        }

        list.sortBy { it.name.lowercase() }
        adapter.setData(list)
        emptyView.isVisible = list.isEmpty()
        recyclerView.isVisible = list.isNotEmpty()
    }

    private fun addActiveBlockerCard(title: String, reason: String, endTimeStr: String, colorHex: String) {
        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp
            radius = 14.dp.toFloat()
            setCardBackgroundColor(Color.parseColor("#121212")) // Slate dark glass background
            strokeColor = Color.parseColor(colorHex)
            strokeWidth = 2
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val tvTitle = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor(colorHex))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvReason = TextView(this).apply {
            text = reason
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 13f
            val layoutParamsVal = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParamsVal.setMargins(0, 6, 0, 8)
            layoutParams = layoutParamsVal
        }

        val tvEndTime = TextView(this).apply {
            text = endTimeStr
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.ITALIC)
        }

        container.addView(tvTitle)
        container.addView(tvReason)
        container.addView(tvEndTime)
        card.addView(container)
        layoutActiveBlockers.addView(card)
    }

    private fun formatMinsToTime(minutes: Int): String {
        val hour = minutes / 60
        val min = minutes % 60
        val ampm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, min, ampm)
    }

    // Helper extensions inside class
    private val Int.sp: Float get() = this.toFloat()
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    data class AppItem(val packageName: String, val name: String, val reasons: String)

    inner class TargetAppAdapter : RecyclerView.Adapter<TargetAppAdapter.ViewHolder>() {
        private val items = mutableListOf<AppItem>()

        fun setData(newItems: List<AppItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
            val reasons: TextView = view.findViewById(R.id.appReasons)
            val status: TextView = view.findViewById(R.id.appStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.reasons.text = if (item.reasons.isNotEmpty()) "${item.packageName} • ${item.reasons}" else item.packageName
            
            val isCurrentlyBlocked = BlockCache.getAllBlockedApps().containsKey(item.packageName)
            holder.status.text = if (isCurrentlyBlocked) "BLOCKED" else "MONITORED"
            holder.status.setTextColor(if (isCurrentlyBlocked) Color.parseColor("#FF5252") else Color.parseColor("#4CAF50"))

            try {
                val iconDrawable = packageManager.getApplicationIcon(item.packageName)
                holder.icon.setImageDrawable(iconDrawable)
            } catch (e: Exception) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }

        override fun getItemCount() = items.size
    }

    private fun isBedtimeActiveNow(bedtime: com.neubofy.reality.Constants.BedtimeData): Boolean {
        if (!bedtime.isEnabled) return false
        val cal = Calendar.getInstance()
        cal.timeInMillis = SecureTimeProvider.currentTimeMillis(this)
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = bedtime.startTimeInMins
        val end = bedtime.endTimeInMins
        return if (start < end) {
            currentMins in start until end
        } else if (start > end) {
            currentMins >= start || currentMins < end
        } else {
            false
        }
    }
}
