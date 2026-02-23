package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityAmoledFocusBinding
import com.neubofy.reality.services.TapasyaManager
import kotlinx.coroutines.launch

class AmoledFocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAmoledFocusBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen immersive mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        binding = ActivityAmoledFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeClockState()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAction.setOnClickListener {
            val state = TapasyaManager.getCurrentState(this)
            if (state.isRunning) {
                TapasyaManager.pauseSession(this)
            } else if (state.isPaused) {
                TapasyaManager.resumeSession(this)
            } else {
                // Default start — user typically enters AMOLED mode from running Tapasya
                TapasyaManager.startSession(this, "Tapasya", 60 * 60 * 1000L, 15 * 60 * 1000L)
            }
        }

        binding.btnStop.setOnClickListener {
            TapasyaManager.stopSession(this, wasAutoStopped = false)
            finish()
        }
    }

    private fun observeClockState() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    updateUI(TapasyaManager.getCurrentState(this@AmoledFocusActivity))
                    kotlinx.coroutines.delay(500)
                }
            }
        }
    }

    private fun updateUI(state: TapasyaManager.ClockState) {
        binding.tvTimer.text = formatTime(state.elapsedTimeMs)
        binding.waveView.setProgress(state.progress)
        
        // Match wave color to white/grey for AMOLED contrast
        // Primary text is White (#FFFFFF)
        binding.waveView.setWaterColor(android.graphics.Color.WHITE)
        binding.waveView.setBorderColor(android.graphics.Color.WHITE)

        if (state.isRunning) {
            binding.tvStatus.text = "Deep Focus"
            
            binding.btnAction.text = "Pause"
            binding.btnAction.setIconResource(R.drawable.baseline_pause_24)
            
            binding.btnStop.visibility = View.VISIBLE
            
        } else if (state.isPaused) {
            binding.tvStatus.text = "Paused"
            binding.waveView.setWaterColor(android.graphics.Color.DKGRAY)
            
            binding.btnAction.text = "Resume"
            binding.btnAction.setIconResource(R.drawable.baseline_play_arrow_24)
            
            binding.btnStop.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = "Ready"
            binding.btnAction.text = "Start"
            binding.btnAction.setIconResource(R.drawable.baseline_play_arrow_24)
            binding.btnStop.visibility = View.GONE
        }
    }



    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val seconds = totalSecs % 60
        val minutes = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
