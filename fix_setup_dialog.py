import re

with open("app/src/main/res/layout/dialog_wakeup_alarm_setup.xml", "r") as f:
    content = f.read()

new_fields = """
    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/tilSnoozeInterval"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
        app:layout_constraintTop_toBottomOf="@id/swVibration">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/etSnoozeInterval"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="number"
            android:hint="Snooze Interval (minutes)"
            android:text="3" />
    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/tilMaxAttempts"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
        app:layout_constraintTop_toBottomOf="@id/tilSnoozeInterval">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/etMaxAttempts"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="number"
            android:hint="Max Attempts"
            android:text="5" />
    </com.google.android.material.textfield.TextInputLayout>
"""

content = content.replace('app:layout_constraintTop_toBottomOf="@id/swVibration"', 'app:layout_constraintTop_toBottomOf="@id/tilMaxAttempts"')
content = content.replace("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="24dp"
        app:layout_constraintTop_toBottomOf="@id/tilMaxAttempts"
        app:layout_constraintBottom_toBottomOf="parent">""", new_fields + """
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="24dp"
        app:layout_constraintTop_toBottomOf="@id/tilMaxAttempts"
        app:layout_constraintBottom_toBottomOf="parent">""")

with open("app/src/main/res/layout/dialog_wakeup_alarm_setup.xml", "w") as f:
    f.write(content)
