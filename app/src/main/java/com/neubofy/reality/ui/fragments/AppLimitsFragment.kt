package com.neubofy.reality.ui.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.*
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.AppLimitEntity
import com.neubofy.reality.databinding.DialogSetLimitBinding
import com.neubofy.reality.databinding.FragmentAppLimitsBinding
import com.neubofy.reality.databinding.ItemAppLimitBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils
import com.neubofy.reality.utils.TimeTools
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fragment for managing individual app limits.
 * Shows only apps that have limits set, allows creating/editing/deleting limits.
 */
class AppLimitsFragment : Fragment() {

    private var _binding: FragmentAppLimitsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private lateinit var savedPreferencesLoader: SavedPreferencesLoader
    private var limitsList = mutableListOf<AppLimitItem>()
    private var adapter: AppLimitsAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppLimitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedPreferencesLoader = SavedPreferencesLoader(requireContext())
        
        binding.recyclerAppLimits.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppLimitsAdapter()
        binding.recyclerAppLimits.adapter = adapter
        
        binding.fabAddLimit.setOnClickListener {
            showAppSelectionDialog()
        }
        
        loadLimits()
    }
    
    override fun onResume() {
        super.onResume()
        loadLimits()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scope.cancel()
    }
    
    private fun loadLimits() {
        scope.launch(Dispatchers.IO) {
            try {
                val context = context ?: return@launch
                val db = AppDatabase.getDatabase(context)
                val limits = db.appLimitDao().getAllLimits()
                val pm = context.packageManager
                
                // Get usage stats from system
                // Get accurate usage
                val usageStatsMap = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(context)
                
                val items = limits.mapNotNull { limit ->
                    try {
                        val appInfo = pm.getApplicationInfo(limit.packageName, 0)
                        val label = appInfo.loadLabel(pm).toString()
                        
                        // Get real system usage
                        val usedMs = usageStatsMap[limit.packageName] ?: 0L
                        
                        AppLimitItem(
                            packageName = limit.packageName,
                            appName = label,
                            limitEntity = limit,
                            usedMs = usedMs
                        )
                    } catch (e: Exception) {
                        null // App not found
                    }
                }.sortedBy { it.appName.lowercase() }
                
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        limitsList.clear()
                        limitsList.addAll(items)
                        adapter?.notifyDataSetChanged()
                        
                        binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        binding.recyclerAppLimits.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun showAppSelectionDialog() {
        scope.launch(Dispatchers.IO) {
            try {
                val context = context ?: return@launch
                val pm = context.packageManager
                val db = AppDatabase.getDatabase(context)
                val existingLimits = db.appLimitDao().getAllLimits().map { it.packageName }.toSet()
                
                // Get usage stats map for dialog
                // Get accurate usage using UsageUtils
                val usageMap = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(context)

                data class AppInfo(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable?, val usedMs: Long)
                val apps = mutableListOf<AppInfo>()
                
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                
                val resolveInfoList = pm.queryIntentActivities(intent, 0)
                for (resolveInfo in resolveInfoList) {
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg != context.packageName && !existingLimits.contains(pkg) && !apps.any { it.packageName == pkg }) {
                        val label = resolveInfo.loadLabel(pm).toString()
                        val icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }
                        val used = usageMap[pkg] ?: 0L
                        apps.add(AppInfo(pkg, label, icon, used))
                    }
                }
                apps.sortByDescending { it.usedMs } // Sort by usage
                
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    
                    if (apps.isEmpty()) {
                        Toast.makeText(context, "All apps already have limits", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    // Create custom RecyclerView dialog
                    val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
                        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                        setPadding(0, 16, 0, 16)
                    }
                    
                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Select App to Limit")
                        .setView(recyclerView)
                        .setNegativeButton("Cancel", null)
                        .create()
                    
                    // Custom Adapter
                    recyclerView.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                        inner class VH(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
                            val icon: android.widget.ImageView = itemView.findViewById(com.neubofy.reality.R.id.iv_app_icon)
                            val name: android.widget.TextView = itemView.findViewById(com.neubofy.reality.R.id.tv_app_name)
                            // We recycle item_app_select but might want to show usage...
                            // For now layout doesn't have usage textview, just name/icon.
                            // I should append usage to name.
                        }
                        
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                            val view = LayoutInflater.from(parent.context).inflate(com.neubofy.reality.R.layout.item_app_select, parent, false)
                            return VH(view)
                        }
                        
                        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                            val vh = holder as VH
                            val app = apps[position]
                            val usedMins = TimeUnit.MILLISECONDS.toMinutes(app.usedMs)
                            vh.name.text = "${app.label} (${usedMins}m today)"
                            vh.icon.setImageDrawable(app.icon)
                            vh.itemView.setOnClickListener {
                                dialog.dismiss()
                                showSetLimitDialog(app.packageName, app.label, null)
                            }
                        }
                        
                        override fun getItemCount() = apps.size
                    }
                    
                    dialog.show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading apps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showSetLimitDialog(packageName: String, appName: String, existingLimit: AppLimitEntity?) {
        val context = requireContext()
        val dialogBinding = DialogSetLimitBinding.inflate(layoutInflater)
        
        // Initial State
        var currentLimit = existingLimit?.limitInMinutes ?: 60 // Default 1 hour
        var isStrict = existingLimit?.isStrict ?: false
        val activePeriods = ArrayList<Pair<String,String>>()
        
        // Parse existing periods
        if (existingLimit != null) {
            try {
                val arr = JSONArray(existingLimit.activePeriodsJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    activePeriods.add(Pair(obj.getString("start"), obj.getString("end")))
                }
            } catch (e: Exception) {}
        }
        
        // Lock Logic - Check GLOBAL strict mode
        val isMaintenance = StrictLockUtils.isMaintenanceWindow()
        val globalStrictData = savedPreferencesLoader.getStrictModeData()
        val isGlobalStrictEnabled = globalStrictData.isEnabled
        
        // An item is locked if:
        // 1. Global strict mode is ENABLED, AND
        // 2. This item has isStrict = true
        // When locked: cannot edit limit, cannot uncheck strict checkbox
        val isLocked = if (isMaintenance) {
            false  // Maintenance window always allows editing
        } else {
            isGlobalStrictEnabled && (isStrict || globalStrictData.isAppLimitLocked)
        }
        
        // Bind UI
        dialogBinding.seekBarLimit.valueFrom = 0f
        dialogBinding.seekBarLimit.valueTo = 480f
        dialogBinding.seekBarLimit.stepSize = 15f
        dialogBinding.seekBarLimit.value = currentLimit.toFloat()
        dialogBinding.tvLimitValue.text = "${currentLimit / 60}h ${currentLimit % 60}m"
        dialogBinding.cbStrict.isChecked = isStrict
        
        if (isLocked) {
            // Disable all UI elements when locked
            dialogBinding.seekBarLimit.isEnabled = false
            dialogBinding.cbStrict.isEnabled = false
            dialogBinding.cbStrict.isClickable = false
            dialogBinding.btnAddPeriod.isEnabled = false
            dialogBinding.tvStrictHint.text = "🔒 Locked by Strict Mode. Disable global Strict Mode first."
            dialogBinding.tvStrictHint.setTextColor(0xFFFF0000.toInt())
            
            // CRITICAL: Force checkbox to stay checked
            dialogBinding.cbStrict.setOnCheckedChangeListener { _, _ ->
                dialogBinding.cbStrict.isChecked = true
            }
        }
        
        dialogBinding.seekBarLimit.addOnChangeListener { _, value, _ ->
            currentLimit = value.toInt()
            dialogBinding.tvLimitValue.text = "${currentLimit / 60}h ${currentLimit % 60}m"
        }
        
        // Chips Logic
        fun refreshChips() {
            dialogBinding.chipGroupPeriods.removeAllViews()
            activePeriods.forEachIndexed { index, period ->
                val chip = Chip(context)
                chip.text = "${period.first} - ${period.second}"
                chip.isCloseIconVisible = !isLocked
                chip.setOnCloseIconClickListener {
                    activePeriods.removeAt(index)
                    refreshChips()
                }
                dialogBinding.chipGroupPeriods.addView(chip)
            }
        }
        refreshChips()
        
        dialogBinding.btnAddPeriod.setOnClickListener {
            showTimePicker("Start Time") { h, m ->
                val start = String.format("%02d:%02d", h, m)
                showTimePicker("End Time") { eh, em ->
                    val end = String.format("%02d:%02d", eh, em)
                    activePeriods.add(Pair(start, end))
                    refreshChips()
                }
            }
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Set Limit for $appName")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                if (isLocked) return@setPositiveButton
                
                if (currentLimit <= 0) {
                    Toast.makeText(context, "Limit must be at least 1 minute", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val arr = JSONArray()
                activePeriods.forEach { 
                    arr.put(JSONObject().apply { put("start", it.first); put("end", it.second) }) 
                }
                val json = arr.toString()
                
                val newEntity = AppLimitEntity(
                    packageName = packageName,
                    limitInMinutes = currentLimit,
                    isStrict = dialogBinding.cbStrict.isChecked,
                    activePeriodsJson = json
                )
                
                scope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    db.appLimitDao().insert(newEntity)
                    
                    // Clean legacy limits
                    val usageData = savedPreferencesLoader.getUsageLimitData()
                    if (usageData.appLimits.containsKey(packageName)) {
                        usageData.appLimits.remove(packageName)
                        savedPreferencesLoader.saveUsageLimitData(usageData)
                    }
                    
                    withContext(Dispatchers.Main) {
                        context.sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                        loadLimits()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Remove") { _, _ ->
                if (isLocked) {
                    Toast.makeText(context, "Cannot remove active strict limit!", Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                
                // Requirement: Uncheck strict before removing
                if (existingLimit?.isStrict == true) {
                    Toast.makeText(context, "Please uncheck 'Strict' and Save before removing this limit.", Toast.LENGTH_LONG).show()
                    return@setNeutralButton
                }
                
                if (existingLimit == null) return@setNeutralButton
                
                scope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).appLimitDao().deleteByPackage(packageName)
                    
                    // Clean legacy
                    val usageData = savedPreferencesLoader.getUsageLimitData()
                    usageData.appLimits.remove(packageName)
                    savedPreferencesLoader.saveUsageLimitData(usageData)
                    
                    withContext(Dispatchers.Main) {
                        context.sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                        loadLimits()
                    }
                }
            }
            .show()
    }
    
    private fun showTimePicker(title: String, onTimeSet: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(12)
            .setMinute(0)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { onTimeSet(picker.hour, picker.minute) }
        picker.show(childFragmentManager, "time_picker")
    }
    
    inner class AppLimitsAdapter : RecyclerView.Adapter<AppLimitsAdapter.VH>() {
        inner class VH(val binding: ItemAppLimitBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemAppLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = limitsList[position]
            holder.binding.tvAppName.text = item.appName
            
            val usedMins = TimeUnit.MILLISECONDS.toMinutes(item.usedMs)
            val limitMins = item.limitEntity.limitInMinutes
            
            holder.binding.tvLimitInfo.text = "${usedMins}m / ${limitMins}m"
            holder.binding.progressBar.max = limitMins
            holder.binding.progressBar.progress = usedMins.toInt().coerceAtMost(limitMins)
            
            if (usedMins >= limitMins) {
                holder.binding.tvLimitInfo.setTextColor(0xFFFF6B6B.toInt())
            } else {
                holder.binding.tvLimitInfo.setTextColor(0xFFFFFFFF.toInt())
            }
            
            // Strict indicator
            if (item.limitEntity.isStrict) {
                holder.binding.ivStrictIcon.visibility = View.VISIBLE
            } else {
                holder.binding.ivStrictIcon.visibility = View.GONE
            }
            
            // Load icon
            scope.launch(Dispatchers.IO) {
                try {
                    val icon = holder.itemView.context.packageManager.getApplicationIcon(item.packageName)
                    withContext(Dispatchers.Main) { 
                        holder.binding.ivAppIcon.setImageDrawable(icon) 
                    }
                } catch(e: Exception) {}
            }
            
            holder.itemView.setOnClickListener {
                showSetLimitDialog(item.packageName, item.appName, item.limitEntity)
            }
        }
        
        override fun getItemCount() = limitsList.size
    }
    
    data class AppLimitItem(
        val packageName: String,
        val appName: String,
        val limitEntity: AppLimitEntity,
        val usedMs: Long
    )
}
