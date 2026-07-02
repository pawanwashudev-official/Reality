1. **Update `UpdateManager.kt`:**
   - Change `GITHUB_API_URL` to `https://api.github.com/repos/pawanwashudev-official/Reality/releases` to get all releases (needed to find pre-releases).
   - Refactor `fetchGitHubRelease` to take a boolean `isBeta` parameter, and search the array of releases for the first one that matches the flag (i.e. `prerelease == true` for beta, `prerelease == false` for stable).
   - Refactor `checkForUpdates(context, silent, isBeta, onNoUpdate)` so it passes `isBeta` through.

2. **Update App Blocker in `AppBlockerService.kt`:**
   - In `handleBlock`, add a call to `pressHome()` unconditionally before starting the `BlockActivity`. This pushes the blocked app to the background.
   - In `checkUrl`, after deciding to block the website, add a call to `performGlobalAction(GLOBAL_ACTION_HOME)` before starting `BlockActivity`.

3. **Update `activity_about.xml`:**
   - Duplicate `cardUpdate` to create `cardBetaUpdate` (Check for Beta Updates).
   - Duplicate `cardUpdate` (or contact card) to create `cardRaiseIssue` (Report an Issue).

4. **Update `AboutActivity.kt`:**
   - Add click listener for `cardBetaUpdate` to call `UpdateManager.checkForUpdates(this, false, true, ...)`.
   - Add click listener for `cardRaiseIssue` to show a custom MaterialAlertDialogBuilder with two EditTexts (Title, Description). On submit, encode the title and body, and start an Intent for `Intent.ACTION_VIEW` pointing to `https://github.com/pawanwashudev-official/Reality/issues/new?title=...&body=...`.

5. **Update `ABOUT.md`:**
   - Append a section under "Support & Issues" or update the page content explaining how to raise an issue in the repo, along with providing a direct link.

6. **Pre Commit Verification:**
   - Ensure `gradle assembleDebug` succeeds.
   - Use `pre_commit_instructions` tool to verify.
