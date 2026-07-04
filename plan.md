1. **Update `GoogleAuthManager` and `IdentityManager`**
   - In `GoogleAuthManager`, parse `id_token` from both the custom client response (`tokenResponse.idToken`) and the proxy response (`json.optString("id_token")`).
   - Save the `id_token` in `SharedPreferences` (add `KEY_ID_TOKEN = "id_token"`).
   - Add a method `getIdToken(context)` to retrieve it.
   - In `IdentityManager`, change `generateAndCacheIdentity` to fetch the `idToken` instead of `email`. Change the POST payload to send `{"idToken": "..."}` instead of `email` to `/api/generate-identity`.

2. **Update `worker.js` (Cloudflare Worker)**
   - **`/api/generate-identity`**: Stop expecting `email`. Expect `idToken`. Use `fetch("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)` to verify it and extract the `email` field from the response. Proceed with the existing deterministic hashing to create `userId` and `backupPassword`.
   - **Verification Helper**: Create a helper function `verifyAuth(request)` that extracts `userId` and `password` from JSON body or query params. It hashes the `userId` with `APP_SECRET_PEPPER` to compute the expected password and checks if it exactly matches the provided `password`.
   - **`/license` route update**:
     - *Register (Step 1)*: Add a POST flow for `{"status": "P", ...}` which registers the user in DB (now "licenses" or "Reality Elite members management", although D1 query currently uses "licenses", we'll verify this). It inserts with `userId`, current date, and status `P`. Verify auth using helper.
     - *Purchase (Step 2)*: Modify POST flow to accept `userId`, `password`, `transactionId`, and `durationDays`. Verify auth. Calculate `expiryDate` string, set status to `V`, set `transactionId`, set `expiryDate`.
     - *Verify (Step 3)*: Modify GET flow. Remove `vCode`. Accept `userId` and `password`. Verify auth. Look up `userId` in `licenses` DB. If status is `V`, return `SUCCESS` with `expiryDate`.

3. **Rename & Refactor `RealityProActivity` to `RealityEliteActivity`**
   - Rename file `RealityProActivity.kt` to `RealityEliteActivity.kt` and `activity_reality_pro.xml` to `activity_reality_elite.xml`.
   - Update `AndroidManifest.xml` and all explicit intents calling it.
   - In `activity_reality_elite.xml`, add the new "Register (Step 1)" button for Elite membership, and update the UI texts from "Pro" to "Elite Member". Keep 3 cards: Sign In, Trial Experience, Become Elite Member (Register, Buy, Verify).
   - Modify logic in `RealityEliteActivity` to support:
     - Register: POSTs to worker to insert status 'P'.
     - Buy Subscription (`btnPayUpi`): Stays active while Google Play/Stripe greyed out. Successful payment in `PaymentVerificationActivity` triggers the purchase POST with `durationDays`.
     - Verify (`btnVerify`): Calls `worker.js` `/license` GET with `userId` and `password`. Updates `FeatureManager` if successful.
   - Remove usage of `vCode` everywhere (including `RealityProManager` and `PaymentVerificationActivity`).

4. **Update AI Chat (`AIChatActivity.kt` and `AISettingsActivity.kt`)**
   - Remove multiple provider options. Hardcode to our own deployed model (provider: "Neubofy", model: "gpt oss 20 b" or similar).
   - For all requests to the AI worker, include `userId` and `backupPassword` in the payload (which will be verified similarly by the AI worker, though current task focuses on Reality Elite).
   - Change "tavily" to be the specific search provider and remove image generation options.

5. **Complete pre commit steps**
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

6. **Submit changes**
   - Once tested locally (with mocked inputs if necessary), commit and submit changes.
