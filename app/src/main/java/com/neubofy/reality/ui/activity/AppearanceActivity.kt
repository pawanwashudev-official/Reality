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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityAppearanceBinding
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.ui.dialogs.ColorPickerDialog
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.ui.viewmodel.AppearanceViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppearanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAppearanceBinding
    private lateinit var viewModel: AppearanceViewModel
    
    private var selectedAccent = ThemeManager.AccentColor.TEAL
    private var selectedPattern = ThemeManager.BackgroundPattern.ZEN
    
    // Track which mode colors we are currently editing ("light" or "dark")
    private var editingMode = "light"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            
            binding = ActivityAppearanceBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(true)

            // Setup ViewModel with context
            val factory = object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AppearanceViewModel(this@AppearanceActivity) as T
                }
            }
            viewModel = ViewModelProvider(this, factory)[AppearanceViewModel::class.java]

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, (systemBars.bottom + 16 * resources.displayMetrics.density).toInt())
                insets
            }
            
            setupTabNavigation()
            loadCurrentState()
            setupListeners()
            observeViewModel()
            setupColorPickerIntegration()
        } catch (e: Throwable) {
            android.util.Log.e("AppearanceActivity", "CRITICAL ERROR during onCreate", e)
            val errorMsg = "Critical Error: ${e.javaClass.simpleName} - ${e.message}"
            android.widget.Toast.makeText(this, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTabNavigation() {
        binding.tabMainCategories.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                binding.sectionPalette.visibility = android.view.View.GONE
                binding.sectionVisuals.visibility = android.view.View.GONE
                binding.sectionPrecision.visibility = android.view.View.GONE
                binding.sectionTypography.visibility = android.view.View.GONE
                binding.sectionElite.visibility = android.view.View.GONE

                when (tab?.position) {
                    0 -> binding.sectionPalette.visibility = android.view.View.VISIBLE
                    1 -> binding.sectionVisuals.visibility = android.view.View.VISIBLE
                    2 -> binding.sectionPrecision.visibility = android.view.View.VISIBLE
                    3 -> binding.sectionTypography.visibility = android.view.View.VISIBLE
                    4 -> binding.sectionElite.visibility = android.view.View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.themeState.collectLatest { state ->
                updatePreview()
            }
        }
    }


    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menu?.add(0, 1001, 0, "Reset to Default")
            ?.setIcon(R.drawable.baseline_refresh_24)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) {
             showResetConfirmation()
             return true
        }
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showResetConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Reset Appearance?")
            .setMessage("This will reset all theme customization to default/system settings. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                performReset()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performReset() {
        ThemeManager.resetToDefaults(this)
        android.widget.Toast.makeText(this, "Appearance reset to default", android.widget.Toast.LENGTH_SHORT).show()
        // Restart activity to reflect changes
        val intent = intent
        finish()
        startActivity(intent)
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
        val appBg = ThemeManager.getAppBackgroundColor(this)
        val primaryText = ThemeManager.getPrimaryTextColor(this)
        val secondaryText = ThemeManager.getSecondaryTextColor(this)
        
        if (popupBg != null) binding.inputPopupBg.setText(String.format("#%06X", (0xFFFFFF and popupBg)))
        if (appBg != null) binding.inputAppBg.setText(String.format("#%06X", (0xFFFFFF and appBg)))
        if (primaryText != null) binding.inputPrimaryText.setText(String.format("#%06X", (0xFFFFFF and primaryText)))
        if (secondaryText != null) binding.inputSecondaryText.setText(String.format("#%06X", (0xFFFFFF and secondaryText)))
        
        // Header Style
        when (ThemeManager.getHeaderStyle(this)) {
            ThemeManager.HeaderStyle.TRANSPARENT -> binding.chipHeaderTransparent.isChecked = true
            ThemeManager.HeaderStyle.SOLID -> binding.chipHeaderSolid.isChecked = true
            ThemeManager.HeaderStyle.BLENDED -> binding.chipHeaderBlended.isChecked = true
        }

        // Animation
        binding.sliderAnimationV2.value = ThemeManager.getAnimationSpeed(this)
        
        // Background Pattern
        selectedPattern = ThemeManager.getBackgroundPattern(this)
        updatePatternSelectionUI()
        
        // Glass Intensity
        when (ThemeManager.getGlassIntensity(this)) {
            ThemeManager.GlassIntensity.SUBTLE -> binding.chipGlassSubtleV2.isChecked = true
            ThemeManager.GlassIntensity.LIGHT -> binding.chipGlassLightV2.isChecked = true
            ThemeManager.GlassIntensity.MEDIUM -> binding.chipGlassMediumV2.isChecked = true
            ThemeManager.GlassIntensity.STRONG -> binding.chipGlassStrongV2.isChecked = true
        }
        
        // Card Style
        when (ThemeManager.getCardStyle(this)) {
            ThemeManager.CardStyle.GLASS -> binding.styleGlassV2.isChecked = true
            ThemeManager.CardStyle.FILLED -> binding.styleFilledV2.isChecked = true
            ThemeManager.CardStyle.OUTLINED -> binding.styleOutlinedV2.isChecked = true
        }
        
        // Highlight active accent
        updateAccentSelectionUI()
        
        // Corner Radius
        binding.sliderRadiusV2.value = ThemeManager.getCornerRadius(this).toFloat()
        
        // ========== EXTRA CUSTOMIZATION OPTIONS ==========
        try {
            binding.sliderFontSize.value = ThemeManager.getFontSizeScale(this)
            binding.sliderSpacing.value = ThemeManager.getSpacingScale(this)
            
            // Icon Style
            when (ThemeManager.getIconStyle(this)) {
                ThemeManager.IconStyle.FILLED -> binding.iconFilled.isChecked = true
                ThemeManager.IconStyle.OUTLINED -> binding.iconOutlined.isChecked = true
                ThemeManager.IconStyle.ROUNDED -> binding.iconRounded.isChecked = true
            }
            
            // Button Style
            when (ThemeManager.getButtonStyle(this)) {
                ThemeManager.ButtonStyle.FILLED -> binding.btnFilled.isChecked = true
                ThemeManager.ButtonStyle.OUTLINED -> binding.btnOutlined.isChecked = true
                ThemeManager.ButtonStyle.TEXT -> binding.btnText.isChecked = true
            }
            
            // Compact Mode
            binding.switchCompactMode.isChecked = ThemeManager.isCompactMode(this)
            
            // ========== MODE-SPECIFIC COLOR LOADING ==========
            // Set default tab to current mode instead of hardcoded Light
            val currentTab = if (ThemeManager.isDark(this)) 1 else 0
            binding.tabModeEditor.getTabAt(currentTab)?.select()
            loadModeSpecificColors(if (currentTab == 0) "light" else "dark")
            editingMode = if (currentTab == 0) "light" else "dark"
            binding.textEditingMode.text = "Editing: ${if (editingMode == "light") "Light" else "Dark"} Mode Colors"
            
            // Tab listener for switching between Light/Dark mode editing
            binding.tabModeEditor.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                    // Save current mode's colors before switching
                    saveModeSpecificColors(editingMode)
                    
                    // Switch to new mode
                    editingMode = if (tab?.position == 0) "light" else "dark"
                    binding.textEditingMode.text = "Editing: ${if (editingMode == "light") "Light" else "Dark"} Mode Colors"
                    loadModeSpecificColors(editingMode)
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            })
            
            // ========== ELITE AESTHETICS LOADING ==========
            binding.sliderNoise.value = ThemeManager.getNoiseIntensity(this)
            
            when (ThemeManager.getHapticLevel(this)) {
                ThemeManager.HapticLevel.OFF -> binding.hapticOff.isChecked = true
                ThemeManager.HapticLevel.SOFT -> binding.hapticSoft.isChecked = true
                ThemeManager.HapticLevel.MEDIUM -> binding.hapticMedium.isChecked = true
                ThemeManager.HapticLevel.SHARP -> binding.hapticSharp.isChecked = true
                ThemeManager.HapticLevel.HEAVY -> binding.hapticHeavy.isChecked = true
            }
            
            when (ThemeManager.getMotionPreset(this)) {
                ThemeManager.MotionPreset.STIFF -> binding.motionStiff.isChecked = true
                ThemeManager.MotionPreset.FLUID -> binding.motionFluid.isChecked = true
                ThemeManager.MotionPreset.BOUNCY -> binding.motionBouncy.isChecked = true
                ThemeManager.MotionPreset.FAST -> binding.motionFast.isChecked = true
            }
            
            binding.switchShimmer.isChecked = ThemeManager.isShimmerEnabled(this)
            
        } catch (e: Exception) {
            android.util.Log.w("AppearanceActivity", "Some new UI elements not found: ${e.message}")
        }
        
        // Live Preview Listeners for new controls
        binding.sliderRadiusV2.addOnChangeListener { _, _, _ -> updatePreview() }
        binding.chipGroupCardStyleVisualsV2.setOnCheckedStateChangeListener { _, _ -> updatePreview() }
    }
    
    /**
     * Load mode-specific colors from preferences into the UI fields.
     */
    private fun loadModeSpecificColors(mode: String) {
        try {
            val prefs = getSharedPreferences("reality_theme_prefs", Context.MODE_PRIVATE)
            
            // Helper to load hex value
            fun loadHex(key: String): String? = prefs.getString(mode + key, null)
            
            // Page Background
            binding.inputModePageBg.setText(loadHex("_page_bg") ?: "")
            
            // Card Colors
            binding.inputModeCardBg.setText(loadHex("_card_bg") ?: "")
            binding.inputModeCardStroke.setText(loadHex("_card_stroke") ?: "")
            
            // Chat Bubble Colors
            binding.inputModeAiBubble.setText(loadHex("_ai_bubble_bg") ?: "")
            binding.inputModeUserBubble.setText(loadHex("_user_bubble_bg") ?: "")
            
            // Status Colors
            binding.inputModeSuccess.setText(loadHex("_success") ?: "")
            binding.inputModeWarning.setText(loadHex("_warning") ?: "")
            binding.inputModeError.setText(loadHex("_error") ?: "")
        } catch (e: Exception) {
            android.util.Log.w("AppearanceActivity", "loadModeSpecificColors error: ${e.message}")
        }
    }
    
    /**
     * Save mode-specific colors from the UI fields to preferences.
     */
    private fun saveModeSpecificColors(mode: String) {
        try {
            val prefs = getSharedPreferences("reality_theme_prefs", Context.MODE_PRIVATE).edit()
            
            // Helper to save hex value
            fun saveHex(key: String, value: String) {
                val fullKey = mode + key
                if (isValidHex(value)) {
                    prefs.putString(fullKey, value)
                } else {
                    prefs.remove(fullKey)
                }
            }
            
            // Page Background
            saveHex("_page_bg", binding.inputModePageBg.text.toString())
            
            // Card Colors
            saveHex("_card_bg", binding.inputModeCardBg.text.toString())
            saveHex("_card_stroke", binding.inputModeCardStroke.text.toString())
            
            // Chat Bubble Colors
            saveHex("_ai_bubble_bg", binding.inputModeAiBubble.text.toString())
            saveHex("_user_bubble_bg", binding.inputModeUserBubble.text.toString())
            
            // Status Colors
            saveHex("_success", binding.inputModeSuccess.text.toString())
            saveHex("_warning", binding.inputModeWarning.text.toString())
            saveHex("_error", binding.inputModeError.text.toString())
            
            prefs.apply()
        } catch (e: Exception) {
            android.util.Log.w("AppearanceActivity", "saveModeSpecificColors error: ${e.message}")
        }
    }
    
    private fun setupListeners() {
        // Mode Chips
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, _ ->
            updateAmoledVisibility()
            updatePreview()
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
                updateAccentSelectionUI()
                updatePreview()
            }
        }
        
        // Color Suggestions Listeners
        try {
            setupSuggestionChips(binding.chipsAppBg, binding.inputAppBg)
        } catch (e: Exception) { /* Ignore */ }
        

        
        setupColorPickerIntegration()
        
        // Text Watchers for Live Preview
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }
        
        binding.inputPopupBg.addTextChangedListener(textWatcher)
        binding.inputAppBg.addTextChangedListener(textWatcher)
        binding.inputPrimaryText.addTextChangedListener(textWatcher)
        binding.inputSecondaryText.addTextChangedListener(textWatcher)
        
        // Mode Specific Live Preview
        try {
            binding.inputModePageBg.addTextChangedListener(textWatcher)
            binding.inputModeCardBg.addTextChangedListener(textWatcher)
            binding.inputModeCardStroke.addTextChangedListener(textWatcher)
            binding.inputModeAiBubble.addTextChangedListener(textWatcher)
            binding.inputModeUserBubble.addTextChangedListener(textWatcher)
            binding.inputModeSuccess.addTextChangedListener(textWatcher)
            binding.inputModeWarning.addTextChangedListener(textWatcher)
            binding.inputModeError.addTextChangedListener(textWatcher)
        } catch (e: Exception) { /* Ignore safely */ }
        
        // Update Start Icon Tints on change
        binding.inputPopupBg.addTextChangedListener(createIconTintWatcher(binding.inputLayoutPopupBg))
        binding.inputAppBg.addTextChangedListener(createIconTintWatcher(binding.inputLayoutAppBg))
        binding.inputPrimaryText.addTextChangedListener(createIconTintWatcher(binding.inputLayoutPrimaryText))
        binding.inputSecondaryText.addTextChangedListener(createIconTintWatcher(binding.inputLayoutSecondaryText))
        
        try {
            binding.inputModePageBg.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModePageBg))
            binding.inputModeCardBg.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeCardBg))
            binding.inputModeCardStroke.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeCardStroke))
            binding.inputModeAiBubble.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeAiBubble))
            binding.inputModeUserBubble.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeUserBubble))
            binding.inputModeSuccess.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeSuccess))
            binding.inputModeWarning.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeWarning))
            binding.inputModeError.addTextChangedListener(createIconTintWatcher(binding.inputLayoutModeError))
        } catch (e: Exception) { /* Ignore safely */ }
        
        // Save Button
        binding.btnSaveAppearance.setOnClickListener {
            saveAndRestart()
        }
        
        // Elite Listeners for Live Preview
        binding.sliderNoise.addOnChangeListener { _, _, _ -> updatePreview() }
        binding.chipGroupHaptics.setOnCheckedStateChangeListener { _, _ -> updatePreview() }
        binding.chipGroupMotion.setOnCheckedStateChangeListener { _, _ -> updatePreview() }
        binding.switchShimmer.setOnCheckedChangeListener { _, _ -> updatePreview() }

        binding.btnResetAppearance.setOnClickListener {
            showResetConfirmation()
        }
        
        // Pattern Selection Listeners
        binding.optionPatternNone.setOnClickListener { 
            selectedPattern = ThemeManager.BackgroundPattern.NONE
            updatePatternSelectionUI()
        }
        binding.optionPatternZen.setOnClickListener { 
            selectedPattern = ThemeManager.BackgroundPattern.ZEN
            updatePatternSelectionUI()
        }
        binding.optionPatternGradient.setOnClickListener { 
            selectedPattern = ThemeManager.BackgroundPattern.GRADIENT
            updatePatternSelectionUI()
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
        // ========== 1. Apply Accent ==========
        binding.previewTitle.setTextColor(selectedAccent.primaryColor)
        val accentColorStateList = android.content.res.ColorStateList.valueOf(selectedAccent.primaryColor)
        binding.previewButton.backgroundTintList = accentColorStateList
        // Also tint chips and sliders for immediate feedback
        binding.sliderRadiusV2.thumbTintList = accentColorStateList
        binding.sliderAnimationV2.thumbTintList = accentColorStateList
        binding.sliderFontSize.thumbTintList = accentColorStateList
        binding.sliderSpacing.thumbTintList = accentColorStateList
        
        // ========== 2. Apply Page Background (Immediate Feedback) ==========
        // Try Mode Specific first
        val modePageBgText = binding.inputModePageBg.text.toString()
        val appBgText = binding.inputAppBg.text.toString()
        
        try {
            if (isValidHex(modePageBgText)) {
                binding.root.setBackgroundColor(Color.parseColor(modePageBgText))
                binding.toolbar.setBackgroundColor(Color.parseColor(modePageBgText))
            } else if (isValidHex(appBgText)) {
                binding.root.setBackgroundColor(Color.parseColor(appBgText))
                binding.toolbar.setBackgroundColor(Color.parseColor(appBgText))
            } else {
                // Return to default theme background if inputs cleared
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
                binding.root.setBackgroundColor(typedValue.data)
                binding.toolbar.setBackgroundColor(typedValue.data)
            }
        } catch (e: Exception) { /* Ignore */ }

        // ========== 3. Apply Card Style & Radius ==========
        val radius = binding.sliderRadiusV2.value
        binding.cardPreview.radius = radius * resources.displayMetrics.density
        
        val style = when {
            binding.styleFilledV2.isChecked -> ThemeManager.CardStyle.FILLED
            binding.styleOutlinedV2.isChecked -> ThemeManager.CardStyle.OUTLINED
            binding.styleGlassV2.isChecked -> ThemeManager.CardStyle.GLASS
            else -> ThemeManager.CardStyle.GLASS
        }
        
        // Check for Custom Card Colors (Mode Specific)
        val modeCardBgText = binding.inputModeCardBg.text.toString()
        val modeCardStrokeText = binding.inputModeCardStroke.text.toString()
        
        var appliedCardBg = false
        if (isValidHex(modeCardBgText)) {
            binding.cardPreview.setCardBackgroundColor(Color.parseColor(modeCardBgText))
            appliedCardBg = true
        }
        
        if (!appliedCardBg) {
             if (style == ThemeManager.CardStyle.OUTLINED) {
                binding.cardPreview.setCardBackgroundColor(Color.TRANSPARENT)
                binding.cardPreview.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                binding.cardPreview.strokeColor = Color.GRAY
            } else if (style == ThemeManager.CardStyle.FILLED) {
                 val typedValue = android.util.TypedValue()
                 theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
                 binding.cardPreview.setCardBackgroundColor(typedValue.data)
                 binding.cardPreview.strokeWidth = 0
            } else {
                 // Glass
                 binding.cardPreview.setCardBackgroundColor(Color.parseColor("#1AFFFFFF")) // Approx
                 binding.cardPreview.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                 binding.cardPreview.strokeColor = Color.parseColor("#33FFFFFF")
            }
        }
        
        // Apply Stroke Overrides
        if (isValidHex(modeCardStrokeText)) {
             binding.cardPreview.strokeColor = Color.parseColor(modeCardStrokeText)
             binding.cardPreview.strokeWidth = (1 * resources.displayMetrics.density).toInt()
        }
        
        // ========== 4. Apply Text Color Overrides ==========
        try {
            // Mode Specific Text Colors
            // Note: We don't have direct input fields for mode-specific text colors in the UI yet (except in legacy code)
            // fallback to legacy inputs
            
            val primText = binding.inputPrimaryText.text.toString()
            if (isValidHex(primText)) binding.previewTitle.setTextColor(Color.parseColor(primText))
            
            val secText = binding.inputSecondaryText.text.toString()
            if (isValidHex(secText)) binding.previewBody.setTextColor(Color.parseColor(secText))
        } catch (e: Exception) { }
        
        // ========== 5. Apply Elite Noise (Subtle Preview) ==========
        val noiseIntensity = binding.sliderNoise.value
        if (noiseIntensity > 0) {
            val noise = com.neubofy.reality.ui.view.NoiseDrawable(noiseIntensity * 0.15f)
            val currentBg = binding.previewContainer.background
            if (currentBg != null) {
                binding.previewContainer.background = android.graphics.drawable.LayerDrawable(arrayOf(currentBg, noise))
            } else {
                binding.previewContainer.background = noise
            }
        }
    }
    
    private fun setupColorPickerIntegration() {
        // Legacy color inputs
        val mapping = mutableListOf(
            Triple(binding.inputLayoutPopupBg, binding.inputPopupBg, "Popup Background"),
            Triple(binding.inputLayoutAppBg, binding.inputAppBg, "App Background"),
            Triple(binding.inputLayoutPrimaryText, binding.inputPrimaryText, "Primary Text"),
            Triple(binding.inputLayoutSecondaryText, binding.inputSecondaryText, "Secondary Text")
        )
        
        // Add new mode-specific color inputs (wrapped in try-catch for safety)
        try {
            mapping.addAll(listOf(
                Triple(binding.inputLayoutModePageBg, binding.inputModePageBg, "Page Background"),
                Triple(binding.inputLayoutModeCardBg, binding.inputModeCardBg, "Card Background"),
                Triple(binding.inputLayoutModeCardStroke, binding.inputModeCardStroke, "Card Stroke"),
                Triple(binding.inputLayoutModeAiBubble, binding.inputModeAiBubble, "AI Chat Bubble"),
                Triple(binding.inputLayoutModeUserBubble, binding.inputModeUserBubble, "User Chat Bubble"),
                Triple(binding.inputLayoutModeSuccess, binding.inputModeSuccess, "Success Color"),
                Triple(binding.inputLayoutModeWarning, binding.inputModeWarning, "Warning Color"),
                Triple(binding.inputLayoutModeError, binding.inputModeError, "Error Color")
            ))
        } catch (e: Exception) {
            android.util.Log.w("AppearanceActivity", "New color inputs not available: ${e.message}")
        }
        
        mapping.forEach { (layout, input, title) ->
            layout.setStartIconOnClickListener {
                val currentColorStr = input.text.toString()
                val initialColor = try {
                    if (currentColorStr.isNotEmpty()) Color.parseColor(currentColorStr) else Color.BLACK
                } catch (e: Exception) { Color.BLACK }
                
                ColorPickerDialog(initialColor) { selectedColor ->
                    val hex = String.format("#%06X", (0xFFFFFF and selectedColor))
                    input.setText(hex)
                    layout.setStartIconTintList(android.content.res.ColorStateList.valueOf(selectedColor))
                }.show(supportFragmentManager, "ColorPicker")
            }
            // Initial Tint
             val initialStr = input.text.toString()
             if (isValidHex(initialStr)) {
                 layout.setStartIconTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(initialStr)))
             }
        }
    }
    
    private fun createIconTintWatcher(layout: com.google.android.material.textfield.TextInputLayout): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val colorStr = s.toString()
                if (isValidHex(colorStr)) {
                    layout.setStartIconTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(colorStr)))
                }
            }
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
        ThemeManager.setAnimationSpeed(this, binding.sliderAnimationV2.value)
        
        // Save Hex Colors
        val bgText = binding.inputPopupBg.text.toString()
        if (isValidHex(bgText)) ThemeManager.setPopupBackgroundColor(this, bgText) else ThemeManager.setPopupBackgroundColor(this, null)
        
        val appBgText = binding.inputAppBg.text.toString()
        if (isValidHex(appBgText)) ThemeManager.setAppBackgroundColor(this, appBgText) else ThemeManager.setAppBackgroundColor(this, null)
        
        val primText = binding.inputPrimaryText.text.toString()
        if (isValidHex(primText)) ThemeManager.setPrimaryTextColor(this, primText) else ThemeManager.setPrimaryTextColor(this, null)
 
        val secText = binding.inputSecondaryText.text.toString()
        if (isValidHex(secText)) ThemeManager.setSecondaryTextColor(this, secText) else ThemeManager.setSecondaryTextColor(this, null)
        
        // Header Style
        val headerStyle = when {
            binding.chipHeaderSolid.isChecked -> ThemeManager.HeaderStyle.SOLID
            binding.chipHeaderBlended.isChecked -> ThemeManager.HeaderStyle.BLENDED
            else -> ThemeManager.HeaderStyle.TRANSPARENT
        }
        ThemeManager.setHeaderStyle(this, headerStyle)
        
        // Pattern & Glass
        ThemeManager.setBackgroundPattern(this, selectedPattern)
        
        val glass = when {
            binding.chipGlassSubtleV2.isChecked -> ThemeManager.GlassIntensity.SUBTLE
            binding.chipGlassMediumV2.isChecked -> ThemeManager.GlassIntensity.MEDIUM
            binding.chipGlassStrongV2.isChecked -> ThemeManager.GlassIntensity.STRONG
            else -> ThemeManager.GlassIntensity.LIGHT
        }
        ThemeManager.setGlassIntensity(this, glass)
        
        // Save Card Style & Radius
        val cardStyle = when {
            binding.styleFilledV2.isChecked -> ThemeManager.CardStyle.FILLED
            binding.styleOutlinedV2.isChecked -> ThemeManager.CardStyle.OUTLINED
            binding.styleGlassV2.isChecked -> ThemeManager.CardStyle.GLASS
            else -> ThemeManager.CardStyle.GLASS
        }
        ThemeManager.setCardStyle(this, cardStyle)
        ThemeManager.setCornerRadius(this, binding.sliderRadiusV2.value.toInt())
        
        // ========== SAVE EXTRA CUSTOMIZATION OPTIONS ==========
        ThemeManager.setFontSizeScale(this, binding.sliderFontSize.value)
        ThemeManager.setSpacingScale(this, binding.sliderSpacing.value)
        
        // Icon Style
        val iconStyle = when {
            binding.iconOutlined.isChecked -> ThemeManager.IconStyle.OUTLINED
            binding.iconRounded.isChecked -> ThemeManager.IconStyle.ROUNDED
            else -> ThemeManager.IconStyle.FILLED
        }
        ThemeManager.setIconStyle(this, iconStyle)
        
        // Button Style
        val buttonStyle = when {
            binding.btnOutlined.isChecked -> ThemeManager.ButtonStyle.OUTLINED
            binding.btnText.isChecked -> ThemeManager.ButtonStyle.TEXT
            else -> ThemeManager.ButtonStyle.FILLED
        }
        ThemeManager.setButtonStyle(this, buttonStyle)
        
        // Compact Mode
        ThemeManager.setCompactMode(this, binding.switchCompactMode.isChecked)
        
        // ========== SAVE ELITE SETTINGS ==========
        ThemeManager.setNoiseIntensity(this, binding.sliderNoise.value)
        
        val haptic = when {
            binding.hapticSoft.isChecked -> ThemeManager.HapticLevel.SOFT
            binding.hapticMedium.isChecked -> ThemeManager.HapticLevel.MEDIUM
            binding.hapticSharp.isChecked -> ThemeManager.HapticLevel.SHARP
            binding.hapticHeavy.isChecked -> ThemeManager.HapticLevel.HEAVY
            else -> ThemeManager.HapticLevel.OFF
        }
        ThemeManager.setHapticLevel(this, haptic)
        
        val motion = when {
            binding.motionStiff.isChecked -> ThemeManager.MotionPreset.STIFF
            binding.motionBouncy.isChecked -> ThemeManager.MotionPreset.BOUNCY
            binding.motionFast.isChecked -> ThemeManager.MotionPreset.FAST
            else -> ThemeManager.MotionPreset.FLUID
        }
        ThemeManager.setMotionPreset(this, motion)
        ThemeManager.setShimmerEnabled(this, binding.switchShimmer.isChecked)
        
        // ========== SAVE MODE-SPECIFIC COLORS ==========
        // Save the currently editing mode's colors
        saveModeSpecificColors(editingMode)
        
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
    


    private fun updateAmoledVisibility() {
        val isDark = binding.modeDark.isChecked || (binding.modeSystem.isChecked && isSystemDark())
        binding.switchAmoled.isEnabled = isDark
        if (!isDark) binding.switchAmoled.isChecked = false
    }
    
    private fun isSystemDark(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun updatePatternSelectionUI() {
        // Reset all
        binding.checkPatternNone.visibility = android.view.View.GONE
        binding.checkPatternZen.visibility = android.view.View.GONE
        binding.checkPatternGradient.visibility = android.view.View.GONE
        
        // Highlight Selected
        when (selectedPattern) {
            ThemeManager.BackgroundPattern.NONE -> binding.checkPatternNone.visibility = android.view.View.VISIBLE
            ThemeManager.BackgroundPattern.ZEN -> binding.checkPatternZen.visibility = android.view.View.VISIBLE
            ThemeManager.BackgroundPattern.GRADIENT -> binding.checkPatternGradient.visibility = android.view.View.VISIBLE
        }
    }

    private fun updateAccentSelectionUI() {
        val colorMap = mapOf(
            ThemeManager.AccentColor.TEAL to binding.colorTeal,
            ThemeManager.AccentColor.PURPLE to binding.colorPurple,
            ThemeManager.AccentColor.BLUE to binding.colorBlue,
            ThemeManager.AccentColor.PINK to binding.colorPink,
            ThemeManager.AccentColor.ORANGE to binding.colorOrange,
            ThemeManager.AccentColor.CYAN to binding.colorCyan,
            ThemeManager.AccentColor.GREEN to binding.colorGreen,
            ThemeManager.AccentColor.RED to binding.colorRed
        )

        val density = resources.displayMetrics.density
        val strokeWidth = (3 * density).toInt()
        val padding = (8 * density).toInt()

        colorMap.forEach { (accent, view) ->
            if (accent == selectedAccent) {
                // Apply a white rounded stroke as selection indicator
                val stroke = android.graphics.drawable.GradientDrawable()
                stroke.shape = android.graphics.drawable.GradientDrawable.OVAL
                stroke.setStroke(strokeWidth, Color.WHITE)
                stroke.setColor(accent.primaryColor)
                view.background = stroke
                view.setPadding(padding, padding, padding, padding)
            } else {
                view.background = null
                view.setPadding(padding, padding, padding, padding)
            }
        }
    }
}
