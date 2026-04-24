with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

content = content.replace("<service android:name=\".services.WakeupAlarmService\" />", "<service android:name=\".services.WakeupAlarmService\" android:foregroundServiceType=\"mediaPlayback\" />")

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)
