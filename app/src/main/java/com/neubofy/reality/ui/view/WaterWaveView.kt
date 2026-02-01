package com.neubofy.reality.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.sin

class WaterWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Properties
    private var progress: Float = 0f // 0.0 to 1.0
    private var waterColor: Int = com.neubofy.reality.utils.ThemeManager.getAccentColor(context).primaryColor
    private var borderColor: Int = waterColor
    private var borderWidth: Float = 15f // Thicker for 3D bezel effect

    
    // Wave physics
    private var waveAmplitude: Float = 20f
    private var waveSpeed: Float = 0.05f
    private var shiftX = 0f
    private var shiftX2 = 0f
    
    // Drawing objects
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    // 3D Bezel Paints
    private val bezelPaintLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bezelPaintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val waterPath = Path()
    
    // Animation
    private var animator: ValueAnimator? = null
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null) // Hardware for better performance
        startAnimation()
    }
    
    fun setProgress(value: Float) {
        val target = value.coerceIn(0f, 1f)
        val diff = kotlin.math.abs(target - progress)
        if (diff > 0.01f) {
           ValueAnimator.ofFloat(progress, target).apply {
               duration = 600 // Slower, smoother fill
               interpolator = android.view.animation.DecelerateInterpolator()
               addUpdateListener { 
                   progress = it.animatedValue as Float
                   invalidate()
               }
               start()
           }
        } else {
            progress = target
            invalidate()
        }
    }
    
    fun setWaterColor(color: Int) {
        waterColor = color
        // Rich Gradient: Deep color at bottom -> Lighter at top
        val deepColor = ColorUtils.blendARGB(color, Color.BLACK, 0.3f)
        val lightColor = ColorUtils.blendARGB(color, Color.WHITE, 0.1f)
        
        waterPaint.shader = android.graphics.LinearGradient(
            0f, height.toFloat(), 0f, 0f, // Bottom to Top
            deepColor, lightColor,
            android.graphics.Shader.TileMode.CLAMP
        )
        waterPaint.color = color
        invalidate()
    }
    
    fun setBorderColor(color: Int) {
        borderColor = color
        // 3D Bezel Gradient setup happens in onSizeChanged
        invalidate()
    }
    
    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { 
                shiftX += waveSpeed * 100
                shiftX2 += waveSpeed * 130
                invalidate()
            }
            start()
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setWaterColor(waterColor)
        
        // Setup Glass Shine (Top reflection)
        val shineGradient = android.graphics.LinearGradient(
            0f, 0f, 0f, h / 2f,
            Color.parseColor("#40FFFFFF"), // Semi-transparent white
            Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
        glassPaint.shader = shineGradient
        
        // Setup 3D Bezel Gradients
        // Light source top-left
        bezelPaintLight.strokeWidth = borderWidth
        bezelPaintLight.shader = android.graphics.LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        bezelPaintLight.alpha = 150
        
        // Shadow bottom-right
        bezelPaintDark.strokeWidth = borderWidth
        bezelPaintDark.shader = android.graphics.LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        bezelPaintDark.alpha = 100
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) - borderWidth) / 2f
        
        // 1. Draw Container Background (Dark glass look)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = Color.parseColor("#10FFFFFF") // Subtle background
        bgPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, bgPaint)
        
        // 2. Draw Water (Clipped to circle)
        canvas.save()
        waterPath.reset()
        waterPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(waterPath)
        
        if (progress > 0) {
            waterPath.reset()
            val waterHeight = height * progress
            val waterLevel = height - waterHeight
            
            waterPath.moveTo(0f, height.toFloat())
            waterPath.lineTo(0f, waterLevel)
            
            var x = 0f
            while (x <= width) {
                val y1 = waveAmplitude * sin((2 * Math.PI * x / width) + (shiftX / width)).toFloat()
                val y2 = (waveAmplitude * 0.6f) * sin((2 * Math.PI * x * 1.5f / width) + (shiftX2 / width)).toFloat()
                waterPath.lineTo(x, waterLevel + y1 + y2)
                x += 10f
            }
            
            waterPath.lineTo(width.toFloat(), height.toFloat())
            waterPath.close()
            canvas.drawPath(waterPath, waterPaint)
        }
        canvas.restore()
        
        // 3. Draw Glass Shine (Top Gloss)
        val glossOval = RectF(cx - radius * 0.7f, cy - radius * 0.9f, cx + radius * 0.7f, cy)
        canvas.drawOval(glossOval, glassPaint)

        // 4. Draw 3D Bezel (Rim)
        // Main colored rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = borderColor
            alpha = 200
        }
        canvas.drawCircle(cx, cy, radius, rimPaint)
        
        // Light highlight (Top Left)
        canvas.drawCircle(cx, cy, radius, bezelPaintLight)
        
        // Dark shadow (Bottom Right)
        canvas.drawCircle(cx, cy, radius, bezelPaintDark)
    }
    
    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
