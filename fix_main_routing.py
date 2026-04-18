import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "r") as f:
    content = f.read()

new_routing = """    private fun handleIntentAction(intent: Intent?) {
        val action = intent?.getStringExtra("action") ?: intent?.data?.host
        if (action == "sleep_verify" || action == "smart_sleep") {
            // User dismissed wake-up alarm or tapped notification - trigger smart sleep page
            startActivity(Intent(this, SmartSleepActivity::class.java))
        } else if (action == "wakeup_alarm") {
            val alarmIntent = Intent(this, SmartSleepActivity::class.java).apply {
                putExtra("action", "wakeup_alarm")
                putExtra("id", intent.getStringExtra("id"))
            }
            startActivity(alarmIntent)
        }
    }"""

content = content.replace("""    private fun handleIntentAction(intent: Intent?) {
        val action = intent?.getStringExtra("action") ?: intent?.data?.host
        if (action == "sleep_verify" || action == "smart_sleep") {
            // User dismissed wake-up alarm or tapped notification - trigger smart sleep page
            startActivity(Intent(this, SmartSleepActivity::class.java))
        }
    }""", new_routing)

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "w") as f:
    f.write(content)
