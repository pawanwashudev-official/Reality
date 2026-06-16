package com.neubofy.reality.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.neubofy.reality.R

class HorizontalNumberPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var minValue: Int = 0
    var maxValue: Int = 100

    private var incrementJob: Job? = null
    private var decrementJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var valueText: EditText
    private lateinit var incrementButton: MaterialButton
    private lateinit var decrementButton: MaterialButton
    private lateinit var unitText: TextView

    init {
        orientation = HORIZONTAL
        initView()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val view =
            LayoutInflater.from(context).inflate(R.layout.horizontal_number_picker, this, true)

        valueText = view.findViewById(R.id.valueTxt)
        unitText = view.findViewById(R.id.unit)
        incrementButton = view.findViewById(R.id.incrementButton)
        decrementButton = view.findViewById(R.id.decrementButton)


        incrementButton.apply {

            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start incrementing when button is pressed
                        incrementButton.performClick()
                        startIncrementing()

                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Stop incrementing when button is released
                        stopIncrementing()
                        true
                    }

                    else -> false
                }
            }

            decrementButton.apply {

                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Start incrementing when button is pressed
                            decrementButton.performClick()
                            startDecrementing()
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Stop incrementing when button is released
                            stopDecrementing()
                            true
                        }

                        else -> false
                    }
                }
            }


        }
    }

    private fun startIncrementing() {
        incrementJob?.cancel()

        incrementJob = coroutineScope.launch {
            while (isActive) {
                val currentValue = getValue()
                if (currentValue < maxValue) {
                    setValue(currentValue + 1)
                }
                incrementButton.performClick()
                delay(100) // Increment every 100 milliseconds
            }
        }
    }

    private fun stopIncrementing() {
        incrementJob?.cancel()
        incrementJob = null
    }


    private fun startDecrementing() {
        decrementJob?.cancel()

        decrementJob = coroutineScope.launch {
            while (isActive) {
                val currentValue = getValue()
                if (currentValue > minValue) {
                    setValue(currentValue - 1)
                }
                decrementButton.performClick()
                delay(100) // Increment every 100 milliseconds
            }
        }
    }

    private fun stopDecrementing() {
        decrementJob?.cancel()
        decrementJob = null
    }

    // Cleanup method to be called when the view is detached
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    @SuppressLint("SetTextI18n")
    fun setValue(value: Int) {
        valueText.setText(value.toString())
    }

    fun getValue(): Int {
        return valueText.text.toString().toInt()
    }

    fun setUnit(unit: String) {
        unitText.text = unit
    }
}