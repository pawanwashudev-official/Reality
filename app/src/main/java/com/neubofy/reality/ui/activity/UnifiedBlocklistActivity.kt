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
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        val adapter = BlocklistPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Apps"
                1 -> "Websites"
                else -> "Apps"
            }
        }.attach()
    }
    
    inner class BlocklistPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when(position) {
                0 -> BlocklistAppsFragment()
                1 -> BlocklistWebsitesFragment()
                else -> BlocklistAppsFragment()
            }
        }
    }
}
