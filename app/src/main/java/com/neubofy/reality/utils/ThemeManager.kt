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
        
        // Ensure Status Bar is transparent for edge-to-edge effect with our new header
        context.window.statusBarColor = android.graphics.Color.TRANSPARENT
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

    private fun isDark(context: Context): Boolean {
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
        
        // Background Color & Style Logic
        if (isAmoledMode(context) && isDark(context)) {
             card.setCardBackgroundColor(android.graphics.Color.BLACK)
             card.strokeColor = android.graphics.Color.DKGRAY
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
                     card.strokeColor = getGlassStrokeColor(context) // Re-use stroke logic or standard outline
                     // Use simpler outline color for non-glass outline
                     val outValue = android.util.TypedValue()
                     context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, outValue, true)
                     card.strokeColor = if(outValue.resourceId != 0) context.getColor(outValue.resourceId) else android.graphics.Color.GRAY
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
                         card.strokeColor = getGlassStrokeColor(context)
                         card.strokeWidth = (1 * density).toInt()
                     }
                 }
             }
        }
    }
    
    /**
     * Recursively applies card appearance to all MaterialCardViews in the view hierarchy.
     */
    fun applyToAllCards(view: android.view.View) {
        if (view is com.google.android.material.card.MaterialCardView) {
            applyCardAppearance(view)
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToAllCards(view.getChildAt(i))
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
        getPrefs(context).edit().putString(KEY_APP_BACKGROUND_COLOR, hex).apply()
    }
    
    fun getHeaderStyle(context: Context): HeaderStyle {
        val name = getPrefs(context).getString(KEY_HEADER_STYLE, HeaderStyle.TRANSPARENT.name) ?: HeaderStyle.TRANSPARENT.name
        return HeaderStyle.valueOf(name)
    }
    
    fun setHeaderStyle(context: Context, style: HeaderStyle) {
        getPrefs(context).edit().putString(KEY_HEADER_STYLE, style.name).apply()
    }

    /**
     * Applies the custom app background color to a root view.
     */
    fun applyAppBackground(view: android.view.View) {
        val color = getAppBackgroundColor(view.context)
        if (color != null) {
            view.setBackgroundColor(color)
        }
    }
    
    // Existing PRO keys...
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
