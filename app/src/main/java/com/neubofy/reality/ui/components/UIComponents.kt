package com.neubofy.reality.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.view.View
import android.view.animation.AnimationUtils
import com.neubofy.reality.R

/**
 * Reusable Loading Overlay Component
 * Shows a premium loading state with spinner and optional message
 * 
 * Usage:
 * binding.loadingOverlay.show("Loading...")
 * binding.loadingOverlay.hide()
 */
class LoadingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val progressBar: ProgressBar
    private val messageText: TextView
    
    init {
        // Create programmatically for simplicity
        setBackgroundColor(0x80000000.toInt()) // Semi-transparent black
        isClickable = true // Block clicks to items behind
        isFocusable = true
        visibility = View.GONE
        elevation = 100f // Always on top
        
        // Center container
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        
        // Progress indicator
        progressBar = ProgressBar(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(64.dp, 64.dp)
            isIndeterminate = true
        }
        container.addView(progressBar)
        
        // Message text
        messageText = TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16.dp
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            text = "Loading..."
        }
        container.addView(messageText)
        
        addView(container)
    }
    
    fun show(message: String = "Loading...") {
        messageText.text = message
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()
    }
    
    fun hide() {
        animate().alpha(0f).setDuration(150).withEndAction {
            visibility = View.GONE
        }.start()
    }
    
    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}

/**
 * Reusable Empty State Component
 * Shows illustration + title + subtitle for empty lists/states
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val iconView: ImageView
    private val titleText: TextView
    private val subtitleText: TextView
    private val actionButton: com.google.android.material.button.MaterialButton
    
    init {
        visibility = View.GONE
        
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32.dp, 48.dp, 32.dp, 48.dp)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        
        // Icon
        iconView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(80.dp, 80.dp)
            alpha = 0.5f
        }
        container.addView(iconView)
        
        // Title
        titleText = TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp }
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        container.addView(titleText)
        
        // Subtitle
        subtitleText = TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
            textSize = 14f
            alpha = 0.7f
            gravity = android.view.Gravity.CENTER
        }
        container.addView(subtitleText)
        
        // Action button (optional)
        actionButton = com.google.android.material.button.MaterialButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24.dp }
            visibility = View.GONE
        }
        container.addView(actionButton)
        
        addView(container)
    }
    
    fun setup(
        iconRes: Int,
        title: String,
        subtitle: String,
        actionText: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        iconView.setImageResource(iconRes)
        titleText.text = title
        subtitleText.text = subtitle
        
        if (actionText != null && onAction != null) {
            actionButton.text = actionText
            actionButton.visibility = View.VISIBLE
            actionButton.setOnClickListener { onAction() }
        } else {
            actionButton.visibility = View.GONE
        }
    }
    
    fun show() {
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(300).start()
    }
    
    fun hide() {
        visibility = View.GONE
    }
    
    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}
