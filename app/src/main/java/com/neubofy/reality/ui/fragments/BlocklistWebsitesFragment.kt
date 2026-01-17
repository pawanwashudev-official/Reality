package com.neubofy.reality.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.databinding.FragmentBlocklistWebsitesBinding
import com.neubofy.reality.databinding.ItemWebsiteBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils

class BlocklistWebsitesFragment : Fragment() {

    private var _binding: FragmentBlocklistWebsitesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SavedPreferencesLoader
    private var blockedList = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlocklistWebsitesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = SavedPreferencesLoader(requireContext())
        loadData()
        
        binding.websiteList.layoutManager = LinearLayoutManager(requireContext())
        binding.websiteList.adapter = WebsiteAdapter(blockedList) {
            // On Config Changed -> Refresh
            val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            intent.setPackage(requireContext().packageName)
            requireContext().sendBroadcast(intent)
        }
        
        updateEmptyState()
        
        binding.btnAddWebsite.setOnClickListener {
            showAddDialog()
        }
    }
    
    private fun loadData() {
        val data = prefs.getFocusModeData()
        blockedList = ArrayList(data.blockedWebsites)
    }
    
    private fun saveData() {
        val data = prefs.getFocusModeData()
        data.blockedWebsites = HashSet(blockedList)
        prefs.saveFocusModeData(data)
        
        // Refresh Service
        val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        intent.setPackage(requireContext().packageName)
        requireContext().sendBroadcast(intent)
    }
    
    private fun showAddDialog() {
        val input = EditText(requireContext())
        input.hint = "example.com"
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Block Website")
            .setMessage("Enter the domain of the website to block.")
            .setView(input)
            .setPositiveButton("Block") { _, _ ->
                val domain = input.text.toString().trim().lowercase()
                if (domain.isNotEmpty() && domain.contains(".")) {
                   if (!blockedList.contains(domain)) {
                       blockedList.add(domain)
                       binding.websiteList.adapter?.notifyDataSetChanged()
                       saveData()
                       updateEmptyState()
                   } else {
                       Toast.makeText(requireContext(), "Already blocked", Toast.LENGTH_SHORT).show()
                   }
                } else {
                    Toast.makeText(requireContext(), "Invalid domain", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateEmptyState() {
        if (blockedList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.websiteList.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.websiteList.visibility = View.VISIBLE
        }
    }
    
    inner class WebsiteAdapter(
        private val list: MutableList<String>,
        private val onConfigChanged: () -> Unit
    ) : RecyclerView.Adapter<WebsiteAdapter.VH>() {
        
        private val expandedPositions = mutableSetOf<Int>()
        
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDomain: android.widget.TextView = itemView.findViewById(com.neubofy.reality.R.id.tv_domain)
            val btnDelete: android.widget.ImageView = itemView.findViewById(com.neubofy.reality.R.id.btn_delete)
            val btnExpand: android.widget.ImageView = itemView.findViewById(com.neubofy.reality.R.id.btnExpand)
            val modeSelectionLayout: View = itemView.findViewById(com.neubofy.reality.R.id.modeSelectionLayout)
            val cbFocus: android.widget.CheckBox = itemView.findViewById(com.neubofy.reality.R.id.cbFocus)
            val cbAutoFocus: android.widget.CheckBox = itemView.findViewById(com.neubofy.reality.R.id.cbAutoFocus)
            val cbBedtime: android.widget.CheckBox = itemView.findViewById(com.neubofy.reality.R.id.cbBedtime)
            val cbCalendar: android.widget.CheckBox = itemView.findViewById(com.neubofy.reality.R.id.cbCalendar)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(com.neubofy.reality.R.layout.item_website_expandable, parent, false)
            return VH(view)
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val domain = list[position]
            val isExpanded = expandedPositions.contains(position)
            
            holder.tvDomain.text = domain
            holder.modeSelectionLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.btnExpand.rotation = if (isExpanded) 180f else 0f
            
            // Load per-website mode config (using domain as package name key)
            val config = prefs.getBlockedAppConfig(domain)
            holder.cbFocus.setOnCheckedChangeListener(null)
            holder.cbAutoFocus.setOnCheckedChangeListener(null)
            holder.cbBedtime.setOnCheckedChangeListener(null)
            holder.cbCalendar.setOnCheckedChangeListener(null)
            
            holder.cbFocus.isChecked = config.blockInFocus
            holder.cbAutoFocus.isChecked = config.blockInAutoFocus
            holder.cbBedtime.isChecked = config.blockInBedtime
            holder.cbCalendar.isChecked = config.blockInCalendar
            
            // Mode checkbox listeners
            val modeChangeListener = { _: android.widget.CompoundButton, _: Boolean ->
                val updatedConfig = com.neubofy.reality.Constants.BlockedAppConfig(
                    packageName = domain,
                    blockInFocus = holder.cbFocus.isChecked,
                    blockInAutoFocus = holder.cbAutoFocus.isChecked,
                    blockInBedtime = holder.cbBedtime.isChecked,
                    blockInCalendar = holder.cbCalendar.isChecked
                )
                prefs.updateBlockedAppConfig(updatedConfig)
                onConfigChanged()
            }
            holder.cbFocus.setOnCheckedChangeListener(modeChangeListener)
            holder.cbAutoFocus.setOnCheckedChangeListener(modeChangeListener)
            holder.cbBedtime.setOnCheckedChangeListener(modeChangeListener)
            holder.cbCalendar.setOnCheckedChangeListener(modeChangeListener)
            
            // Expand button
            holder.btnExpand.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }
            
            // Delete button
            holder.btnDelete.setOnClickListener {
                if (!StrictLockUtils.isModificationAllowedFor(requireContext(), StrictLockUtils.FeatureType.BLOCKLIST)) {
                    Toast.makeText(requireContext(), "Locked by Strict Mode", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val pos = holder.layoutPosition
                list.removeAt(pos)
                expandedPositions.remove(pos)
                notifyItemRemoved(pos)
                saveData()
                updateEmptyState()
            }
        }
        
        override fun getItemCount() = list.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
