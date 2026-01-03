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
        binding.websiteList.adapter = WebsiteAdapter(blockedList)
        
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
    
    inner class WebsiteAdapter(private val list: MutableList<String>) : RecyclerView.Adapter<WebsiteAdapter.VH>() {
        inner class VH(val binding: ItemWebsiteBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemWebsiteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val domain = list[position]
            holder.binding.tvDomain.text = domain
            holder.binding.btnDelete.setOnClickListener {
                if (!StrictLockUtils.isModificationAllowedFor(requireContext(), StrictLockUtils.FeatureType.BLOCKLIST)) {
                    Toast.makeText(requireContext(), "Locked by Strict Mode", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                list.removeAt(holder.layoutPosition)
                notifyItemRemoved(holder.layoutPosition)
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
