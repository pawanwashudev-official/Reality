package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.chip.Chip
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityAppearanceBinding
import com.neubofy.reality.utils.ThemeManager

class AppearanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppearanceBinding
    private var selectedAccent = ThemeManager.AccentColor.TEAL

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        binding = ActivityAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        
        loadCurrentState()
        setupListeners()
        updatePreview()
    }
    
    private fun loadCurrentState() {
        // Theme Mode
        when (ThemeManager.getDarkMode(this)) {
            ThemeManager.DarkModeOption.SYSTEM -> binding.modeSystem.isChecked = true
            ThemeManager.DarkModeOption.LIGHT -> binding.modeLight.isChecked = true
            ThemeManager.DarkModeOption.DARK -> binding.modeDark.isChecked = true
        }
        binding.switchAmoled.isChecked = ThemeManager.isAmoledMode(this)
        updateAmoledVisibility()
        
        // Accent Color
        selectedAccent = ThemeManager.getAccentColor(this)
        
        // Colors
        val popupBg = ThemeManager.getPopupBackgroundColor(this)
        val primaryText = ThemeManager.getPrimaryTextColor(this)
        val secondaryText = ThemeManager.getSecondaryTextColor(this)
        
        if (popupBg != null) binding.inputPopupBg.setText(String.format("#%06X", (0xFFFFFF and popupBg)))
        if (primaryText != null) binding.inputPrimaryText.setText(String.format("#%06X", (0xFFFFFF and primaryText)))
        if (secondaryText != null) binding.inputSecondaryText.setText(String.format("#%06X", (0xFFFFFF and secondaryText)))
        
        // Animation
        binding.sliderAnimation.value = ThemeManager.getAnimationSpeed(this)
        
        // Background Pattern
        when (ThemeManager.getBackgroundPattern(this)) {
            ThemeManager.BackgroundPattern.ZEN -> binding.chipPatternZen.isChecked = true
            ThemeManager.BackgroundPattern.NONE -> binding.chipPatternNone.isChecked = true
            ThemeManager.BackgroundPattern.GRADIENT -> binding.chipPatternGradient.isChecked = true
        }
        
        // Glass Intensity
        when (ThemeManager.getGlassIntensity(this)) {
            ThemeManager.GlassIntensity.SUBTLE -> binding.chipGlassSubtle.isChecked = true
            ThemeManager.GlassIntensity.LIGHT -> binding.chipGlassLight.isChecked = true
            ThemeManager.GlassIntensity.MEDIUM -> binding.chipGlassMedium.isChecked = true
            ThemeManager.GlassIntensity.STRONG -> binding.chipGlassStrong.isChecked = true
        }
    }
    
    private fun setupListeners() {
        // Mode Chips
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, _ ->
            updateAmoledVisibility()
        }

        // Accent Colors
        val colorMap = mapOf(
            R.id.color_teal to ThemeManager.AccentColor.TEAL,
            R.id.color_purple to ThemeManager.AccentColor.PURPLE,
            R.id.color_blue to ThemeManager.AccentColor.BLUE,
            R.id.color_pink to ThemeManager.AccentColor.PINK,
            R.id.color_orange to ThemeManager.AccentColor.ORANGE,
            R.id.color_cyan to ThemeManager.AccentColor.CYAN,
            R.id.color_green to ThemeManager.AccentColor.GREEN,
            R.id.color_red to ThemeManager.AccentColor.RED
        )
        
        colorMap.forEach { (viewId, accent) ->
            findViewById<ImageView>(viewId).setOnClickListener {
                selectedAccent = accent
                updatePreview()
            }
        }
        
        // Color Suggestions Listeners
        setupSuggestionChips(binding.chipsPopupBg, binding.inputPopupBg)
        setupSuggestionChips(binding.chipsPrimaryText, binding.inputPrimaryText)
        setupSuggestionChips(binding.chipsSecondaryText, binding.inputSecondaryText)
        
        // Text Watchers for Live Preview
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }
        
        binding.inputPopupBg.addTextChangedListener(textWatcher)
        binding.inputPrimaryText.addTextChangedListener(textWatcher)
        binding.inputSecondaryText.addTextChangedListener(textWatcher)
        
        // Save Button
        binding.btnSaveAppearance.setOnClickListener {
            saveAndRestart()
        }
    }
    
    private fun setupSuggestionChips(chipGroup: com.google.android.material.chip.ChipGroup, input: android.widget.EditText) {
        val count = chipGroup.childCount
        for (i in 0 until count) {
            val view = chipGroup.getChildAt(i)
            if (view is Chip) {
                view.setOnClickListener {
                    input.setText(view.text) // Text is the hex code
                }
            }
        }
    }
    
    private fun updatePreview() {
        // Apply Accent
        binding.previewTitle.setTextColor(selectedAccent.primaryColor)
        binding.previewButton.backgroundTintList = android.content.res.ColorStateList.valueOf(selectedAccent.primaryColor)
        
        // Apply Custom Colors
        try {
            val bgText = binding.inputPopupBg.text.toString()
            if (bgText.isNotEmpty()) binding.cardPreview.setCardBackgroundColor(Color.parseColor(bgText))
            
            val primText = binding.inputPrimaryText.text.toString()
            if (primText.isNotEmpty()) binding.previewTitle.setTextColor(Color.parseColor(primText))
            
            val secText = binding.inputSecondaryText.text.toString()
            if (secText.isNotEmpty()) binding.previewBody.setTextColor(Color.parseColor(secText))
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    private fun saveAndRestart() {
        // Save Mode
        val mode = when {
            binding.modeLight.isChecked -> ThemeManager.DarkModeOption.LIGHT
            binding.modeDark.isChecked -> ThemeManager.DarkModeOption.DARK
            else -> ThemeManager.DarkModeOption.SYSTEM
        }
        ThemeManager.setDarkMode(this, mode)
        ThemeManager.setAmoledMode(this, binding.switchAmoled.isChecked && binding.switchAmoled.isEnabled)
        
        // Accent
        ThemeManager.setAccentColor(this, selectedAccent)
        
        // Animation
        ThemeManager.setAnimationSpeed(this, binding.sliderAnimation.value)
        
        // Save Hex Colors
        val bgText = binding.inputPopupBg.text.toString()
        if (isValidHex(bgText)) ThemeManager.setPopupBackgroundColor(this, bgText) else ThemeManager.setPopupBackgroundColor(this, null)
        
        val primText = binding.inputPrimaryText.text.toString()
        if (isValidHex(primText)) ThemeManager.setPrimaryTextColor(this, primText) else ThemeManager.setPrimaryTextColor(this, null)

        val secText = binding.inputSecondaryText.text.toString()
        if (isValidHex(secText)) ThemeManager.setSecondaryTextColor(this, secText) else ThemeManager.setSecondaryTextColor(this, null)
        
        // Pattern & Glass
        val pattern = when {
            binding.chipPatternNone.isChecked -> ThemeManager.BackgroundPattern.NONE
            binding.chipPatternGradient.isChecked -> ThemeManager.BackgroundPattern.GRADIENT
            else -> ThemeManager.BackgroundPattern.ZEN
        }
        ThemeManager.setBackgroundPattern(this, pattern)
        
        val glass = when {
            binding.chipGlassSubtle.isChecked -> ThemeManager.GlassIntensity.SUBTLE
            binding.chipGlassMedium.isChecked -> ThemeManager.GlassIntensity.MEDIUM
            binding.chipGlassStrong.isChecked -> ThemeManager.GlassIntensity.STRONG
            else -> ThemeManager.GlassIntensity.LIGHT
        }
        ThemeManager.setGlassIntensity(this, glass)
        
        // Restart App to apply changes
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
    
    private fun isValidHex(color: String): Boolean {
        return try {
            Color.parseColor(color)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateAmoledVisibility() {
        val isDark = binding.modeDark.isChecked || (binding.modeSystem.isChecked && isSystemDark())
        binding.switchAmoled.isEnabled = isDark
        if (!isDark) binding.switchAmoled.isChecked = false
    }
    
    private fun isSystemDark(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
