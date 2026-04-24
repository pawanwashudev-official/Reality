with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "r") as f:
    content = f.read()

new_intent = """
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    override fun onResume() {"""

content = content.replace("    override fun onResume() {", new_intent)

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "w") as f:
    f.write(content)
