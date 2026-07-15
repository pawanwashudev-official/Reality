# About Reality

Reality is an open, military-grade productivity operating system for Android, designed to eliminate digital distractions and enforce discipline. Rather than acting as a standard, easily-bypassed app blocker, Reality utilizes Android's native `AccessibilityService` ([AppBlockerService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AppBlockerService.kt)) alongside device administration ([StrictModeActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt)) to construct an impenetrable **Strict Mode** that blocks uninstallation, system settings tampering, and clock manipulation. 

Its ecosystem integrates:
- **Tapasya (Neural Focus)** ([TapasyaService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaService.kt)) for deep, distraction-free work blocks.
- **Nightly Protocol** ([NightlyWorker.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/workers/NightlyWorker.kt)) for structured evening planning, Google Workspace sync, and daily report generation.
- **Wakeup Alarms** ([WakeupAlarmService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt)) that scale math difficulty based on morning hours to prevent oversleeping.
- **Reality Intelligence Assistant** ([AIChatActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AIChatActivity.kt)), a local-first, Jarvis-like agent for secure, private in-app automation.

Everything is processed on-device, preserving total user privacy.
