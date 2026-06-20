#!/bin/bash
sed -i '/popup.menu.add(0, 1, 5, "🌐 Reality Website")/a \
            popup.menu.add(0, 8, 6, "😴 Sleep & Alarms")' ./app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt

sed -i '/5 -> {/i \
                    8 -> {\n                        startActivity(Intent(this, SmartSleepActivity::class.java))\n                        true\n                    }' ./app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt
