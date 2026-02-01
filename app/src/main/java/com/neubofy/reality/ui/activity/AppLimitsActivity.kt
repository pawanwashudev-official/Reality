package com.neubofy.reality.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.databinding.ActivitySingleFragmentBinding
import com.neubofy.reality.ui.fragments.AppLimitsFragment

class AppLimitsActivity : BaseActivity() {

    private lateinit var binding: ActivitySingleFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySingleFragmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.title = "App Limits"
        binding.topAppBar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, AppLimitsFragment())
                .commit()
        }
    }
}
