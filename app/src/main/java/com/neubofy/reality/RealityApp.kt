package com.neubofy.reality

import android.app.Application
import com.google.android.material.color.DynamicColors

class RealityApp: Application() {
  override fun onCreate() {
    DynamicColors.applyToActivitiesIfAvailable(this)
    Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    super.onCreate()
  }
}
