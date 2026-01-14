# F-Droid Submission Readiness Guide

This repository has been audited and prepped for F-Droid submission.
Follow these steps to complete the submission process.

## 1. Compliance Audit (✅ Completed)
- **License:** GPLv3 detected (Fully Compliant).
- **Dependencies:** 
  - Removed unused Google Play Services references.
  - All current libraries (`Room`, `WorkManager`, `MPAndroidChart`, `TimeRangePicker`) are Open Source.
  - **No** Firebase, Crashlytics, or AdMob SDKs found.
- **Build System:** Standard Gradle build supported.

## 2. Metadata Structure (✅ Ready)
Your metadata is located in: `fastlane/metadata/android/en-US/`
F-Droid supports this Fastlane structure automatically.

**Action Required:**
Ensure the following text files exist in `fastlane/metadata/android/en-US/`:
- `title.txt` (App Name)
- `short_description.txt` (Max 80 chars)
- `full_description.txt` (Markdown supported)
- `images/icon.png` (512x512)
- `images/phoneScreenshots/` (At least 2 screenshots)

## 3. Submission Steps

### Option A: Fork & Pull Request (Recommended)
1. Fork the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository on GitLab.
2. Create a file `metadata/com.neubofy.reality.yml`.
3. Use the following template:

```yaml
Categories:
  - Productivity
  - Security
License: GPL-3.0-only
SourceCode: https://github.com/pawanwashudev-official/Reality
IssueTracker: https://github.com/pawanwashudev-official/Reality/issues
WebSite: https://neubofyreality.vercel.app/
Donate: https://buymeacoffee.com/yourlink

AutoName: Reality
Summary: Advanced open-source app blocker and digital wellbeing tool.
Description: |
  Reality helps you regain control of your time by blocking distracting apps and websites. 
  Features include Strict Mode (Anti-Uninstall), Schedule Blocking, and Statistics.

RepoType: git
Repo: https://github.com/pawanwashudev-official/Reality.git

Builds:
  - versionName: 1.0.0
    versionCode: 11
    commit: v1.0.0  # TAG THIS COMMIT IN YOUR REPO
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 11
```

### Option B: Tagging Your Release (Critical)
F-Droid builds from **Git Tags**.
1. Commit all recent changes.
2. Run:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   *Make sure the tag matches the `versionName` in `app/build.gradle.kts`.*

## 4. Final Verification
Before submitting, run this locally to ensure a clean build (simulating F-Droid):
```bash
# Windows
./gradlew clean assembleRelease
```
*Note: The build will produce an unsigned APK if `local.properties` is missing. This is EXPECTED for F-Droid (they sign it themselves).*
