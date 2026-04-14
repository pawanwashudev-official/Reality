package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityAppearanceBinding
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.ui.viewmodel.AppearanceViewModel
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppearanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAppearanceBinding
    private lateinit var viewModel: AppearanceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AppearanceViewModel(this@AppearanceActivity) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[AppearanceViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.cardPresetMinimalist.setOnClickListener { selectPreset(ThemeManager.ThemePreset.MINIMALIST) }
        binding.cardPresetGlass.setOnClickListener { selectPreset(ThemeManager.ThemePreset.GLASSMORPHISM) }
        binding.cardPresetElite.setOnClickListener { selectPreset(ThemeManager.ThemePreset.ELITE) }
        binding.cardPresetCompact.setOnClickListener { selectPreset(ThemeManager.ThemePreset.COMPACT) }
        binding.cardPresetVibrant.setOnClickListener { selectPreset(ThemeManager.ThemePreset.VIBRANT) }

        binding.chipGroupMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val mode = when (checkedIds.first()) {
                R.id.mode_light -> ThemeManager.DarkModeOption.LIGHT
                R.id.mode_dark -> ThemeManager.DarkModeOption.DARK
                else -> ThemeManager.DarkModeOption.SYSTEM
            }
            viewModel.setDarkMode(mode)
        }

        binding.switchAmoled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAmoledMode(isChecked)
        }

        binding.btnSaveAppearance.setOnClickListener {
            // Apply and restart
            Toast.makeText(this, "Appearance saved! Restarting...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
        
        binding.btnResetAppearance.setOnClickListener {
            viewModel.resetToDefaults()
            Toast.makeText(this, "Reset to defaults.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun selectPreset(preset: ThemeManager.ThemePreset) {
        viewModel.setThemePreset(preset)
        Toast.makeText(this, "${preset.displayName} selected", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.themeState.collectLatest { state ->
                // Update UI to reflect selected preset
                updatePresetHighlight(state.themePreset)
                
                // Update dark mode selection
                when (state.darkModeOption) {
                    ThemeManager.DarkModeOption.LIGHT -> binding.chipGroupMode.check(R.id.mode_light)
                    ThemeManager.DarkModeOption.DARK -> binding.chipGroupMode.check(R.id.mode_dark)
                    else -> binding.chipGroupMode.check(R.id.mode_system)
                }

                binding.switchAmoled.isChecked = state.isAmoled
            }
        }
    }

    private fun updatePresetHighlight(preset: ThemeManager.ThemePreset) {
        val defaultStroke = (1 * resources.displayMetrics.density).toInt()
        val selectedStroke = (3 * resources.displayMetrics.density).toInt()
        val defaultColor = ThemeManager.getColorWithFallback(this, null, com.google.android.material.R.attr.colorOutlineVariant, 0x44FFFFFF.toInt())
        val selectedColor = ThemeManager.getColorWithFallback(this, null, com.google.android.material.R.attr.colorPrimary, 0xFF00BCD4.toInt())

        val cards = listOf(
            binding.cardPresetMinimalist to ThemeManager.ThemePreset.MINIMALIST,
            binding.cardPresetGlass to ThemeManager.ThemePreset.GLASSMORPHISM,
            binding.cardPresetElite to ThemeManager.ThemePreset.ELITE,
            binding.cardPresetCompact to ThemeManager.ThemePreset.COMPACT,
            binding.cardPresetVibrant to ThemeManager.ThemePreset.VIBRANT
        )

        cards.forEach { (card, p) ->
            if (p == preset) {
                card.strokeWidth = selectedStroke
                card.strokeColor = selectedColor
            } else {
                card.strokeWidth = defaultStroke
                card.strokeColor = defaultColor
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
