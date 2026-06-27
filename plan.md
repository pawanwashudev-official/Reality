1. **Remove Unused Permissions & Clean up ADB Setup**:
   - In `app/src/main/AndroidManifest.xml`, use `replace_with_git_merge_diff` to delete the `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" ... />` line and `AdbSetupActivity`.
   - Delete `AdbSetupActivity.kt` and `activity_adb_setup.xml` files.
   - Verify changes using `cat` on `AndroidManifest.xml` and `ls` to ensure files are deleted.

2. **Remove Onboarding Permissions Flow & Adjust Navigation**:
   - In `MainActivity.kt`:
      - Use `replace_with_git_merge_diff` to remove the intent that navigates to `OnboardingActivity` when `onboarding_v2_complete` is false.
      - Use `replace_with_git_merge_diff` to remove the `checkAndRequestNextPermission()` method and its call in `onResume()`.
   - In `SecurityIntroActivity.kt`:
      - Use `replace_with_git_merge_diff` to change the navigation intent from `OnboardingActivity` to `MainActivity` upon completing the intro.
   - Verify changes using `cat` on `MainActivity.kt` and `SecurityIntroActivity.kt`.
   - Delete the onboarding files: `OnboardingActivity.kt`, `OnboardingPermissionsFragment.kt`, `PermissionsFragment.kt`, `activity_onboarding.xml`, `fragment_onboarding_permissions.xml`, and `fragment_permissions.xml`.
   - Verify deletions with `ls`.

3. **Fix the View Pro Members Button in RealityProActivity**:
   - In `RealityProActivity.kt`, use `replace_with_git_merge_diff` to move `setContentView(R.layout.activity_reality_pro)` to execute *before* `findViewById(R.id.btn_view_pro_members)`.
   - Verify the fix using `cat RealityProActivity.kt`.

4. **Add Permission Manager to Settings**:
   - In `activity_settings.xml`, use `replace_with_git_merge_diff` to add a new MaterialCardView for "Permission Manager".
   - Create `PermissionManagerActivity.kt` and `activity_permission_manager.xml`.
   - The UI should dynamically display the permissions required by the app based on enabled features (Core: Accessibility, Usage, Overlay, Battery, Notifications; Optional based on features: Health Connect, Alarm, Device Admin, Calendar).
   - In `AndroidManifest.xml`, register `PermissionManagerActivity`.
   - Verify the creation and updates using `cat`.

5. **On-Demand Permission Prompts on Feature Page Opens**:
   - Create a `PermissionHelper.kt` utility with a `checkAndPromptFor` method.
   - Use `replace_with_git_merge_diff` to call this utility in `onResume` of key activities: `AppLimitsActivity.kt`, `UnifiedBlocklistActivity.kt`, `ScheduleListActivity.kt`, `TapasyaActivity.kt` and others that clearly need permissions.
   - Verify the changes using `cat` on the modified files.

6. **Tests**:
   - Run a gradle build using `./gradlew assembleDebug` to verify compilation and functionality.

7. **Pre Commit Steps**:
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

8. **Submit**:
   - Call the `submit` tool to finalize the task.
