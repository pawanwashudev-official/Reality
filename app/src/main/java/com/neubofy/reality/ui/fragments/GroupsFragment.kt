package com.neubofy.reality.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.*
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.AppGroupEntity
import com.neubofy.reality.databinding.DialogAddGroupBinding
import com.neubofy.reality.databinding.FragmentGroupsBinding
import com.neubofy.reality.databinding.ItemAppGroupBinding
import com.neubofy.reality.ui.activity.SelectAppsActivity
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.TimeTools
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroupsFragment : Fragment() {

    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val appGroupList = mutableListOf<AppGroupEntity>()
    private lateinit var adapter: AppGroupAdapter
    private lateinit var prefsLoader: SavedPreferencesLoader
    private var usageMap: Map<String, Long> = emptyMap()

    // For app selection result
    private var pendingDialogBinding: DialogAddGroupBinding? = null
    private var pendingSelectedPackages = mutableListOf<String>()
    private var lastSaveTimestamp = 0L
    
    private val selectAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val selected = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (selected != null) {
                pendingSelectedPackages.clear()
                pendingSelectedPackages.addAll(selected)
                pendingDialogBinding?.tvSelectedApps?.text = "${selected.size} apps selected"
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefsLoader = SavedPreferencesLoader(requireContext())
        refreshUsageData()

        adapter = AppGroupAdapter(appGroupList)
        binding.recyclerGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerGroups.adapter = adapter
        
        binding.fabAddGroup.setOnClickListener {
            showGroupDialog(null)
        }

        loadGroups()
    }
    
    private fun refreshUsageData() {
        scope.launch(Dispatchers.IO) {
            val map = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(requireContext())
            withContext(Dispatchers.Main) {
                usageMap = map
                if (::adapter.isInitialized) adapter.notifyDataSetChanged()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshUsageData()
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scope.cancel()
    }
    
    private fun loadGroups() {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val groups = db.appGroupDao().getAllGroups()
            withContext(Dispatchers.Main) {
                appGroupList.clear()
                appGroupList.addAll(groups)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showGroupDialog(group: AppGroupEntity?) {
        val dialogBinding = DialogAddGroupBinding.inflate(layoutInflater)
        pendingDialogBinding = dialogBinding
        pendingSelectedPackages.clear()
        
        val activePeriods = ArrayList<Pair<String,String>>() // start, end

        // Lock Logic
        var isLocked = false
        if (group != null) {
            // Edit Mode - Populate
            dialogBinding.etGroupName.setText(group.name)
            
            val limit = group.limitInMinutes
            dialogBinding.sliderLimit.progress = limit
            dialogBinding.tvLimitValue.text = "${limit / 60}h ${limit % 60}m"
            
            dialogBinding.cbStrict.isChecked = group.isStrict
            
            if (group.packageNamesJson.isNotEmpty()) {
               val pkgs = group.packageNamesJson.split(",")
               pendingSelectedPackages.addAll(pkgs)
               dialogBinding.tvSelectedApps.text = "${pkgs.size} apps selected"
            }
            
            try {
                val arr = JSONArray(group.activePeriodsJson)
                for (i in 0 until arr.length()) {
                     val obj = arr.getJSONObject(i)
                     activePeriods.add(Pair(obj.getString("start"), obj.getString("end")))
                }
            } catch (e: Exception) {}
            
            // Check Locks - use GLOBAL strict mode
            val isMaintenance = com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
            val globalStrictData = prefsLoader.getStrictModeData()
            val isGlobalStrictEnabled = globalStrictData.isEnabled
            
            // Lock logic:
            // 1. Global strict mode must be ENABLED, AND
            // 2. This group has isStrict = true
            // When locked: cannot edit group, cannot uncheck strict checkbox
            isLocked = if (isMaintenance) {
                false  // Maintenance window allows editing
            } else {
                isGlobalStrictEnabled && (group.isStrict || globalStrictData.isGroupLimitLocked)
            }
        } else {
             dialogBinding.tvLimitValue.text = "1h 30m"
             dialogBinding.sliderLimit.progress = 90
        }
        
        updateActivePeriodText(dialogBinding, activePeriods)

        // Interactions
        dialogBinding.sliderLimit.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val h = progress / 60
                val m = progress % 60
                dialogBinding.tvLimitValue.text = "${h}h ${m}m"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialogBinding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            if (pendingSelectedPackages.isNotEmpty()) {
                intent.putStringArrayListExtra("PRE_SELECTED_APPS", ArrayList(pendingSelectedPackages))
            }
            selectAppsLauncher.launch(intent)
        }
        
        dialogBinding.btnActivePeriod.setOnClickListener {
            showTimePicker("Start Time") { h, m ->
                val start = String.format("%02d:%02d", h, m)
                showTimePicker("End Time") { eh, em ->
                     val end = String.format("%02d:%02d", eh, em)
                     activePeriods.add(Pair(start, end))
                     updateActivePeriodText(dialogBinding, activePeriods)
                }
            }
        }
        
        // Strict Mode Locking
        if (isLocked) {
             dialogBinding.etGroupName.isEnabled = false
             dialogBinding.sliderLimit.isEnabled = false
             dialogBinding.cbStrict.isEnabled = false
             dialogBinding.cbStrict.isClickable = false
             dialogBinding.btnSelectApps.isEnabled = false
             dialogBinding.btnActivePeriod.isEnabled = false
             dialogBinding.etGroupName.setError("Locked by Strict Mode")
             
             // CRITICAL: Prevent checkbox state change when locked
             dialogBinding.cbStrict.setOnCheckedChangeListener { _, _ ->
                 dialogBinding.cbStrict.isChecked = true // Always keep checked
             }
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (group == null) "Create Group" else "Edit Group")
            .setView(dialogBinding.root)
            .setPositiveButton(if (isLocked) "Locked" else "Save") { _, _ ->
                if (System.currentTimeMillis() - lastSaveTimestamp < 1000) return@setPositiveButton
                lastSaveTimestamp = System.currentTimeMillis()

                if (isLocked) {
                    Toast.makeText(requireContext(), "Cannot edit active strict group", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val name = dialogBinding.etGroupName.text.toString()
                val limit = dialogBinding.sliderLimit.progress
                val isStrict = dialogBinding.cbStrict.isChecked
                
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Enter group name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pendingSelectedPackages.isEmpty()) {
                     Toast.makeText(requireContext(), "Select at least 1 app", Toast.LENGTH_SHORT).show()
                     return@setPositiveButton
                }
                
                val arr = JSONArray()
                activePeriods.forEach { 
                    arr.put(JSONObject().apply { put("start", it.first); put("end", it.second) })
                }
                val periodsJson = arr.toString()

                val newGroup = AppGroupEntity(
                    id = group?.id ?: 0,
                    name = name,
                    limitInMinutes = limit,
                    packageNamesJson = pendingSelectedPackages.joinToString(","),
                    isStrict = isStrict,
                    activePeriodsJson = periodsJson
                )

                scope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.appGroupDao().insert(newGroup)
                    
                    requireContext().sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                    loadGroups()
                }
            }
            .setNegativeButton("Cancel", null)
            
         if (group != null && !isLocked) {
             builder.setNeutralButton("Delete") { _, _ ->
                 deleteGroup(group)
             }
         } else if (group != null && isLocked) {
              // Can't delete
         }
            
         builder.show()
    }
    
    private fun updateActivePeriodText(binding: DialogAddGroupBinding, periods: List<Pair<String,String>>) {
        if (periods.isEmpty()) {
            binding.tvActivePeriodResult.text = "Always Active"
        } else {
            binding.tvActivePeriodResult.text = periods.joinToString("\n") { "${it.first} - ${it.second}" }
        }
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

    private fun deleteGroup(group: AppGroupEntity) {
        scope.launch(Dispatchers.IO) {
             val db = AppDatabase.getDatabase(requireContext())
             db.appGroupDao().delete(group)
             requireContext().sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
             loadGroups()
        }
    }

    private fun parsePackages(json: String): List<String> {
        if (json.isEmpty()) return emptyList()
        try {
            if (json.trim().startsWith("[")) {
                val list = mutableListOf<String>()
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                return list
            }
        } catch (e: Exception) {}
        return json.split(",")
    }

    inner class AppGroupAdapter(private val list: List<AppGroupEntity>) : RecyclerView.Adapter<AppGroupAdapter.VH>() {
        inner class VH(val binding: ItemAppGroupBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemAppGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.binding.tvGroupName.text = item.name
            
            // Period Text
            val isActive = TimeTools.isActivePeriod(item.activePeriodsJson)
            val periods = try { JSONArray(item.activePeriodsJson) } catch(e:Exception){ JSONArray() }
            
            if (periods.length() > 0) {
                val sb = StringBuilder()
                if (isActive) sb.append("Active Now • ") else sb.append("Inactive • ")
                sb.append("${periods.length()} Periods")
                holder.binding.tvTimeRange.text = sb.toString()
                holder.binding.tvTimeRange.visibility = View.VISIBLE
            } else {
                 holder.binding.tvTimeRange.text = "Always Active"
                 holder.binding.tvTimeRange.visibility = View.VISIBLE
            }
            
            if (item.isStrict) {
                // Should show Lock Icon or text?
                // Assuming layout has space or I can append to name
                // holder.binding.tvGroupName.append(" 🔒")
            }

            // Calculate usage
            val packages = parsePackages(item.packageNamesJson)
            var totalUsageMs = 0L
            packages.forEach { pkg ->
                totalUsageMs += usageMap.getOrDefault(pkg, 0L)
            }
            
            val limitMs = item.limitInMinutes * 60 * 1000L
            val usedPercent = if (limitMs > 0) (totalUsageMs.toFloat() / limitMs.toFloat() * 100).toInt() else 0
            
            holder.binding.progressBar.progress = usedPercent.coerceIn(0, 100)
            
            val usedMins = TimeUnit.MILLISECONDS.toMinutes(totalUsageMs)
            holder.binding.tvUsageStats.text = "${usedMins}m / ${item.limitInMinutes}m"
            
            // Icons - Using AppIconCache for efficient loading
            holder.binding.iconContainer.removeAllViews()
            val currentPosition = position
            scope.launch(Dispatchers.IO) {
                val icons = packages.take(5).mapNotNull { pkg ->
                    com.neubofy.reality.utils.AppIconCache.get(holder.itemView.context, pkg)
                }
                withContext(Dispatchers.Main) {
                    // Check if ViewHolder is still showing the same item
                    if (holder.adapterPosition == currentPosition) {
                        icons.forEach { icon ->
                            val img = ImageView(holder.itemView.context)
                            val size = (24 * resources.displayMetrics.density).toInt()
                            val margin = (4 * resources.displayMetrics.density).toInt()
                            img.layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = margin
                            }
                            img.setImageDrawable(icon)
                            holder.binding.iconContainer.addView(img)
                        }
                    }
                }
            }
            
            holder.itemView.setOnClickListener {
                 showGroupDialog(item)
            }
        }

        override fun getItemCount() = list.size
    }
}
