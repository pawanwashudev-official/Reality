with open("app/src/main/res/layout/activity_main.xml", "r") as f:
    content = f.read()

# I want to add a settings icon to the emergency card.
# It currently has:
#                    <ImageView
#                        android:layout_width="24dp"
#                        android:layout_height="24dp"
#                        android:src="@drawable/baseline_arrow_forward_24"
#                        app:tint="?attr/colorError"/>
# Let's replace the arrow with the settings icon and attach an ID to it so we can click it independently.
# Actually, the entire card is clickable by `btn_emergency_click`. We need to bring the icon to the front so it captures clicks.

new_arrow = """                    <ImageButton
                        android:id="@+id/btn_emergency_settings"
                        android:layout_width="48dp"
                        android:layout_height="48dp"
                        android:src="@drawable/baseline_settings_24"
                        android:background="?attr/selectableItemBackgroundBorderless"
                        app:tint="?attr/colorError"
                        android:elevation="4dp"/>"""

content = content.replace("""                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:src="@drawable/baseline_arrow_forward_24"
                        app:tint="?attr/colorError"/>""", new_arrow)

with open("app/src/main/res/layout/activity_main.xml", "w") as f:
    f.write(content)
