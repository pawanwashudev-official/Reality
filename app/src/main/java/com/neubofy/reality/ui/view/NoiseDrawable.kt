package com.neubofy.reality.ui.view

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.random.Random

/**
 * A custom drawable that renders a fine-grained noise texture.
 * Used in Elite Aesthetics 3.0 to add physical depth to the UI.
 */
class NoiseDrawable(private val intensity: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var noiseBitmap: Bitmap? = null
    private val shaderMatrix = Matrix()

    init {
        // Only generate if intensity is > 0
        if (intensity > 0) {
            generateNoiseBitmap()
        }
    }

    private fun generateNoiseBitmap() {
        val size = 128 // Small tiled size for performance
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        
        val alpha = (intensity * 255).toInt().coerceIn(0, 255)
        
        for (i in pixels.indices) {
            val brightness = Random.nextInt(256)
            // Multi-colored noise for a more "organic" feel, or grayscale for classic grain
            // Here we use grayscale to stay subtle
            pixels[i] = Color.argb(alpha, brightness, brightness, brightness)
        }
        
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.shader = shader
        noiseBitmap = bitmap
    }

    override fun draw(canvas: Canvas) {
        if (intensity <= 0) return
        
        // Use a blend mode to overlay onto existing content
        // DST_ATOP or OVERLAY-like math
        // In simple terms, we just draw the transparent noise over the background
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
