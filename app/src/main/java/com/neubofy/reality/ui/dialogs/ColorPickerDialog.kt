package com.neubofy.reality.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.neubofy.reality.databinding.DialogColorPickerBinding
import java.util.Locale

class ColorPickerDialog(
    private val initialColor: Int,
    private val onColorSelected: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!

    private var currentHue = 0f
    private var currentSat = 1f
    private var currentVal = 1f
    private var currentColor = initialColor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initial Setup
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        currentHue = hsv[0]
        currentSat = hsv[1]
        currentVal = hsv[2]

        setupGradients()
        updateUI(fromInput = false)

        // Saturation/Value Touch Listener
        binding.viewSatVal.setOnTouchListener { v, event ->
            handleSatValTouch(event, v)
            true
        }

        // Hue Touch Listener
        binding.viewHueSpectrum.setOnTouchListener { v, event ->
            handleHueTouch(event, v)
            true
        }

        // Hex Input Listener
        binding.inputHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                if (binding.inputHex.hasFocus()) {
                    val colorStr = s.toString()
                    if (colorStr.length >= 6) {
                        try {
                            val hex = if (colorStr.startsWith("#")) colorStr else "#$colorStr"
                            val color = Color.parseColor(hex)
                            val newHsv = FloatArray(3)
                            Color.colorToHSV(color, newHsv)
                            currentHue = newHsv[0]
                            currentSat = newHsv[1]
                            currentVal = newHsv[2]
                            currentColor = color
                            updateUI(fromInput = true)
                        } catch (e: Exception) {
                            // Invalid hex, ignore
                        }
                    }
                }
            }
        })

        binding.btnSelect.setOnClickListener {
            onColorSelected(currentColor)
            dismiss()
        }
        
        // Initial Color Preview
        binding.viewPreviewOld.backgroundTintList = android.content.res.ColorStateList.valueOf(initialColor)
    }

    private fun setupGradients() {
        // Hue Gradient (Rainbow)
        val hueColors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        val hueGradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hueColors)
        hueGradient.cornerRadius = 12f * resources.displayMetrics.density
        binding.viewHueSpectrum.background = hueGradient
    }

    private fun updateSatValGradient() {
        // Saturation/Value Gradient depends on Hue
        // Base is the pure Hue color
        val pureHue = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        
        // Horizontal: White -> Hue
        // Vertical: Transparent -> Black (Overlay)
        // We need to compose drawables or set a custom drawable
        // For simplicity, we can use two overlapping views or a custom logic. 
        // Here, we'll try to update binding.viewSatVal background color? No, we need gradients.
        
        // Create composed drawable programmatically
        val saturationGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.WHITE, pureHue)
        )
        
        val valueGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.BLACK)
        )
        
        val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(saturationGradient, valueGradient))
        binding.viewSatVal.background = layerDrawable
    }

    private fun handleSatValTouch(event: MotionEvent, view: View) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(0f, view.width.toFloat())
                val y = event.y.coerceIn(0f, view.height.toFloat())
                
                currentSat = x / view.width
                currentVal = 1f - (y / view.height)
                
                updateColorFromHsv()
                updateUI(fromInput = false)
            }
        }
    }

    private fun handleHueTouch(event: MotionEvent, view: View) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(0f, view.width.toFloat())
                currentHue = (x / view.width) * 360f
                
                updateColorFromHsv()
                updateUI(fromInput = false)
            }
        }
    }

    private fun updateColorFromHsv() {
        currentColor = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
        updateSatValGradient()
    }

    private fun updateUI(fromInput: Boolean) {
        // Update Preview
        binding.viewPreviewNew.backgroundTintList = android.content.res.ColorStateList.valueOf(currentColor)
        
        // Update Cursors Position
        val width = binding.viewSatVal.width
        val height = binding.viewSatVal.height
        
        // We need to wait for layout pass if widths are 0, but this is usually called after layout or during touch
        if (width > 0 && height > 0) {
            binding.viewCursor.translationX = (currentSat * width) - (binding.viewCursor.width / 2)
            binding.viewCursor.translationY = ((1f - currentVal) * height) - (binding.viewCursor.height / 2)
            binding.viewCursor.backgroundTintList = if (currentVal < 0.5) android.content.res.ColorStateList.valueOf(Color.WHITE) else android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        
        // Hue Cursor
        val hueWidth = binding.viewHueSpectrum.width
        if (hueWidth > 0) {
            binding.viewHueCursor.translationX = (currentHue / 360f * hueWidth) - (binding.viewHueCursor.width / 2)
        }
        
        // Update Text if not from input
        if (!fromInput) {
            val hex = String.format("#%06X", (0xFFFFFF and currentColor))
            if (binding.inputHex.text.toString() != hex) {
               binding.inputHex.setText(hex)
            }
        } else {
             updateSatValGradient()
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Wait for layout to update cursor positions initially
        binding.viewSatVal.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.viewSatVal.viewTreeObserver.removeOnGlobalLayoutListener(this)
                updateUI(fromInput = false)
            }
        })
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
