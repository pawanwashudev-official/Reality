with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "r") as f:
    content = f.read()

content = content.replace("val id = intent?.getStringExtra(\"id\") ?: return", """TerminalLogger.log("WAKEUP ALARM: Receiver Triggered!")
        val id = intent?.getStringExtra("id") ?: return""")

with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "w") as f:
    f.write(content)
