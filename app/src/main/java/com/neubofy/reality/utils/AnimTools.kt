package com.neubofy.reality.utils

import android.view.View

class AnimTools {
    companion object {
        fun View.animateVisibility(show: Boolean, duration: Long = 300) {
            if (show) {
                this.apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                    animate().alpha(1f).setDuration(duration).start()
                }
            } else {
                this.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction { visibility = View.GONE }
                    .start()
            }
        }
    }
}