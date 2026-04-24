with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("putExtra(\"id\", intent.getStringExtra(\"id\"))", "putExtra(\"id\", intent?.getStringExtra(\"id\"))")

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "w") as f:
    f.write(content)
