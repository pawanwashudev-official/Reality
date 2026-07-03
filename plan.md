1.  **Create `IdentityManager.kt`:**
    *   Location: `app/src/main/java/com/neubofy/reality/utils/IdentityManager.kt`
    *   Functionality: Handle generation and fetching of `userId` and `backupPassword`.
    *   Network: POST to `BuildConfig.WORKER_URL + "/api/generate-identity"` with `{"email": "user@example.com"}`. Parse JSON response `{"userId": "...", "backupPassword": "..."}`.
    *   Storage: Save both to `EncryptedSharedPreferences` via `SecurePreferences.kt` (e.g., `reality_identity_prefs`).
    *   Method `getUserId(email)`: Retrieve from cache. If cache misses, synchronously network fetch (with coroutine blocking/runBlocking or appropriate handling, given the constraint "trigger a synchronous call") to recover.
    *   Method `getBackupPassword(email)`: Retrieve from cache.
    *   Method `clearIdentity()`: Remove keys from cache on logout.
    *   Method `refreshIdentity(email)`: Force network fetch to JIT refresh keys.

2.  **Integrate JIT Refresh Triggers:**
    *   **Login**: In `GoogleAuthManager.kt`, call `IdentityManager.refreshIdentity(email)` upon successful login/token exchange. Call `IdentityManager.clearIdentity()` on `signOut`.
    *   **Backup/Restore**: In `BackupManager.kt`, call `IdentityManager.refreshIdentity(email)` before backup/restore routines.
    *   **Reality Pro Launch**: In `RealityProActivity.kt`, call `IdentityManager.refreshIdentity(email)` in `onCreate`/`onResume`.
    *   **Reality Pro Payment**: In `PaymentVerificationActivity.kt` and `RealityProManager.kt` (or where activation occurs), call `refreshIdentity(email)` post-activation.

3.  **Rip Out MD5:**
    *   Delete `app/src/main/java/com/neubofy/reality/utils/MD5Utils.kt`.
    *   Search and replace `MD5Utils.getUserIdFromEmail(...)` with `IdentityManager.getUserId(...)` across the codebase (e.g., `FeatureManager.kt`, `RealityProActivity.kt`, `ProfileActivity.kt`, `PaymentVerificationActivity.kt`, `patch_feature_manager.js`).

4.  **Automated Backup Encryption:**
    *   **Remove UI**: Delete `app/src/main/java/com/neubofy/reality/ui/activity/EncryptionSetupActivity.kt`. Remove references to it in `SettingsActivity.kt` and `AndroidManifest.xml`.
    *   **Modify `BackupEncryption.kt`**: Change `getSecretKey()` to fetch `backupPassword` from `IdentityManager` instead of user prefs. Remove `getSecretKeyFromPassword` logic related to the plaintext fallback (`com.neubofy.reality.backup.key.v1`).
    *   **Modify `BackupManager.kt`**: Remove `password` parameter from `restoreBackup` and `decrypt` calls. It should automatically use `BackupEncryption.decrypt` which will fetch the password via `IdentityManager`.
    *   **Modify `BackupRestoreActivity.kt`**: Remove `promptForPassword` logic entirely. Make restore seamless.

5.  **Remove Client-Side Subscription Exploits (`FeatureManager.kt`):**
    *   Remove `getExternalTrialFile()` and logic interacting with `.reality_engine_sys_config`.
    *   Simplify `getTrialEndTime()`, `isTrialActive()`, `hasUsedTrial()`, `activateTrial()` to only use `SecurePreferences`.

6.  **Profile UI Expansion:**
    *   **XML (`activity_profile.xml`)**: Add a new `LinearLayout` block identical to `ll_user_id` but for `backupPassword` below `ll_user_id`. Add `tv_backup_password` and `btn_copy_backup_password`.
    *   **Kotlin (`ProfileActivity.kt`)**: Bind the new views. Fetch and display `backupPassword` from `IdentityManager`. Add copy functionality.

7.  **Documentation:**
    *   Update `README.md` / `ABOUT.md` to reflect deterministic server-side HMAC-SHA256 identity generation via Cloudflare proxy.

8.  **Pre-commit Instructions:**
    *   Run `pre_commit_instructions` and follow steps.
