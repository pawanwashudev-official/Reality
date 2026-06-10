1. **Remove Google Android Credential & Hardcoded Key Logic**
   - In `app/build.gradle.kts`:
     - Remove Android credential manager / identity dependencies if no longer used. Keep `google-api-client-android` and Google API service dependencies.
   - In `app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt`:
     - Remove `androidx.credentials` and `com.google.android.libraries.identity.googleid` imports and usage.
     - Remove `WEB_CLIENT_ID` usage via `BuildConfig`.
     - Implement the new flow:
       - Update `GoogleAuthManager` to store and manage desktop client ID and client secret (via SharedPreferences).
       - Create a function to generate the Google OAuth authorization URL (with `response_type=code`, `access_type=offline`, `prompt=consent`, and the necessary scopes).
       - Create a function to exchange the auth code for access & refresh tokens using Google's OAuth 2.0 token endpoint.
       - Create a function to get an authorized Google credential using the saved tokens (e.g., `GoogleCredential.Builder()`). Replace `GoogleAccountCredential`.
       - Update `isSignedIn`, `getUserEmail`, `getUserName`, `getUserPhotoUrl` to depend on token validity (and maybe fetch user info using the token).
     - Update all Google service managers (`GoogleDriveManager.kt`, `GoogleDocsManager.kt`, `GoogleSheetsManager.kt`, `GoogleTasksManager.kt`, `GoogleCalendarManager.kt`) to use the new token-based credential instead of `GoogleAccountCredential`.

2. **Update ProfileActivity UI**
   - In `app/src/main/res/layout/activity_profile.xml`:
     - Remove `btn_info_profile` (the info icon).
     - Add a settings icon (e.g., `btn_settings_cloud`) in place of or near the old info icon to open a Google Cloud Setup dialog.
   - In `app/src/main/java/com/neubofy/reality/ui/activity/ProfileActivity.kt`:
     - Add logic for the settings icon to show a dialog/bottom sheet where users can input their `Client ID` and `Client Secret`. Save these in `SharedPreferences`.
     - Update the Sign In logic:
       - Check if Client ID and Secret are configured. If not, prompt the user to set them up.
       - If configured, generate the OAuth URL using `GoogleAuthManager`.
       - Open the URL in an external browser or Custom Tab for the user to log in.
       - Prompt the user to paste the authorization code returned by Google (or intercept it via a redirect URL like `urn:ietf:wg:oauth:2.0:oob` or a local redirect if possible). *Using "copy from there till then ap should be awaiting for token and user back to app and paste that" implies OOB (out-of-band) or a manual copy-paste flow.*
       - Exchange the code for tokens and save them.
     - Update the profile UI to show "Ready to Login" state based on whether credentials are provided.

3. **Pre-commit and Test**
   - Run the pre-commit instructions and `./gradlew assembleDebug` to ensure compilation and verification of changes.
