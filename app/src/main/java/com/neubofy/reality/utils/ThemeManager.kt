package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.neubofy.reality.R

/**
 * Manages app theme preferences - accent color, dark/light mode, background patterns, and glassmorphism.
 */
object ThemeManager {
    
    private const val PREFS_NAME = "reality_theme_prefs"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_AMOLED_MODE = "amoled_mode"
    private const val KEY_BACKGROUND_PATTERN = "background_pattern"
    private const val KEY_GLASS_INTENSITY = "glass_intensity"
    
    // Pro Customization Keys (Legacy - kept for backward compatibility)
    private const val KEY_POPUP_BG_COLOR = "popup_bg_color" // Hex
    private const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color" // Hex
    private const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color" // Hex
    private const val KEY_ANIMATION_SPEED = "animation_speed" // Float 0.5 - 2.0
    private const val KEY_FONT_FAMILY = "font_family" // String
    
    // ========== MODE-SPECIFIC COLOR KEYS ==========
    // Format: {mode}_{color_type} where mode = "light" or "dark"
    
    // Core Colors (per mode)
    private const val KEY_PAGE_BG = "_page_bg"              // Main page/activity background
    private const val KEY_SURFACE_COLOR = "_surface"        // Card/container surface
    private const val KEY_POPUP_BG = "_popup_bg"            // Dialog/popup background
    
    // Text Colors (per mode)
    private const val KEY_TEXT_PRIMARY = "_text_primary"    // Main text
    private const val KEY_TEXT_SECONDARY = "_text_secondary" // Subtitle/hint text
    private const val KEY_TEXT_ACCENT = "_text_accent"      // Highlighted/accent text
    
    // Card Colors (per mode)
    private const val KEY_CARD_BG = "_card_bg"              // Card background
    private const val KEY_CARD_STROKE = "_card_stroke"      // Card border
    
    // Status Colors (per mode)
    private const val KEY_SUCCESS_COLOR = "_success"        // Success (green)
    private const val KEY_WARNING_COLOR = "_warning"        // Warning (amber)
    private const val KEY_ERROR_COLOR = "_error"            // Error (red)
    private const val KEY_INFO_COLOR = "_info"              // Info (blue)
    
    // Chat UI Colors (per mode)
    private const val KEY_AI_BUBBLE_BG = "_ai_bubble_bg"    // AI message background
    private const val KEY_USER_BUBBLE_BG = "_user_bubble_bg" // User message background
    private const val KEY_AI_BUBBLE_TEXT = "_ai_bubble_text" // AI message text
    private const val KEY_USER_BUBBLE_TEXT = "_user_bubble_text" // User message text
    
    // Input Field Colors (per mode)
    private const val KEY_INPUT_BG = "_input_bg"            // Input field background
    private const val KEY_INPUT_STROKE = "_input_stroke"    // Input field border
    private const val KEY_INPUT_TEXT = "_input_text"        // Input text color
    
    // Utility Colors (per mode)
    private const val KEY_DIVIDER = "_divider"              // Dividers/separators
    private const val KEY_ICON_TINT = "_icon_tint"          // Default icon tint
    private const val KEY_RIPPLE = "_ripple"                // Ripple/touch feedback
    
    // ========== EXTRA CUSTOMIZATION KEYS ==========
    private const val KEY_FONT_SIZE_SCALE = "font_size_scale"   // Float 0.8 - 1.4
    private const val KEY_SPACING_SCALE = "spacing_scale"       // Float 0.8 - 1.2
    private const val KEY_ICON_STYLE = "icon_style"             // Enum: FILLED, OUTLINED, ROUNDED
    private const val KEY_BUTTON_STYLE = "button_style"         // Enum: FILLED, OUTLINED, TEXT
    private const val KEY_COMPACT_MODE = "compact_mode"         // Boolean
    
    // ========== ELITE AESTHETICS 3.0 KEYS ==========
    private const val KEY_NOISE_INTENSITY = "noise_intensity"   // Float 0.0 - 1.0
    private const val KEY_HAPTIC_LEVEL = "haptic_level"         // Enum: OFF, SOFT, MEDIUM, SHARP, HEAVY
    private const val KEY_MOTION_PRESET = "motion_preset"       // Enum: STIFF, FLUID, BOUNCY, FAST
    private const val KEY_SHIMMER_ENABLED = "shimmer_enabled"   // Boolean
    
    // Predefined accent color options with their theme resource IDs
    enum class AccentColor(
        val displayName: String, 
        val primaryColor: Int, 
        val themeResId: Int
    ) {
        TEAL("Teal", 0xFF00695C.toInt(), R.style.Theme_Reality_Teal),
        PURPLE("Purple", 0xFF651FFF.toInt(), R.style.Theme_Reality_Purple),
        BLUE("Blue", 0xFF2196F3.toInt(), R.style.Theme_Reality_Blue),
        PINK("Pink", 0xFFE91E63.toInt(), R.style.Theme_Reality_Pink),
        ORANGE("Orange", 0xFFFF5722.toInt(), R.style.Theme_Reality_Orange),
        CYAN("Cyan", 0xFF00BCD4.toInt(), R.style.Theme_Reality_Cyan),
        GREEN("Green", 0xFF4CAF50.toInt(), R.style.Theme_Reality_Green),
        RED("Red", 0xFFF44336.toInt(), R.style.Theme_Reality_Red);
        
        companion object {
            fun fromName(name: String): AccentColor {
                return entries.find { it.name == name } ?: TEAL
            }
        }
    }
    
    enum class DarkModeOption(val displayName: String, val nightMode: Int) {
        SYSTEM("Follow System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("Light Mode", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("Dark Mode", AppCompatDelegate.MODE_NIGHT_YES);
        
        companion object {
            fun fromName(name: String): DarkModeOption {
                return entries.find { it.name == name } ?: SYSTEM
            }
        }
    }
    
    // Background pattern options
    enum class BackgroundPattern(val displayName: String, val drawableResId: Int) {
        ZEN("Zen Circles", R.drawable.bg_pattern_zen),
        NONE("Solid Color", 0),
        GRADIENT("Gradient", R.drawable.dialog_gradient_background);
        
        companion object {
            fun fromName(name: String): BackgroundPattern {
                return entries.find { it.name == name } ?: ZEN
            }
        }
    }
    
    // Glassmorphism intensity levels
    enum class GlassIntensity(val displayName: String, val alpha: Float, val strokeAlpha: Float) {
        SUBTLE("Subtle", 0.05f, 0.10f),      // Very transparent
        LIGHT("Light", 0.10f, 0.20f),        // Default
        MEDIUM("Medium", 0.15f, 0.30f),      // More visible
        STRONG("Strong", 0.25f, 0.40f);      // Very visible glass
        
        companion object {
            fun fromName(name: String): GlassIntensity {
                return entries.find { it.name == name } ?: LIGHT
            }
        }
    }

    // Elite Haptic levels
    enum class HapticLevel(val displayName: String, val hapticConstant: Int?) {
        OFF("Off", null),
        SOFT("Soft", android.view.HapticFeedbackConstants.CONTEXT_CLICK),
        MEDIUM("Medium", android.view.HapticFeedbackConstants.VIRTUAL_KEY),
        SHARP("Sharp", android.view.HapticFeedbackConstants.KEYBOARD_TAP),
        HEAVY("Heavy", android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    // Elite Motion presets
    enum class MotionPreset(val displayName: String, val tension: Float, val friction: Float) {
        STIFF("Classic/Stiff", 300f, 30f),
        FLUID("Fluid Silk", 150f, 25f),
        BOUNCY("Energetic/Bouncy", 200f, 15f),
        FAST("Hyper Fast", 500f, 40f)
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ========== ACCENT COLOR ==========
    fun getAccentColor(context: Context): AccentColor {
        val name = getPrefs(context).getString(KEY_ACCENT_COLOR, AccentColor.TEAL.name) ?: AccentColor.TEAL.name
        return AccentColor.fromName(name)
    }
    
    fun setAccentColor(context: Context, color: AccentColor) {
        getPrefs(context).edit().putString(KEY_ACCENT_COLOR, color.name).apply()
    }
    
    // ========== DARK MODE ==========
    fun getDarkMode(context: Context): DarkModeOption {
        val name = getPrefs(context).getString(KEY_DARK_MODE, DarkModeOption.SYSTEM.name) ?: DarkModeOption.SYSTEM.name
        return DarkModeOption.fromName(name)
    }
    
    fun setDarkMode(context: Context, mode: DarkModeOption) {
        getPrefs(context).edit().putString(KEY_DARK_MODE, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
    
    // ========== AMOLED MODE ==========
    fun isAmoledMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AMOLED_MODE, false)
    }
    
    fun setAmoledMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }
    
    // ========== BACKGROUND PATTERN ==========
    fun getBackgroundPattern(context: Context): BackgroundPattern {
        val name = getPrefs(context).getString(KEY_BACKGROUND_PATTERN, BackgroundPattern.ZEN.name) ?: BackgroundPattern.ZEN.name
        return BackgroundPattern.fromName(name)
    }
    
    fun setBackgroundPattern(context: Context, pattern: BackgroundPattern) {
        getPrefs(context).edit().putString(KEY_BACKGROUND_PATTERN, pattern.name).apply()
    }
    
    // ========== GLASSMORPHISM INTENSITY ==========
    fun getGlassIntensity(context: Context): GlassIntensity {
        val name = getPrefs(context).getString(KEY_GLASS_INTENSITY, GlassIntensity.LIGHT.name) ?: GlassIntensity.LIGHT.name
        return GlassIntensity.fromName(name)
    }
    
    fun setGlassIntensity(context: Context, intensity: GlassIntensity) {
        getPrefs(context).edit().putString(KEY_GLASS_INTENSITY, intensity.name).apply()
    }
    
    /**
     * Get the theme resource ID for the current accent color.
     */
    fun getThemeResId(context: Context): Int {
        return getAccentColor(context).themeResId
    }
    
    /**
     * Apply saved theme settings on app startup.
     * Call this BEFORE super.onCreate() and setContentView().
     */
    fun applyTheme(context: Context) {
        // Apply dark mode setting
        val darkMode = getDarkMode(context)
        AppCompatDelegate.setDefaultNightMode(darkMode.nightMode)
    }
    
    /**
     * Apply the accent color theme to an activity.
     * Call this BEFORE setContentView().
     */
    fun applyAccentTheme(context: android.app.Activity) {
        val themeResId = getAccentColor(context).themeResId
        context.setTheme(themeResId)
        
        // Apply AMOLED if needed
        if (isAmoledMode(context)) {
             val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
             if (isNight) {
                 context.window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
             }
        }
        
        enforceEdgeToEdge(context)
    }

    /**
     * Enforces transparent system bars and edge-to-edge layout.
     * Safe to call in onResume completely.
     */
    fun enforceEdgeToEdge(context: android.app.Activity) {
        context.window.statusBarColor = android.graphics.Color.TRANSPARENT
        context.window.navigationBarColor = android.graphics.Color.TRANSPARENT // Ensure Nav Bar is also transparent
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(context.window, false)
    }
    
    /**
     * Get glass stroke color with current intensity.
     */
    fun getGlassStrokeColor(context: Context): Int {
        val intensity = getGlassIntensity(context)
        val alpha = (intensity.strokeAlpha * 255).toInt()
        return android.graphics.Color.argb(alpha, 255, 255, 255) // White stroke with alpha
    }
    
    // ========== PRO CUSTOMIZATION: SHAPES ==========
    private const val KEY_CORNER_RADIUS = "corner_radius" // Int dp
    private const val KEY_CARD_STYLE = "card_style" // Enum
    
    enum class CardStyle(val displayName: String) {
        FILLED("Filled"),
        OUTLINED("Outlined"),
        GLASS("Smart Glass")
    }
    
    fun getCornerRadius(context: Context): Int {
        return getPrefs(context).getInt(KEY_CORNER_RADIUS, 16) // Default 16dp
    }
    
    fun setCornerRadius(context: Context, radiusDp: Int) {
        getPrefs(context).edit().putInt(KEY_CORNER_RADIUS, radiusDp).apply()
    }
    
    fun getCardStyle(context: Context): CardStyle {
        val name = getPrefs(context).getString(KEY_CARD_STYLE, CardStyle.GLASS.name) ?: CardStyle.GLASS.name
        return CardStyle.valueOf(name)
    }
    
    fun setCardStyle(context: Context, style: CardStyle) {
        getPrefs(context).edit().putString(KEY_CARD_STYLE, style.name).apply()
    }
    
    /**
     * Get glass background color with current intensity.
     */
    fun getGlassBackgroundColor(context: Context): Int {
        val intensity = getGlassIntensity(context)
        val alpha = (intensity.alpha * 255).toInt()
        return android.graphics.Color.argb(alpha, 255, 255, 255) // White with alpha
    }

    fun isDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Applies glassmorphism, shapes, and styles to a MaterialCardView based on preferences.
     */
    fun applyCardAppearance(card: com.google.android.material.card.MaterialCardView) {
        val context = card.context
        val pattern = getBackgroundPattern(context)
        val glass = getGlassIntensity(context)
        val style = getCardStyle(context)
        val radius = getCornerRadius(context).toFloat()
        
        // Apply Corner Radius
        val density = context.resources.displayMetrics.density
        card.radius = radius * density
        
        // Check for mode-specific custom card colors FIRST
        val customCardBg = getCardBackgroundColor(context)
        val customCardStroke = getCardStrokeColor(context)
        
        if (customCardBg != null) {
            // User has set a custom card background for this mode
            card.setCardBackgroundColor(customCardBg)
            if (customCardStroke != null) {
                card.strokeColor = customCardStroke
                card.strokeWidth = (1 * density).toInt()
            } else {
                card.strokeWidth = 0
            }
            return // Custom colors take full precedence
        }
        
        // Background Color & Style Logic (default behavior)
        if (isAmoledMode(context) && isDark(context)) {
             card.setCardBackgroundColor(android.graphics.Color.BLACK)
             card.strokeColor = customCardStroke ?: android.graphics.Color.DKGRAY
             card.strokeWidth = (1 * density).toInt()
        } else {
             val typedValue = android.util.TypedValue()
             context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
             val surfaceColor = typedValue.data
             
             when (style) {
                 CardStyle.FILLED -> {
                     card.setCardBackgroundColor(surfaceColor)
                     card.strokeWidth = 0
                 }
                 CardStyle.OUTLINED -> {
                     card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                     // Use custom stroke or fallback to outline color
                     if (customCardStroke != null) {
                         card.strokeColor = customCardStroke
                     } else {
                         val outValue = android.util.TypedValue()
                         context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, outValue, true)
                         card.strokeColor = if(outValue.resourceId != 0) context.getColor(outValue.resourceId) else android.graphics.Color.GRAY
                     }
                     card.strokeWidth = (1 * density).toInt()
                 }
                 CardStyle.GLASS -> {
                     // Existing Glass Logic
                     if (pattern == BackgroundPattern.NONE) {
                         // Fallback to Solid Surface if no pattern
                         card.setCardBackgroundColor(surfaceColor)
                         card.strokeWidth = 0
                     } else {
                         card.setCardBackgroundColor(getGlassBackgroundColor(context))
                         card.strokeColor = customCardStroke ?: getGlassStrokeColor(context)
                         card.strokeWidth = (1 * density).toInt()
                     }
                 }
             }
        }
    }
    
    /**
     * Recursively applies all personalization (cards, spacing, styles, colors) to the view hierarchy.
     */
    fun applyGlobalPersonalization(view: android.view.View) {
        val context = view.context
        
        // 1. Apply Card Appearance (if it's a card)
        if (view is com.google.android.material.card.MaterialCardView) {
            applyCardAppearance(view)
        }
        
        // 2. Apply Button Styles
        if (view is com.google.android.material.button.MaterialButton) {
            applyButtonStyle(view)
        }
        
        // 3. Apply Spacing & Density
        applySpacingAndDensity(view)
        
        // 4. Apply Soft Colors (Text, Icons, Dividers)
        applySoftColors(view)
        
        // 5. Recurse for ViewGroups
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyGlobalPersonalization(view.getChildAt(i))
            }
        }
    }



    private fun applySpacingAndDensity(view: android.view.View) {
        val context = view.context
        val scale = getSpacingScale(context)
        val isCompact = isCompactMode(context)
        
        // 1. Get/Store original dimensions to prevent cumulative scaling (glitch fix)
        // Use the ID we defined in ids.xml
        var original = view.getTag(R.id.tag_original_dimensions) as? IntArray
        if (original == null) {
            val lp = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            original = intArrayOf(
                lp?.leftMargin ?: 0, lp?.topMargin ?: 0, lp?.rightMargin ?: 0, lp?.bottomMargin ?: 0,
                view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom
            )
            view.setTag(R.id.tag_original_dimensions, original)
        }
        
        // 2. Always calculate from ORIGINAL values
        val lp = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        
        var topMult = scale
        var bottomMult = scale
        if (isCompact) {
            topMult *= 0.6f
            bottomMult *= 0.6f
        }
        
        lp.leftMargin = (original[0] * scale).toInt()
        lp.rightMargin = (original[2] * scale).toInt()
        lp.topMargin = (original[1] * topMult).toInt()
        lp.bottomMargin = (original[3] * bottomMult).toInt()
        
        // Apply Compact Padding to TextViews
        if (isCompact && view is android.widget.TextView) {
            view.setPadding(
                original[4],
                (original[5] * 0.7f).toInt(),
                original[6],
                (original[7] * 0.7f).toInt()
            )
        } else {
            // Restore original padding if compact is off
            view.setPadding(original[4], original[5], original[6], original[7])
        }
        
        view.layoutParams = lp
    }

    private fun applyButtonStyle(button: com.google.android.material.button.MaterialButton) {
        val context = button.context
        val style = getButtonStyle(context)
        val density = context.resources.displayMetrics.density
        
        when (style) {
            ButtonStyle.FILLED -> {
                // Keep default or ensure background is set
            }
            ButtonStyle.OUTLINED -> {
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                button.strokeWidth = (1 * density).toInt()
                button.strokeColor = android.content.res.ColorStateList.valueOf(button.textColors.defaultColor)
            }
            ButtonStyle.TEXT -> {
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                button.strokeWidth = 0
                button.elevation = 0f
            }
        }
        
        // Elite Haptic Integration (Safe Touch Listener)
        button.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                performEliteHaptic(v)
            }
            false // Return false to allow click listener to work
        }
    }

    @Suppress("DEPRECATION")
    private fun applySoftColors(view: android.view.View) {
        val context = view.context
        
        // Text Colors & Scaling
        if (view is android.widget.TextView) {
            // A. Apply Font Scale
            val scale = getFontSizeScale(context)
            if (scale != 1.0f) {
                var originalSize = view.getTag(R.id.tag_original_text_size) as? Float
                if (originalSize == null) {
                    // Store size in SP
                    originalSize = view.textSize / context.resources.displayMetrics.scaledDensity
                    view.setTag(R.id.tag_original_text_size, originalSize)
                }
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, originalSize * scale)
            } else {
                // Restore if scale is 1.0
                val originalSize = view.getTag(R.id.tag_original_text_size) as? Float
                if (originalSize != null) {
                    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, originalSize)
                }
            }

            val primary = getModePrimaryTextColor(context)
            val secondary = getModeSecondaryTextColor(context)
            val accent = getAccentTextColor(context)
            
            // Heuristic for Title vs Body vs Subtitle
            when {

                // 1. Accent Text (if explicitly sought via tag or ID pattern)
                view.id.toString().contains("accent", ignoreCase = true) -> {
                    accent?.let { view.setTextColor(it) }
                }
                
                // 2. Primary Text (Titles, Headers)
                view.textSize > 17 * context.resources.displayMetrics.density || 
                view.typeface?.isBold == true -> {
                    primary?.let { view.setTextColor(it) }
                }
                
                // 3. Secondary Text (Subtitles, small text, hints)
                else -> {
                    secondary?.let { view.setTextColor(it) }
                }
            }
        }
        
        // Icon Tints (Material Icons often use colorOnSurfaceVariant or colorPrimary)
        if (view is android.widget.ImageView) {
            val tint = getIconTintColor(context)
            if (tint != null) {
                // Only tint if it doesn't look like a content image (e.g. app icon or profile pic)
                if (view.id.toString().contains("icon", ignoreCase = true) || 
                    view.id.toString().contains("btn", ignoreCase = true) ||
                    view.id.toString().contains("iv_profile", ignoreCase = true).not()) {
                     view.imageTintList = android.content.res.ColorStateList.valueOf(tint)
                }
            }
        }
        
        // Dividers
        val dividerColor = getDividerColor(context)
        if (dividerColor != null) {
            // Check if it looks like a divider (height <= 2dp)
            val lp = view.layoutParams
            if (lp != null && lp.height > 0 && lp.height <= 3 * context.resources.displayMetrics.density) {
                view.setBackgroundColor(dividerColor)
            }
        }
    }



    // ========== PRO CUSTOMIZATION: APP BACKGROUND ==========
    private const val KEY_APP_BACKGROUND_COLOR = "app_background_color" // Hex
    private const val KEY_HEADER_STYLE = "header_style" // String enum

    enum class HeaderStyle(val displayName: String) {
        TRANSPARENT("Transparent"),
        SOLID("Solid Surface"),
        BLENDED("Blended/Glass")
    }

    fun getAppBackgroundColor(context: Context): Int? {
         val hex = getPrefs(context).getString(KEY_APP_BACKGROUND_COLOR, null)
         return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }

    fun setAppBackgroundColor(context: Context, hex: String?) {
        if (hex == null) {
            getPrefs(context).edit().remove(KEY_APP_BACKGROUND_COLOR).apply()
        } else {
            getPrefs(context).edit().putString(KEY_APP_BACKGROUND_COLOR, hex).apply()
        }
    }
    
    fun getHeaderStyle(context: Context): HeaderStyle {
        val name = getPrefs(context).getString(KEY_HEADER_STYLE, HeaderStyle.TRANSPARENT.name) ?: HeaderStyle.TRANSPARENT.name
        return HeaderStyle.valueOf(name)
    }
    
    fun setHeaderStyle(context: Context, style: HeaderStyle) {
        getPrefs(context).edit().putString(KEY_HEADER_STYLE, style.name).apply()
    }

    /**
     * Applies the custom app background (color and/or pattern) to a view.
     * Prioritizes the mode-specific "Page Background" over legacy "App Background".
     */
    fun applyAppBackground(view: android.view.View) {
        val context = view.context
        val pattern = getBackgroundPattern(context)
        val bgColor = getPageBackgroundColor(context) ?: getAppBackgroundColor(context)
        
        if (bgColor != null) {
            if (pattern != BackgroundPattern.NONE && pattern.drawableResId != 0) {
                // Combined Color + Pattern
                val colorDrawable = android.graphics.drawable.ColorDrawable(bgColor)
                val patternDrawable = androidx.core.content.ContextCompat.getDrawable(context, pattern.drawableResId)
                if (patternDrawable != null) {
                    // Pattern usually has some transparency, so it layers nicely
                    val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(colorDrawable, patternDrawable))
                    view.background = layerDrawable
                } else {
                    view.setBackgroundColor(bgColor)
                }
            } else {
                view.setBackgroundColor(bgColor)
            }
        } else if (pattern != BackgroundPattern.NONE && pattern.drawableResId != 0) {
            view.setBackgroundResource(pattern.drawableResId)
        }

        // Apply Elite Noise Overlay if intensity > 0
        val noiseIntensity = getNoiseIntensity(context)
        if (noiseIntensity > 0) {
            val noise = com.neubofy.reality.ui.view.NoiseDrawable(noiseIntensity * 0.1f) // Scale down for subtlety
            val currentBg = view.background
            if (currentBg != null) {
                view.background = android.graphics.drawable.LayerDrawable(arrayOf(currentBg, noise))
            } else {
                view.background = noise
            }
        }
    }

    
    // Existing PRO keys...
    fun getPopupBackgroundColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_POPUP_BG_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setPopupBackgroundColor(context: Context, hex: String?) {
        if (hex == null) {
            getPrefs(context).edit().remove(KEY_POPUP_BG_COLOR).apply()
        } else {
            getPrefs(context).edit().putString(KEY_POPUP_BG_COLOR, hex).apply()
        }
    }
    
    // ========== PRO CUSTOMIZATION: TEXT COLORS ==========
    fun getPrimaryTextColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_PRIMARY_TEXT_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setPrimaryTextColor(context: Context, hex: String?) {
        if (hex == null) {
            getPrefs(context).edit().remove(KEY_PRIMARY_TEXT_COLOR).apply()
        } else {
            getPrefs(context).edit().putString(KEY_PRIMARY_TEXT_COLOR, hex).apply()
        }
    }
    
    fun getSecondaryTextColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_SECONDARY_TEXT_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setSecondaryTextColor(context: Context, hex: String?) {
        if (hex == null) {
            getPrefs(context).edit().remove(KEY_SECONDARY_TEXT_COLOR).apply()
        } else {
            getPrefs(context).edit().putString(KEY_SECONDARY_TEXT_COLOR, hex).apply()
        }
    }
    
    // ========== PRO CUSTOMIZATION: ANIMATION SPEED ==========
    fun getAnimationSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ANIMATION_SPEED, 1.0f)
    }
    
    fun setAnimationSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_ANIMATION_SPEED, speed).apply()
    }
    
    // ========== EXTRA CUSTOMIZATION ENUMS ==========
    enum class IconStyle(val displayName: String) {
        FILLED("Filled"),
        OUTLINED("Outlined"),
        ROUNDED("Rounded")
    }
    
    enum class ButtonStyle(val displayName: String) {
        FILLED("Filled"),
        OUTLINED("Outlined"),
        TEXT("Text Only")
    }
    
    // ========== MODE-AWARE COLOR GETTERS/SETTERS ==========
    
    /**
     * Get the mode prefix for storing mode-specific preferences.
     * Returns "light" or "dark" based on the current UI mode.
     */
    fun getModePrefix(context: Context): String {
        return if (isDark(context)) "dark" else "light"
    }
    
    /**
     * Generic getter for mode-specific colors.
     * @param colorKey The color key suffix (e.g., KEY_PAGE_BG)
     * @param fallbackColor Default color if not set
     */
    private fun getModeColor(context: Context, colorKey: String, fallbackColor: Int? = null): Int? {
        val prefix = getModePrefix(context)
        val hex = getPrefs(context).getString(prefix + colorKey, null)
        return try {
            if (hex != null) android.graphics.Color.parseColor(hex) else fallbackColor
        } catch (e: Exception) { fallbackColor }
    }
    
    /**
     * Generic setter for mode-specific colors.
     */
    private fun setModeColor(context: Context, colorKey: String, hex: String?, forMode: String? = null) {
        val prefix = forMode ?: getModePrefix(context)
        if (hex == null) {
            getPrefs(context).edit().remove(prefix + colorKey).apply()
        } else {
            getPrefs(context).edit().putString(prefix + colorKey, hex).apply()
        }
    }
    
    // ========== PAGE & SURFACE COLORS ==========
    fun getPageBackgroundColor(context: Context): Int? = getModeColor(context, KEY_PAGE_BG)
    fun setPageBackgroundColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_PAGE_BG, hex, forMode)
    
    fun getSurfaceColor(context: Context): Int? = getModeColor(context, KEY_SURFACE_COLOR)
    fun setSurfaceColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_SURFACE_COLOR, hex, forMode)
    
    fun getModePopupBgColor(context: Context): Int? = getModeColor(context, KEY_POPUP_BG)
    fun setModePopupBgColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_POPUP_BG, hex, forMode)
    
    // ========== TEXT COLORS (MODE-SPECIFIC) ==========
    fun getModePrimaryTextColor(context: Context): Int? = getModeColor(context, KEY_TEXT_PRIMARY)
    fun setModePrimaryTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_TEXT_PRIMARY, hex, forMode)
    
    fun getModeSecondaryTextColor(context: Context): Int? = getModeColor(context, KEY_TEXT_SECONDARY)
    fun setModeSecondaryTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_TEXT_SECONDARY, hex, forMode)
    
    fun getAccentTextColor(context: Context): Int? = getModeColor(context, KEY_TEXT_ACCENT)
    fun setAccentTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_TEXT_ACCENT, hex, forMode)
    
    // ========== CARD COLORS (MODE-SPECIFIC) ==========
    fun getCardBackgroundColor(context: Context): Int? = getModeColor(context, KEY_CARD_BG)
    fun setCardBackgroundColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_CARD_BG, hex, forMode)
    
    fun getCardStrokeColor(context: Context): Int? = getModeColor(context, KEY_CARD_STROKE)
    fun setCardStrokeColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_CARD_STROKE, hex, forMode)
    
    // ========== STATUS COLORS (MODE-SPECIFIC) ==========
    fun getSuccessColor(context: Context): Int = getModeColor(context, KEY_SUCCESS_COLOR) ?: 0xFF4CAF50.toInt()
    fun setSuccessColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_SUCCESS_COLOR, hex, forMode)
    
    fun getWarningColor(context: Context): Int = getModeColor(context, KEY_WARNING_COLOR) ?: 0xFFFFC107.toInt()
    fun setWarningColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_WARNING_COLOR, hex, forMode)
    
    fun getErrorColor(context: Context): Int = getModeColor(context, KEY_ERROR_COLOR) ?: 0xFFF44336.toInt()
    fun setErrorColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_ERROR_COLOR, hex, forMode)
    
    fun getInfoColor(context: Context): Int = getModeColor(context, KEY_INFO_COLOR) ?: 0xFF2196F3.toInt()
    fun setInfoColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_INFO_COLOR, hex, forMode)
    
    // ========== CHAT UI COLORS (MODE-SPECIFIC) ==========
    fun getAiBubbleBackgroundColor(context: Context): Int? = getModeColor(context, KEY_AI_BUBBLE_BG)
    fun setAiBubbleBackgroundColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_AI_BUBBLE_BG, hex, forMode)
    
    fun getUserBubbleBackgroundColor(context: Context): Int? = getModeColor(context, KEY_USER_BUBBLE_BG)
    fun setUserBubbleBackgroundColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_USER_BUBBLE_BG, hex, forMode)
    
    fun getAiBubbleTextColor(context: Context): Int? = getModeColor(context, KEY_AI_BUBBLE_TEXT)
    fun setAiBubbleTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_AI_BUBBLE_TEXT, hex, forMode)
    
    fun getUserBubbleTextColor(context: Context): Int? = getModeColor(context, KEY_USER_BUBBLE_TEXT)
    fun setUserBubbleTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_USER_BUBBLE_TEXT, hex, forMode)
    
    // ========== INPUT FIELD COLORS (MODE-SPECIFIC) ==========
    fun getInputBackgroundColor(context: Context): Int? = getModeColor(context, KEY_INPUT_BG)
    fun setInputBackgroundColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_INPUT_BG, hex, forMode)
    
    fun getInputStrokeColor(context: Context): Int? = getModeColor(context, KEY_INPUT_STROKE)
    fun setInputStrokeColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_INPUT_STROKE, hex, forMode)
    
    fun getInputTextColor(context: Context): Int? = getModeColor(context, KEY_INPUT_TEXT)
    fun setInputTextColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_INPUT_TEXT, hex, forMode)
    
    // ========== UTILITY COLORS (MODE-SPECIFIC) ==========
    fun getDividerColor(context: Context): Int? = getModeColor(context, KEY_DIVIDER)
    fun setDividerColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_DIVIDER, hex, forMode)
    
    fun getIconTintColor(context: Context): Int? = getModeColor(context, KEY_ICON_TINT)
    fun setIconTintColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_ICON_TINT, hex, forMode)
    
    fun getRippleColor(context: Context): Int? = getModeColor(context, KEY_RIPPLE)
    fun setRippleColor(context: Context, hex: String?, forMode: String? = null) = setModeColor(context, KEY_RIPPLE, hex, forMode)
    
    // ========== EXTRA CUSTOMIZATION OPTIONS ==========
    fun getFontSizeScale(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FONT_SIZE_SCALE, 1.0f)
    }
    
    fun setFontSizeScale(context: Context, scale: Float) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE_SCALE, scale.coerceIn(0.8f, 1.4f)).apply()
    }
    
    fun getSpacingScale(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SPACING_SCALE, 1.0f)
    }
    
    fun setSpacingScale(context: Context, scale: Float) {
        getPrefs(context).edit().putFloat(KEY_SPACING_SCALE, scale.coerceIn(0.8f, 1.2f)).apply()
    }
    
    fun getIconStyle(context: Context): IconStyle {
        val name = getPrefs(context).getString(KEY_ICON_STYLE, IconStyle.FILLED.name) ?: IconStyle.FILLED.name
        return try { IconStyle.valueOf(name) } catch (e: Exception) { IconStyle.FILLED }
    }
    
    fun setIconStyle(context: Context, style: IconStyle) {
        getPrefs(context).edit().putString(KEY_ICON_STYLE, style.name).apply()
    }
    
    fun getButtonStyle(context: Context): ButtonStyle {
        val name = getPrefs(context).getString(KEY_BUTTON_STYLE, ButtonStyle.FILLED.name) ?: ButtonStyle.FILLED.name
        return try { ButtonStyle.valueOf(name) } catch (e: Exception) { ButtonStyle.FILLED }
    }
    
    fun setButtonStyle(context: Context, style: ButtonStyle) {
        getPrefs(context).edit().putString(KEY_BUTTON_STYLE, style.name).apply()
    }
    
    fun isCompactMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_COMPACT_MODE, false)
    }

    fun setCompactMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COMPACT_MODE, enabled).apply()
    }

    // ========== ELITE AESTHETICS 3.0 ACCESSORS ==========

    fun getNoiseIntensity(context: Context): Float {
        return getPrefs(context).getFloat(KEY_NOISE_INTENSITY, 0.0f)
    }

    fun setNoiseIntensity(context: Context, intensity: Float) {
        getPrefs(context).edit().putFloat(KEY_NOISE_INTENSITY, intensity.coerceIn(0f, 1f)).apply()
    }

    fun getHapticLevel(context: Context): HapticLevel {
        val name = getPrefs(context).getString(KEY_HAPTIC_LEVEL, HapticLevel.MEDIUM.name) ?: HapticLevel.MEDIUM.name
        return try { HapticLevel.valueOf(name) } catch (e: Exception) { HapticLevel.MEDIUM }
    }

    fun setHapticLevel(context: Context, level: HapticLevel) {
        getPrefs(context).edit().putString(KEY_HAPTIC_LEVEL, level.name).apply()
    }

    fun getMotionPreset(context: Context): MotionPreset {
        val name = getPrefs(context).getString(KEY_MOTION_PRESET, MotionPreset.FLUID.name) ?: MotionPreset.FLUID.name
        return try { MotionPreset.valueOf(name) } catch (e: Exception) { MotionPreset.FLUID }
    }

    fun setMotionPreset(context: Context, preset: MotionPreset) {
        getPrefs(context).edit().putString(KEY_MOTION_PRESET, preset.name).apply()
    }

    fun isShimmerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHIMMER_ENABLED, false)
    }

    fun setShimmerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHIMMER_ENABLED, enabled).apply()
    }

    /**
     * Performs a haptic feedback based on elite levels.
     */
    fun performEliteHaptic(view: android.view.View) {
        val level = getHapticLevel(view.context)
        val constant = level.hapticConstant ?: return
        view.performHapticFeedback(constant)
    }
    

    
    /**
     * Helper: Get a color with Material fallback.
     * Uses custom color if set, otherwise falls back to Material attribute.
     */
    fun getColorWithFallback(
        context: Context,
        customColor: Int?,
        materialAttr: Int,
        hardcodedDefault: Int = android.graphics.Color.GRAY
    ): Int {
        return customColor ?: try {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(materialAttr, typedValue, true)
            if (typedValue.resourceId != 0) {
                context.getColor(typedValue.resourceId)
            } else {
                typedValue.data
            }
        } catch (e: Exception) {
            hardcodedDefault
        }
    }
    
    /**
     * Apply scaled font size to a TextView.
     */
    fun applyScaledTextSize(textView: android.widget.TextView, baseSizeSp: Float) {
        val scale = getFontSizeScale(textView.context)
        textView.textSize = baseSizeSp * scale
    }
    
    /**
     * Get scaled dimension value.
     */
    fun getScaledDimension(context: Context, baseDp: Float): Float {
        val scale = getSpacingScale(context)
        val density = context.resources.displayMetrics.density
        return baseDp * density * scale
    }
    
    /**
     * Resets all theme settings to defaults.
     */
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().clear().apply()
        // Re-apply defaults immediately where possible
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    /**
     * Finds and applies theme to all cards in a hierarchy.
     */
    fun applyToAllCards(rootView: android.view.View) {
        if (rootView is com.google.android.material.card.MaterialCardView) {
            applyToCard(rootView)
        }
        if (rootView is android.view.ViewGroup) {
            for (i in 0 until rootView.childCount) {
                applyToAllCards(rootView.getChildAt(i))
            }
        }
    }

    /**
     * Applies the current card style, radius, and premium effects to a specific card.
     */
    fun applyToCard(card: com.google.android.material.card.MaterialCardView) {
        val context = card.context
        val style = getCardStyle(context)
        val radius = getCornerRadius(context).toFloat()
        
        card.radius = radius * context.resources.displayMetrics.density
        
        // Mode specific color overrides
        val customBg = getCardBackgroundColor(context)
        val customStroke = getCardStrokeColor(context)
        
        when (style) {
            CardStyle.GLASS -> {
                val intensity = getGlassIntensity(context)
                val bgColor = when (intensity) {
                    GlassIntensity.SUBTLE -> 0x0DFFFFFF.toInt()
                    GlassIntensity.LIGHT -> 0x1AFFFFFF.toInt()
                    GlassIntensity.MEDIUM -> 0x26FFFFFF.toInt()
                    GlassIntensity.STRONG -> 0x40FFFFFF.toInt()
                }
                card.setCardBackgroundColor(customBg ?: bgColor)
                card.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                card.strokeColor = customStroke ?: getGlassStrokeColor(context)
                card.cardElevation = 0f
            }
            CardStyle.FILLED -> {
                card.setCardBackgroundColor(customBg ?: getColorWithFallback(context, null, com.google.android.material.R.attr.colorSurface))
                card.strokeWidth = 0
                card.cardElevation = (2 * context.resources.displayMetrics.density)
            }
            CardStyle.OUTLINED -> {
                card.setCardBackgroundColor(customBg ?: android.graphics.Color.TRANSPARENT)
                card.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                card.strokeColor = customStroke ?: getColorWithFallback(context, null, com.google.android.material.R.attr.colorOutline)
                card.cardElevation = 0f
            }
        }
        
        // Apply Premium Effects (Noise, Shimmer)
        applyPremiumEffects(card)
    }

    /**
     * Applies Elite effects like Noise and Shimmer to a view.
     */
    fun applyPremiumEffects(view: android.view.View) {
        val context = view.context
        val noiseIntensity = getNoiseIntensity(context)
        
        if (noiseIntensity > 0) {
            val noise = com.neubofy.reality.ui.view.NoiseDrawable(noiseIntensity * 0.05f) // Subtle
            val currentBg = view.background
            if (currentBg != null) {
                view.background = android.graphics.drawable.LayerDrawable(arrayOf(currentBg, noise))
            } else {
                view.background = noise
            }
        }
        
        // Shimmer logic would go here if implemented as a drawable/overlay
        if (isShimmerEnabled(context)) {
            // TODO: Wrap with a ShimmerFrameLayout or apply a Shimmer Shader
        }
    }

}

