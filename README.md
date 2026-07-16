<div align="center">

<img src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png" width="180" alt="Reality Logo" style="border-radius: 20px; margin-bottom: 20px;">

# Reality
### The Intelligent Life OS

**Developed by Pawan Washudev | Neubofy**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/pawanwashudev-official/Reality?style=for-the-badge&color=orange)](https://github.com/pawanwashudev-official/Reality/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/pawanwashudev-official/Reality/total?style=for-the-badge&color=success)](https://github.com/pawanwashudev-official/Reality/releases)
[![License](https://img.shields.io/badge/License-Custom-blue.svg?style=for-the-badge)](#%EF%B8%8F-legal--license)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=for-the-badge)](https://www.android.com)
[![Privacy](https://img.shields.io/badge/Privacy-First-teal.svg?style=for-the-badge)]()
[![AI](https://img.shields.io/badge/AI-Deployed%20on%20Own%20server-purple.svg?style=for-the-badge)]()
[![Ads](https://img.shields.io/badge/Ads-ZERO-red.svg?style=for-the-badge)]()

> **"Stop managing your life. Start commanding it."**

### 🌟 99.9% Source-Available • No Ads • Privacy-First • Privacy-Preserving Hosted AI

<p>While advanced features (like Neural Protocol, Gamification, and Google Workspace Sync) require a yearly Reality Elite Member subscription to support ongoing maintenance, the app remains 99.9% source-available. However, building custom APKs for distribution or cloning is strictly prohibited, and the codebase is strictly source-available for review only.</p>

[**🌐 Official Website**](https://reality.neubofy.in) • [**⬇️ Download Latest APK**](https://reality.neubofy.in/download)

</div>

---

## 🎯 EXECUTIVE SUMMARY

**Reality** is a military-grade productivity operating system designed to eliminate digital distractions and act as a relentless partner in achieving your goals. It is not just an app blocker; it is an intelligent, completely integrated local-first system that merges task management, calendar sync, screen-time control, sleep tracking, and agentic AI.

---

## 🏗️ SYSTEM REQUIREMENTS & SETUP

### Device Requirements
* **RAM**: 256MB minimum (typical footprint runs around 50–100MB).
* **Storage**: 150MB for installation and SQLite database space.
* **Battery**: Less than 1% battery drain due to optimized, native accessibility hooks.
* **Connectivity**: Core features are completely offline; Google Sync and AI assistant tasks require network access.

### Setup Instructions
1. **Initial Security Intro**: Proceed through the onboarding cards in [SecurityIntroActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/SecurityIntroActivity.kt).
2. **Grant Core Android Permissions**: Run through the permission checks in [PermissionManagerActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/PermissionManagerActivity.kt) to configure:
   * **Accessibility Service** (for real-time window tracking and blocker overlays).
   * **System Alert Window** (to render custom lockscreens over restricted apps).
   * **Usage Statistics** (to calculate focus grades and screen time trends).
3. **Configure the App Blocklist**: Select and group apps you want to restrict in [UnifiedBlocklistActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/UnifiedBlocklistActivity.kt) or [SelectAppsActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/SelectAppsActivity.kt).
4. **Set Up Google Workspace Sync (Optional)**: Input your Google OAuth credentials in the Settings panel ([GoogleAuthManager.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt)) to link Drive, Calendar, Docs, and Tasks directly.

---

## 🏆 CORE FEATURES DEEP-DIVE

### 1. **🚫 App Blocker & Strict Mode**
Reality operates a zero-tamper blocking system driven by Android's native APIs, ensuring you cannot simply disable focus features mid-session.
* **Under the Hood**: Window transitions are monitored inside [AppBlockerService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AppBlockerService.kt). Each window change checks app state against configuration logic in [RealityBlocker.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/blockers/RealityBlocker.kt).
* **Bypass Prevention features**:
  * **Device Admin Enforcement**: Prevents uninstallation by locking device admin permissions in [StrictModeActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt).
  * **Anti-Tamper Checking**: Flags clock changes and timezone manipulation to prevent bypassing schedules.
  * **System Settings Blocking**: Intercepts settings submenus to disable force-stop shortcuts.
  * **Ratchet Cooldowns**: Locks the configuration interface behind custom time penalties or verification checks if you attempt to override rules.

### 2. **⚡ Tapasya (Neural Focus Timer)**
Tapasya enforces deep, uninterrupted focus blocks instead of acting as a simple, passive timer.
* **Under the Hood**: Managed via [TapasyaService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaService.kt) and [TapasyaManager.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaManager.kt). Enforces a full-screen Amoled focus theme [AmoledFocusActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AmoledFocusActivity.kt) to limit visual clutter.
* **Discipline Rules**:
  * Tracks focus sessions in strict 15-minute chunks ("Effective Time").
  * Distractions (opening unapproved apps or exiting early) immediately trigger session penalties and void progress.
  * Generates and reads secure, encrypted QR codes ([QRScannerActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/QRScannerActivity.kt)) to sync session states externally.

### 3. **🌙 The Nightly Protocol (6 Unified Steps)**
An automated evening reflection and planning workflow run via WorkManager in [NightlyWorker.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/workers/NightlyWorker.kt) and coordinated in [NightlyActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/NightlyActivity.kt).
* **Step 1: Fetch Analytics**: Gathers daily app usage, calendar logs, and fitness metrics.
* **Step 2: Create Diary**: AI reads metrics and asks personalized reflection questions ([NightlyPromptsActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/NightlyPromptsActivity.kt)).
* **Step 3: Save Today Analytics**: Grades your evening responses, computes daily scores, and awards XP.
* **Step 4: Create Plan**: Autonomously drafts tomorrow's plan layout inside Google Drive.
* **Step 5: Apply Plan**: Parses your plan and maps it to Google Tasks, Calendar events, and morning alarms.
* **Step 6: Report & Finalize**: Generates a professional progress report PDF and logs data to Google Sheets.

### 4. **🛌 Bedtime & Sleep Tracking**
A local Sleep and Bedtime manager ([SmartSleepActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt) / [BedtimeActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/BedtimeActivity.kt)) designed to optimize circadian alignment.
* **Under the Hood**: Uses [HealthManager.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/health/HealthManager.kt) to integrate natively with Android Health Connect.
* **Integration**: Pulls daily sleep metrics, active energy burn, and steps to evaluate your biological readiness without using external cloud wearables. A Quick Settings tile ([RealitySleepTileService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/RealitySleepTileService.kt)) enables starting bedtime routines with a single swipe.

### 5. **🔔 Math-Based Wakeup Alarms**
Wakeup Alarms designed to prevent snoozing and sleep-inertia bypasses.
* **Under the Hood**: Alarms are scheduled by [AlarmService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AlarmService.kt) and triggered via [WakeupAlarmService.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt).
* **Anti-Oversleep Logic**:
  * Renders a math problem layout in [WakeupAlarmRingingActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/WakeupAlarmRingingActivity.kt).
  * Auto-scales problem difficulty depending on how early the alarm rings (earlier alarms produce complex arithmetic).
  * Automatically handles snooze restrictions and overrides standard volume buttons.

### 6. **🤖 Reality Intelligence Assistant (Jarvis-Like AI)**
Reality integrates a local-first agent designed for in-app command execution and private support, rather than a generic text-generation bot.
* **Under the Hood**: Runs within [AIChatActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AIChatActivity.kt) and [PopupAIChatActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/PopupAIChatActivity.kt). Initial system configuration and prompt customization are managed in [AISettingsActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AISettingsActivity.kt).
* **Capabilities**:
  * Utilizes Model Context Protocol (MCP) tool registrations defined in [ToolRegistry.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/utils/ToolRegistry.kt) to execute actions like alarm changes, task additions, and app blocks.
  * Leverages a local context sliding window using [ConversationMemoryManager.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/utils/ConversationMemoryManager.kt) to count tokens locally.
  * Connects to private, secure edge servers ([workers/identity/worker.js](https://github.com/pawanwashudev-official/Reality/blob/main/workers/identity/worker.js)) with BYOK (Bring-Your-Own-Key) support.

### 7. **🎨 Cinematic Theme Customization**
A fully customized UI rendering module allows personalizing fonts, layouts, and gradients to make your Life OS look premium.
* **Under the Hood**: Managed inside [AppearanceActivity.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt), customizing active styling maps, dark amoled mode parameters, and dynamic font scales.

---

## 🔒 ZERO-TRUST SECURITY & DATA OWNERSHIP

### Bring Your Own Cloud (BYOC) Setup Guide
Reality works without developer servers. You can host your own sync endpoints using a Google Cloud Console project:
1. **Enable Google APIs**: Go to the Google Cloud Console and activate the **Calendar API**, **Drive API**, **Tasks API**, and **Docs API**.
2. **OAuth Consent Screen**: Add your email as a test user and add scopes for Tasks, Drive, Calendar, and Docs.
3. **Generate OAuth Credentials**: Go to `Credentials` -> `Create Credentials` -> `OAuth Client ID`. Select **Desktop application** as the application type, name your client, and generate the credentials.
4. **Link inside Reality**: Paste your Client ID and Client Secret in [GoogleAuthManager.kt](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt) / Google setup panel. Reality will execute direct on-device OAuth token updates with Google.

### Secure Identity & Encryption
* Encrypted database storage is implemented via [Room](https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/data/) and Android's native `EncryptedSharedPreferences`.
* Backups and keys are secured deterministically using JIT calculations at the Cloudflare edge ([workers/identity/worker.js](https://github.com/pawanwashudev-official/Reality/blob/main/workers/identity/worker.js)) to safeguard keys against physical client-side memory extraction.

---

## 🏗️ DEEP TECHNICAL ARCHITECTURE

### Technology Stack
```
Platform:       Android 8.0+ (API 26 to 36)
Language:       Kotlin 100% (type-safe)
UI Framework:   AndroidX + Material3
Database:       Room ORM + SQLite
Threading:      Coroutines (Kotlin Flow)
Networking:     OkHttp + Retrofit (Google APIs)
Background:     WorkManager + AlarmManager
Logging:        Terminal Logger (custom)
Parsing:        GSON, JSoup, Markwon
```

### Key Libraries
| Category | Library | Version | Purpose |
|----------|---------|---------|---------|
| **Google APIs** | google-api-client-android | 2.2.0 | OAuth2 + service calls |
| | Tasks API | v1-rev20210709 | Task management |
| | Calendar API | v3-rev20231123 | Event scheduling |
| | Docs API | v1-rev20230929 | Document creation |
| | Drive API | v3-rev20230520 | File management |
| | Sheets API | v4-rev20230815 | Data logging |
| **Health** | Health Connect Client | 1.1.0-alpha07 | Fitness tracking |
| **Database** | Room | 2.5.2 | Local persistence |
| **Background** | WorkManager | 2.8.1 | Scheduled tasks |
| **UI** | Material3 | Latest | Design system |
| **Markdown** | Markwon | 4.6.2 | Content rendering |
| **JSoup** | JSoup | 1.17.2 | HTML parsing |

---

## 📞 SUPPORT & COMMUNITY

- **GitHub**: https://github.com/pawanwashudev-official/Reality
- **Website**: https://reality.neubofy.in
- **Email**: support@neubofy.in
- **Telegram / WhatsApp / Instagram / LinkedIn**: @pawanwashudev
- **Issues**: Report bugs on GitHub
- **Contributing**: PRs welcome for features/improvements

---

## ⚖️ Legal & License

This application is strictly source-available for review purposes only. We do not allow anyone to clone this repository, modify the app, or build and distribute their own version. It is strictly prohibited to make your own version of the app or to claim ownership. App stores or individuals are allowed to distribute the exact pre-compiled APK obtained directly from our GitHub release page. Our AI crawlers continuously scan the internet. If unauthorized distribution or cloning is detected, strict legal action will be taken.
