1. **Remove Usage Statistics Graph**:
   - In `app/src/main/res/layout/activity_statistics.xml`, remove the chart elements (the whole Chart Card layout).
   - In `app/src/main/java/com/neubofy/reality/ui/activity/StatisticsActivity.kt`, remove the `setupChart` method, imports related to the graph (`BarChart`, etc.), and calls to the `setupChart` method.
2. **Rename Text on Main Page Cards**:
   - In `app/src/main/res/layout/activity_main.xml`, rename:
     - `distracting App` -> `Distracting App`
     - `App Constraints` -> `App usage limit`
     - `Category Limits` -> `Grouped App limit`
     - `Sleep Mode` -> `Bedtime Time`
   - In `app/src/main/res/layout/item_blocklist_app_expandable.xml` and `app/src/main/res/layout/item_website_expandable.xml`, rename:
     - `Auto Focus (Schedules)` -> `Schedule Focus (Schedules)` (Wait, user says Auto focus > Shchdule Focus. Let's make it `Schedule Focus (Schedules)` or just `Schedule Focus`. Actually the text was `Auto Focus (Schedules)`, we can change it to `Schedule Focus (Schedules)`).
3. **Extend Strict Mode Timer Limit**:
   - In `app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt`, change `daysPicker.maxValue = 20` to `daysPicker.maxValue = 365`.
   - In `app/src/main/res/layout/dialog_timer_picker.xml` and `app/src/main/res/layout/dialog_timer_setup.xml`, change the max instruction text to `Max: 365 days, 23 hours`, and add a clear instruction about how the user will be locked (warning text).
4. **Pre-commit Steps**:
   - Complete pre-commit tests to make sure proper testing, verifications, reviews and reflections are done.
5. **Submit Change**:
   - Submit the change with descriptive commit title and message.
