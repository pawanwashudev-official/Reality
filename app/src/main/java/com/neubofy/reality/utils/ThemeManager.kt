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
    
    // Pro Customization Keys
    private const val KEY_POPUP_BG_COLOR = "popup_bg_color" // Hex
    private const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color" // Hex
    private const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color" // Hex
    private const val KEY_ANIMATION_SPEED = "animation_speed" // Float 0.5 - 2.0
    private const val KEY_FONT_FAMILY = "font_family" // String
    
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
    }
    
    /**
     * Applies glassmorphism or solid color styling to a MaterialCardView based on preferences.
     */
    fun applyCardAppearance(card: com.google.android.material.card.MaterialCardView) {
        val context = card.context
        val pattern = getBackgroundPattern(context)
        val glass = getGlassIntensity(context)
        
        // Background Color
        // If AMOLED -> Black, otherwise Glass color
        if (isAmoledMode(context) && (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
             card.setCardBackgroundColor(android.graphics.Color.BLACK)
             card.strokeColor = android.graphics.Color.DKGRAY
             card.strokeWidth = 2
        } else {
             // Glass Logic
             val alpha = (glass.alpha * 255).toInt()
             // Use surface color with alpha? Or white with alpha? 
             // Usually glass is white with low alpha on light, and black/grey with alpha on dark.
             // We'll use the theme's surface color but modify alpha.
             val typedValue = android.util.TypedValue()
             context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
             val surfaceColor = typedValue.data
             
             // If pattern is NOT NONE, we want glass. If NONE, solid.
             if (pattern == BackgroundPattern.NONE) {
                 // Solid Surface
                 card.setCardBackgroundColor(surfaceColor)
                 card.strokeWidth = 0
             } else {
                 // Glass Effect
                 val glassyColor = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, (255 * (if(isDark(context)) 0.7f else 0.9f)).toInt()) 
                 // Actually, true glass is usually a tint.
                 // Let's use getGlassBackgroundColor
                 card.setCardBackgroundColor(getGlassBackgroundColor(context))
                 card.strokeColor = getGlassStrokeColor(context)
                 card.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
             }
        }
    }
    
    private fun isDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * Get the current primary color int value for programmatic use.
     */
    fun getCurrentPrimaryColorInt(context: Context): Int {
        return getAccentColor(context).primaryColor
    }
    
    /**
     * Get glass background color with current intensity.
     * Call this to get the color for programmatic glass effect.
     */
    fun getGlassBackgroundColor(context: Context): Int {
        val intensity = getGlassIntensity(context)
        val alpha = (intensity.alpha * 255).toInt()
        return android.graphics.Color.argb(alpha, 255, 255, 255) // White with alpha
    }
    
    /**
     * Get glass stroke color with current intensity.
     */
    fun getGlassStrokeColor(context: Context): Int {
        val intensity = getGlassIntensity(context)
        val alpha = (intensity.strokeAlpha * 255).toInt()
        return android.graphics.Color.argb(alpha, 255, 255, 255) // White stroke with alpha
    }



    // ========== PRO CUSTOMIZATION: POPUP BG ==========
    fun getPopupBackgroundColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_POPUP_BG_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setPopupBackgroundColor(context: Context, hex: String?) {
        getPrefs(context).edit().putString(KEY_POPUP_BG_COLOR, hex).apply()
    }
    
    // ========== PRO CUSTOMIZATION: TEXT COLORS ==========
    fun getPrimaryTextColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_PRIMARY_TEXT_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setPrimaryTextColor(context: Context, hex: String?) {
        getPrefs(context).edit().putString(KEY_PRIMARY_TEXT_COLOR, hex).apply()
    }
    
    fun getSecondaryTextColor(context: Context): Int? {
        val hex = getPrefs(context).getString(KEY_SECONDARY_TEXT_COLOR, null)
        return try { if (hex != null) android.graphics.Color.parseColor(hex) else null } catch (e: Exception) { null }
    }
    
    fun setSecondaryTextColor(context: Context, hex: String?) {
        getPrefs(context).edit().putString(KEY_SECONDARY_TEXT_COLOR, hex).apply()
    }
    
    // ========== PRO CUSTOMIZATION: ANIMATION SPEED ==========
    fun getAnimationSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ANIMATION_SPEED, 1.0f)
    }
    
    fun setAnimationSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_ANIMATION_SPEED, speed).apply()
    }
    
}
