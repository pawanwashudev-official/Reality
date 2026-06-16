package com.neubofy.reality.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.neubofy.reality.databinding.ActivityUnifiedBlocklistBinding
import com.neubofy.reality.ui.fragments.BlocklistAppsFragment
import com.neubofy.reality.ui.fragments.BlocklistWebsitesFragment

class UnifiedBlocklistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUnifiedBlocklistBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedBlocklistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Blocklist"
        toolbar.setNavigationOnClickListener { finish() }
        
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Apps"
                1 -> "Websites"
                else -> null
            }
        }.attach()
    }
    
    inner class ViewPagerAdapter(activity: androidx.fragment.app.FragmentActivity) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when(position) {
                0 -> com.neubofy.reality.ui.fragments.BlocklistAppsFragment()
                1 -> com.neubofy.reality.ui.fragments.BlocklistWebsitesFragment()
                else -> throw IllegalStateException("Invalid position")
            }
        }
    }
}
