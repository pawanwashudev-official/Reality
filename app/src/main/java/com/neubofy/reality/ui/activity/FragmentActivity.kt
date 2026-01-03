package com.neubofy.reality.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.neubofy.reality.R
import com.neubofy.reality.ui.fragments.anti_uninstall.ChooseModeFragment
import com.neubofy.reality.ui.fragments.installation.AccessibilityGuide
import com.neubofy.reality.ui.fragments.installation.PermissionsFragment
import com.neubofy.reality.ui.fragments.installation.WelcomeFragment

class FragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        var fragment: Fragment? = null
        val fragmentId = intent.getStringExtra("fragment")
        if (fragmentId != null) {
            when (fragmentId) {
                ChooseModeFragment.FRAGMENT_ID -> {
                    fragment = ChooseModeFragment()
                }
                WelcomeFragment.FRAGMENT_ID -> {
                    fragment = WelcomeFragment()
                }
                PermissionsFragment.FRAGMENT_ID -> {
                    fragment = PermissionsFragment()
                }
                AccessibilityGuide.FRAGMENT_ID -> {
                    fragment = AccessibilityGuide()
                }
            }
            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .commit()
            }
        }
    }
}