import re

with open("app/src/main/res/layout/activity_smart_sleep.xml", "r") as f:
    content = f.read()

# Replace the TextButton "Setup" with an IconButton "Settings"
target_btn = """                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnSetupAlarm"
                            style="@style/Widget.Material3.Button.TextButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Setup" />"""

new_btn = """                        <ImageButton
                            android:id="@+id/btnSetupAlarm"
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:src="@drawable/baseline_settings_24"
                            android:background="?attr/selectableItemBackgroundBorderless"
                            app:tint="?attr/colorPrimary"
                            android:contentDescription="Setup Alarm" />"""

content = content.replace(target_btn, new_btn)

with open("app/src/main/res/layout/activity_smart_sleep.xml", "w") as f:
    f.write(content)
