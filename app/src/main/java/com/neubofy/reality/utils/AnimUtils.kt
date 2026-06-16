package com.neubofy.reality.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.ViewPropertyAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Premium Animation Utilities
 * Provides fast, snappy, and comfortable animations for a premium feel.
 * 
 * Design Philosophy:
 * - Fast (200-300ms) for immediate feedback
 * - Snappy with overshoot for premium feel
 * - Comfortable with smooth easing
 */
object AnimUtils {
    
    // === DURATIONS ===
    const val DURATION_INSTANT = 100L      // Haptic-like instant feedback
    const val DURATION_FAST = 200L         // Quick transitions
    const val DURATION_NORMAL = 300L       // Standard transitions
    const val DURATION_SLOW = 500L         // Important state changes
    
    // === PREMIUM CLICK FEEDBACK ===
    /**
     * Applies a snappy scale animation on click (shrink then bounce back)
     * Makes buttons feel premium and responsive
     */
    fun View.applyClickFeedback(
        scaleDown: Float = 0.95f,
        duration: Long = DURATION_INSTANT
    ) {
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleDown)
                        .scaleY(scaleDown)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration * 2)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            true
        }
    }
    
    // === FADE ANIMATIONS ===
    fun View.fadeIn(duration: Long = DURATION_NORMAL): ViewPropertyAnimator {
        alpha = 0f
        visibility = View.VISIBLE
        return animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
    }
    
    fun View.fadeOut(duration: Long = DURATION_NORMAL, gone: Boolean = true): ViewPropertyAnimator {
        return animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                visibility = if (gone) View.GONE else View.INVISIBLE
            }
    }
    
    // === SLIDE ANIMATIONS ===
    fun View.slideInFromBottom(duration: Long = DURATION_NORMAL): ViewPropertyAnimator {
        translationY = 100f
        alpha = 0f
        visibility = View.VISIBLE
        return animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
    }
    
    fun View.slideOutToBottom(duration: Long = DURATION_NORMAL): ViewPropertyAnimator {
        return animate()
            .translationY(100f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { visibility = View.GONE }
    }
    
    // === SCALE ANIMATIONS ===
    fun View.popIn(duration: Long = DURATION_NORMAL): ViewPropertyAnimator {
        scaleX = 0.5f
        scaleY = 0.5f
        alpha = 0f
        visibility = View.VISIBLE
        return animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(OvershootInterpolator(1.5f))
    }
    
    fun View.popOut(duration: Long = DURATION_FAST): ViewPropertyAnimator {
        return animate()
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { visibility = View.GONE }
    }
    
    // === SUCCESS ANIMATION ===
    /**
     * Premium success animation - scale up with bounce
     * Use after successful actions like saving settings
     */
    fun View.animateSuccess() {
        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.2f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = DURATION_NORMAL
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }
    
    // === SHAKE ANIMATION ===
    /**
     * Shake animation for error feedback
     */
    fun View.shake() {
        val shake = ObjectAnimator.ofFloat(this, "translationX", 0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f)
        shake.duration = 400L
        shake.start()
    }
    
    // === PULSE ANIMATION ===
    /**
     * Continuous pulse for attention-grabbing elements
     */
    fun View.startPulse() {
        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.05f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 1000L
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (visibility == View.VISIBLE) {
                        start()
                    }
                }
            })
            start()
        }
    }
    
    // === CARD ELEVATION ANIMATION ===
    /**
     * Animate card elevation for pressed state
     */
    fun View.animateElevation(fromDp: Float, toDp: Float, duration: Long = DURATION_FAST) {
        val density = resources.displayMetrics.density
        ObjectAnimator.ofFloat(this, "elevation", fromDp * density, toDp * density).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }
    
    // === STAGGERED LIST ANIMATION ===
    /**
     * Animate list items with staggered delay
     */
    fun animateListItems(views: List<View>, delayBetween: Long = 50L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(DURATION_NORMAL)
                .setStartDelay(index * delayBetween)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
    
    // === CIRCULAR REVEAL (For loading) ===
    /**
     * Circular reveal animation starting from center
     */
    fun View.circularReveal(duration: Long = DURATION_SLOW) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val cx = width / 2
            val cy = height / 2
            val finalRadius = kotlin.math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            
            val anim = android.view.ViewAnimationUtils.createCircularReveal(this, cx, cy, 0f, finalRadius)
            anim.duration = duration
            visibility = View.VISIBLE
            anim.start()
        } else {
            fadeIn(duration)
        }
    }
}
