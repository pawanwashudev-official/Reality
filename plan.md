1. **Create Secure Token Utility:**
   - Create `website/src/lib/tokenCookie.ts` for AES-GCM encryption/decryption of tokens using Web Crypto API. Provide `setTokenCookie`, `getTokenCookie`, `clearTokenCookie`, `setStateCookie`, `getStateCookie`, and `clearStateCookie` functions.
   - Run verification (`cat website/src/lib/tokenCookie.ts`) to ensure the file is created correctly.

2. **Update OAuth Start Route:**
   - Modify `website/src/app/api/auth/google/route.ts` to generate a random state, set it as a short-lived HttpOnly cookie using `tokenCookie.ts`, and pass it in the Google OAuth URL.
   - Run verification (`cat website/src/app/api/auth/google/route.ts`) to ensure the file was updated.

3. **Update OAuth Callback Route:**
   - Modify `website/src/app/api/auth/callback/google/route.ts` to verify the state cookie, exchange the code for tokens, save the encrypted token in an HttpOnly cookie using `tokenCookie.ts`, clear the state cookie, and redirect to `/tapashya?connected=1` without tokens in the URL.
   - Run verification (`cat website/src/app/api/auth/callback/google/route.ts`) to ensure the file was updated.

4. **Update Frontend Token Handling (Tapasya):**
   - Modify `website/src/app/tapashya/page.tsx` to remove `localStorage` and URL hash token parsing. Fetch calendar events by calling the new internal API `/api/calendar/events`. Update UI state for disconnected/connected.
   - Run verification (`cat website/src/app/tapashya/page.tsx`) to ensure the file was updated.

5. **Create Calendar Events API:**
   - Create `website/src/app/api/calendar/events/route.ts` to read the encrypted cookie, refresh the token if needed using Google API, fetch primary calendar events for today, and return sanitized data (id, title, start, end).
   - Run verification (`cat website/src/app/api/calendar/events/route.ts`) to ensure the file was created.

6. **Create Logout Route:**
   - Create `website/src/app/api/auth/logout/route.ts` to clear the token cookie.
   - Run verification (`cat website/src/app/api/auth/logout/route.ts`) to ensure the file was created.

7. **Delete Refresh Route:**
   - Delete `website/src/app/api/auth/refresh/route.ts`.
   - Run verification (`ls website/src/app/api/auth/refresh`) to ensure deletion.

8. **Update Website Security Headers:**
   - Modify `website/next.config.mjs` to add security headers (`X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`, `Permissions-Policy`).
   - Run verification (`cat website/next.config.mjs`) to ensure update.

9. **Update Website Privacy Policy:**
   - Modify `website/src/app/privacypolicy/page.tsx` to reflect local-first Vercel serverless reality, explicit cookie usage, and AI provider data flow. Update date to May 19, 2024 (current date approx).
   - Run verification (`cat website/src/app/privacypolicy/page.tsx`) to ensure files are updated.

10. **Update Website Terms of Service:**
    - Modify `website/src/app/termsofservice/page.tsx` to reflect the updated architecture.
    - Run verification (`cat website/src/app/termsofservice/page.tsx`) to ensure files are updated.

11. **Android Security Fixes - AndroidManifest:**
    - Modify `app/src/main/AndroidManifest.xml` to set `android:allowBackup="false"` and `android:fullBackupContent="false"`. Add data extraction rules metadata if target SDK requires.
    - Run verification (`cat app/src/main/AndroidManifest.xml`) to ensure update.

12. **Android Security Fixes - Extraction Rules:**
    - Create `app/src/main/res/xml/data_extraction_rules.xml` excluding sensitive paths.
    - Run verification (`cat app/src/main/res/xml/data_extraction_rules.xml`) to ensure file was created.

13. **Android Security Fixes - Password Hashing:**
    - Modify `app/src/main/java/com/neubofy/reality/utils/SecurityUtils.kt` to use PBKDF2WithHmacSHA256. Add backward compatibility for existing 64-char SHA-256 hashes.
    - Run verification (`cat app/src/main/java/com/neubofy/reality/utils/SecurityUtils.kt`) to ensure update.

14. **Android Security Fixes - SecurePrefs (AI 1):**
    - Modify `app/src/main/java/com/neubofy/reality/ui/activity/AISettingsActivity.kt` and `app/src/main/java/com/neubofy/reality/ui/activity/AIChatActivity.kt` to use `SecurePreferences.get(context, "ai_prefs")`.
    - Run verification (`cat app/src/main/java/com/neubofy/reality/ui/activity/AISettingsActivity.kt` and `cat app/src/main/java/com/neubofy/reality/ui/activity/AIChatActivity.kt`) to ensure files are updated.

15. **Android Security Fixes - SecurePrefs (AI 2):**
    - Modify `app/src/main/java/com/neubofy/reality/utils/AgentTools.kt`, `app/src/main/java/com/neubofy/reality/utils/ToolRegistry.kt`, `app/src/main/java/com/neubofy/reality/widget/AIChatWidget.kt`, `app/src/main/java/com/neubofy/reality/ui/activity/NightlyActivity.kt` to use `SecurePreferences.get(context, "ai_prefs")`.
    - Run verification on updated files using `cat`.

16. **Android Security Fixes - SecurePrefs (GoogleAuthManager):**
    - Modify `app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt` to use `SecurePreferences` for "google_connector_prefs".
    - Run verification (`cat app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt`) to ensure update.

17. **Android Security Fixes - TerminalLogger Redaction:**
    - Modify `app/src/main/java/com/neubofy/reality/utils/TerminalLogger.kt` to redact sensitive patterns (Bearer tokens, AI keys, passwords) before logging.
    - Run verification (`cat app/src/main/java/com/neubofy/reality/utils/TerminalLogger.kt`) to ensure file is updated.

18. **Android Security Fixes - CrashLogger Redaction:**
    - Modify `app/src/main/java/com/neubofy/reality/CrashLogger.kt` to redact sensitive patterns before writing to file.
    - Run verification (`cat app/src/main/java/com/neubofy/reality/CrashLogger.kt`) to ensure file is updated.

19. **Android Security Fixes - AI Privacy UI:**
    - Modify `app/src/main/res/layout/activity_ai_settings.xml` to add a privacy note regarding AI data access.
    - Run verification (`cat app/src/main/res/layout/activity_ai_settings.xml`) to ensure file is updated.

20. **Pre-commit Steps:**
    - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.

21. **Final Validation:**
    - Run Vercel build (`cd website && npm run build`) and linting (`cd website && npm run lint`).
    - Run `cd website && npm audit`.
    - Run Android debug build (`./gradlew assembleDebug`).
    - Run automated tests (`cd website && npm run test` if exists, and Android `./gradlew test`).
