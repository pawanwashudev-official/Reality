package com.neubofy.reality.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.neubofy.reality.databinding.ActivityAppGroupsBinding
import com.neubofy.reality.ui.fragments.AllAppsFragment
import com.neubofy.reality.ui.fragments.AppLimitsFragment
import com.neubofy.reality.ui.fragments.GroupsFragment

class AppGroupsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppGroupsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }

        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Groups"
                1 -> "App Limits"
                2 -> "Statistics"
                else -> ""
            }
        }.attach()
        
        // Select tab based on intent extra
        val tabIndex = intent.getIntExtra("TAB_INDEX", 0)
        binding.viewPager.setCurrentItem(tabIndex, false)
    }

    inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> GroupsFragment()
                1 -> AppLimitsFragment()
                2 -> AllAppsFragment()
                else -> GroupsFragment()
            }
        }
    }
}
