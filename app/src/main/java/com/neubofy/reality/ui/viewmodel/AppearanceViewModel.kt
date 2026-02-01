package com.neubofy.reality.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppearanceViewModel(private val context: Context) : ViewModel() {

    private val _themeState = MutableStateFlow(ThemeState())
    val themeState: StateFlow<ThemeState> = _themeState

    init {
        loadCurrentState()
    }

    fun loadCurrentState() {
        _themeState.value = ThemeState(
            accentColor = ThemeManager.getAccentColor(context),
            darkModeOption = ThemeManager.getDarkMode(context),
            backgroundPattern = ThemeManager.getBackgroundPattern(context),
            cornerRadius = ThemeManager.getCornerRadius(context),
            fontSizeScale = ThemeManager.getFontSizeScale(context),
            spacingScale = ThemeManager.getSpacingScale(context),
            isAmoled = ThemeManager.isAmoledMode(context),
            glassIntensity = ThemeManager.getGlassIntensity(context),
            cardStyle = ThemeManager.getCardStyle(context),
            headerStyle = ThemeManager.getHeaderStyle(context),
            isCompactMode = ThemeManager.isCompactMode(context),
            animationSpeed = ThemeManager.getAnimationSpeed(context),
            noiseIntensity = ThemeManager.getNoiseIntensity(context),
            hapticLevel = ThemeManager.getHapticLevel(context),
            motionPreset = ThemeManager.getMotionPreset(context),
            shimmerEnabled = ThemeManager.isShimmerEnabled(context)
        )
    }

    fun setNoiseIntensity(intensity: Float) {
        _themeState.value = _themeState.value.copy(noiseIntensity = intensity)
        ThemeManager.setNoiseIntensity(context, intensity)
    }

    fun setHapticLevel(level: ThemeManager.HapticLevel) {
        _themeState.value = _themeState.value.copy(hapticLevel = level)
        ThemeManager.setHapticLevel(context, level)
    }

    fun setMotionPreset(preset: ThemeManager.MotionPreset) {
        _themeState.value = _themeState.value.copy(motionPreset = preset)
        ThemeManager.setMotionPreset(context, preset)
    }

    fun setShimmerEnabled(enabled: Boolean) {
        _themeState.value = _themeState.value.copy(shimmerEnabled = enabled)
        ThemeManager.setShimmerEnabled(context, enabled)
    }

    fun setAccentColor(accent: ThemeManager.AccentColor) {
        _themeState.value = _themeState.value.copy(accentColor = accent)
        ThemeManager.setAccentColor(context, accent)
    }

    fun setDarkMode(mode: ThemeManager.DarkModeOption) {
        _themeState.value = _themeState.value.copy(darkModeOption = mode)
        ThemeManager.setDarkMode(context, mode)
    }

    fun setAmoledMode(enabled: Boolean) {
        _themeState.value = _themeState.value.copy(isAmoled = enabled)
        ThemeManager.setAmoledMode(context, enabled)
    }

    fun setCornerRadius(radius: Int) {
        _themeState.value = _themeState.value.copy(cornerRadius = radius)
        ThemeManager.setCornerRadius(context, radius)
    }

    fun setFontSizeScale(scale: Float) {
        _themeState.value = _themeState.value.copy(fontSizeScale = scale)
        ThemeManager.setFontSizeScale(context, scale)
    }
    
    fun setSpacingScale(scale: Float) {
        _themeState.value = _themeState.value.copy(spacingScale = scale)
        ThemeManager.setSpacingScale(context, scale)
    }

    fun setBackgroundPattern(pattern: ThemeManager.BackgroundPattern) {
        _themeState.value = _themeState.value.copy(backgroundPattern = pattern)
        ThemeManager.setBackgroundPattern(context, pattern)
    }

    fun setGlassIntensity(intensity: ThemeManager.GlassIntensity) {
        _themeState.value = _themeState.value.copy(glassIntensity = intensity)
        ThemeManager.setGlassIntensity(context, intensity)
    }

    fun setCardStyle(style: ThemeManager.CardStyle) {
        _themeState.value = _themeState.value.copy(cardStyle = style)
        ThemeManager.setCardStyle(context, style)
    }

    fun setHeaderStyle(style: ThemeManager.HeaderStyle) {
        _themeState.value = _themeState.value.copy(headerStyle = style)
        ThemeManager.setHeaderStyle(context, style)
    }

    fun setCompactMode(enabled: Boolean) {
        _themeState.value = _themeState.value.copy(isCompactMode = enabled)
        ThemeManager.setCompactMode(context, enabled)
    }

    fun setAnimationSpeed(speed: Float) {
        _themeState.value = _themeState.value.copy(animationSpeed = speed)
        ThemeManager.setAnimationSpeed(context, speed)
    }

    fun resetToDefaults() {
        ThemeManager.resetToDefaults(context)
        loadCurrentState()
    }

    data class ThemeState(
        val accentColor: ThemeManager.AccentColor = ThemeManager.AccentColor.TEAL,
        val darkModeOption: ThemeManager.DarkModeOption = ThemeManager.DarkModeOption.SYSTEM,
        val backgroundPattern: ThemeManager.BackgroundPattern = ThemeManager.BackgroundPattern.ZEN,
        val cornerRadius: Int = 16,
        val fontSizeScale: Float = 1.0f,
        val spacingScale: Float = 1.0f,
        val isAmoled: Boolean = false,
        val glassIntensity: ThemeManager.GlassIntensity = ThemeManager.GlassIntensity.LIGHT,
        val cardStyle: ThemeManager.CardStyle = ThemeManager.CardStyle.GLASS,
        val headerStyle: ThemeManager.HeaderStyle = ThemeManager.HeaderStyle.TRANSPARENT,
        val isCompactMode: Boolean = false,
        val animationSpeed: Float = 1.0f,
        val noiseIntensity: Float = 0.0f,
        val hapticLevel: ThemeManager.HapticLevel = ThemeManager.HapticLevel.MEDIUM,
        val motionPreset: ThemeManager.MotionPreset = ThemeManager.MotionPreset.FLUID,
        val shimmerEnabled: Boolean = false
    )
}

