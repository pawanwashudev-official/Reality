1. *Fix Smart Sleep Icon Visibility on Health Page in Light Mode.*
   - In `activity_health_dashboard.xml`, the Smart Sleep icon inside the monitoring card uses `app:tint="?attr/colorOnTertiary"` with a circle background tinted by `?attr/colorTertiary`. Since `colorOnTertiary` is `#FFFFFF` in light mode and `colorTertiary` is a visible color, the issue might be that it's too bright or `colorOnTertiary` fails. I will change `backgroundTint` to `?attr/colorTertiaryContainer` and `app:tint` to `?attr/colorOnTertiaryContainer` which is standard Material 3 pattern and works in both modes.

2. *Add Option to Open Sleep & Alarm Page from Home Page.*
   - In `MainActivity.kt`, update `btnInfo.setOnClickListener` to add a new menu item for "😴 Sleep & Alarms" (e.g. ID 8).
   - In `setOnMenuItemClickListener`, handle `8` by launching `SmartSleepActivity`.

3. *Change QR Code Lock from Sleep Page Open to Alarm Dismiss.*
   - In `SmartSleepActivity.kt`, remove the `!isUnlockedThisSession` check and QR Scanner launch from `onCreate`. Make the page accessible directly.
   - When launching math dismiss logic in `SmartSleepActivity`, modify `btnDismiss.setOnClickListener` to check the math answer. If correct, launch `qrScannerLauncher`.
   - The `qrScannerLauncher` will stop the service, dismiss the alarm, and sync sleep when `RESULT_OK` is returned, moving the current `btnDismiss` logic to the `qrScannerLauncher` callback.

4. *Create Different QR Scanner Mode for Alarm Dismiss.*
   - In `SmartSleepActivity.kt`, pass `intent.putExtra("purpose", "alarm_dismiss")` to `QRScannerActivity`.
   - In `QRScannerActivity.kt` and `activity_qr_scanner.xml`, check `intent.getStringExtra("purpose")`.
   - If `"alarm_dismiss"`, update `tvScanHint` to "Scan QR to dismiss alarm" (instead of "Scan Tapashya web page QR...").
   - Also, the validation in `QRScannerActivity.kt` should just return `RESULT_OK` if it reads the standard "reality://smart_sleep" key, else error.

5. *Move QR Save/Share to Sleep Page with a Proper Long Card.*
   - Remove "Get QR Access Key" action from `HealthDashboardActivity.kt` and `menu_health_dashboard.xml`.
   - Remove `btnShareScan` from `activity_qr_scanner.xml` and `QRScannerActivity.kt` because the user wants it on the Sleep page.
   - Add a long card to `activity_smart_sleep.xml` (or `item_recycled_alarm` if needed, but the main XML is better) with text: "Save this QR in your phone and take print out because it need to scan to dismiss any alarm".
   - Include "Save QR" and "Share QR" buttons in this new card in `SmartSleepActivity.kt` that trigger the save/share code that was previously in `HealthDashboardActivity.kt`.

6. *Redesign AI Tooling (MCP).*
   - The user requested that AI tools should be grouped as MCPs. Instead of providing all tools at once, AI receives a list of MCPs. Once it selects an MCP, it receives the tools inside it.
   - In `NightlyAIHelper.kt` (or similar file defining tools), restructure the tools JSON schema. Create tool `use_mcp(mcp_name: string)` that responds with the tools of that MCP. Wait, the AI might need this automatically injected into the chat context. I will update `AIChatActivity.kt` and `NightlyAIHelper.kt` to handle MCP categorization.
   - Specifically, implement tools `set_alarm`, `list_alarms`, `edit_alarm` in an MCP named "mcp_alarms".
   - I will inspect `NightlyAIHelper.kt` to see how tools are currently defined.

7. *Complete pre commit steps.*
   - Complete pre commit steps to ensure proper testing, verifications, review and reflections are done before submit.

8. *Submit.*
   - Submit the change with an appropriate title and description.
