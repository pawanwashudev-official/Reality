import re

with open("app/src/main/res/layout/activity_smart_sleep.xml", "r") as f:
    content = f.read()

new_alarm_card = """            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardAlarmSettings"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                app:cardCornerRadius="16dp"
                app:cardElevation="0dp"
                app:strokeWidth="1dp"
                app:strokeColor="?attr/colorOutlineVariant"
                app:cardBackgroundColor="?attr/colorSurfaceContainer">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/baseline_access_time_24"
                            app:tint="?attr/colorPrimary"/>

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:layout_marginStart="8dp"
                            android:text="Wake Up Alarms"
                            android:textSize="16sp"
                            android:textStyle="bold"
                            android:textColor="?attr/colorOnSurface"/>

                        <ImageButton
                            android:id="@+id/btnRecycleBin"
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:src="@drawable/baseline_delete_outline_24"
                            android:background="?attr/selectableItemBackgroundBorderless"
                            app:tint="?attr/colorOnSurfaceVariant"
                            android:contentDescription="Recycle Bin" />

                        <ImageButton
                            android:id="@+id/btnAddAlarm"
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:src="@drawable/baseline_add_24"
                            android:background="?attr/selectableItemBackgroundBorderless"
                            app:tint="?attr/colorPrimary"
                            android:contentDescription="Add Alarm" />
                    </LinearLayout>

                    <androidx.recyclerview.widget.RecyclerView
                        android:id="@+id/rvWakeupAlarms"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:nestedScrollingEnabled="false"
                        app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>"""

content = re.sub(r'<com\.google\.android\.material\.card\.MaterialCardView\n                android:id="\@\+id/cardAlarmSettings".*?</com\.google\.android\.material\.card\.MaterialCardView>', new_alarm_card, content, flags=re.DOTALL)

with open("app/src/main/res/layout/activity_smart_sleep.xml", "w") as f:
    f.write(content)
