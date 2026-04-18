with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

content = content.replace("private var snoozeInterval = 3", "private var snoozeInterval = 3\n    private var ringtoneUri: String? = null\n    private var vibrationEnabled = true")

content = content.replace("snoozeInterval = intent?.getIntExtra(\"snoozeInterval\", 3) ?: 3", """snoozeInterval = intent?.getIntExtra("snoozeInterval", 3) ?: 3
        ringtoneUri = intent?.getStringExtra("ringtoneUri")
        vibrationEnabled = intent?.getBooleanExtra("vibrationEnabled", true) ?: true""")

content = content.replace("val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)", """val alarmUri: Uri = if (ringtoneUri != null && ringtoneUri!!.isNotEmpty()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }""")

content = content.replace("""vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                vibrator?.vibrate(effect)
            } else {
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }""", """if (vibrationEnabled) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                    vibrator?.vibrate(effect)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
                }
            }""")

content = content.replace("WakeupAlarmScheduler.scheduleSnooze(this, id, alarmTitle ?: \"Wake Up\", maxAttempts, snoozeInterval)", "WakeupAlarmScheduler.scheduleSnooze(this, id, alarmTitle ?: \"Wake Up\", maxAttempts, snoozeInterval, ringtoneUri, vibrationEnabled)")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
