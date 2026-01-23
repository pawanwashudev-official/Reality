package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityAmoledFocusBinding
import com.neubofy.reality.services.TapasyaService
import kotlinx.coroutines.flow.collectLatest
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
            val state = TapasyaService.clockState.value
            if (state.isRunning) {
                sendServiceAction(TapasyaService.ACTION_PAUSE)
            } else if (state.isPaused) {
                sendServiceAction(TapasyaService.ACTION_RESUME)
            } else {
                // Determine logic for 'Start' if needed, but usually we enter this mode while running
                // If not running, typically we redirect or show start dialog. 
                // For simplified "Super Mode", we assume user starts in Tapasya and switches here, 
                // but let's handle start to be safe (default params)
                sendServiceAction(TapasyaService.ACTION_START) 
            }
        }

        binding.btnStop.setOnClickListener {
            sendServiceAction(TapasyaService.ACTION_STOP)
            finish() // Exit AMOLED mode on stop
        }
    }

    private fun observeClockState() {
        lifecycleScope.launch {
            TapasyaService.clockState.collectLatest { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: TapasyaService.ClockState) {
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

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, TapasyaService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val seconds = totalSecs % 60
        val minutes = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
