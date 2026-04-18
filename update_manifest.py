import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

new_components = """        <receiver
            android:name=".receivers.WakeupAlarmReceiver"
            android:exported="false" />
        <service android:name=".services.WakeupAlarmService" />
"""

content = content.replace("</application>", new_components + "\n    </application>")

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)
