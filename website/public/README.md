<div align="center">

<img src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png" width="180" alt="Reality Logo" style="border-radius: 20px; margin-bottom: 20px;">

# Reality
### The Intelligent Life OS

**Developed by Pawan Washudev | Neubofy**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=for-the-badge)](https://www.android.com)
[![Privacy](https://img.shields.io/badge/Privacy-First-teal.svg?style=for-the-badge)]()
[![AI](https://img.shields.io/badge/AI-Deployed%20on%20Own%20server-purple.svg?style=for-the-badge)]()
[![Ads](https://img.shields.io/badge/Ads-ZERO-red.svg?style=for-the-badge)]()

> **"Stop managing your life. Start commanding it."**

### 🌟 99.9% Source-Available • No Ads • Privacy-First • Privacy-Preserving Hosted AI

<p>While advanced features (like Neural Protocol, Gamification, and Google Workspace Sync) require a yearly Reality Elite Member subscription to support ongoing maintenance, the app remains 99.9% source-available. However, building custom APKs for distribution or cloning is strictly prohibited, and the codebase is strictly source-available for review only.</p>

[**🌐 Official Website (Architecture & Details)**](https://reality.neubofy.in/) • [**⬇️ Download Best Version**](https://reality.neubofy.in/download)

[**🔒 Privacy Policy**](https://reality.neubofy.in/privacypolicy) • [**📜 Terms of Service**](https://reality.neubofy.in/termsofservice) • [**👑 Elite Members**](https://reality.neubofy.in/promembers)

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

### Complete App Setup Instructions
1. **Initial Security Intro**: Proceed through the onboarding cards when you first open the app to understand Reality's strict philosophy.
2. **Grant Core Android Permissions**: The app requires:
   * **Accessibility Service**: To monitor window changes for real-time app blocking without battery drain.
   * **System Alert Window**: To render the block screen over restricted apps.
   * **Usage Statistics**: To track daily focus grades and metrics.
3. **Configure the App Blocklist**: Go to "App Constraints" or "Grouped App limit" in settings. Select the apps you want to restrict or group them by category.
4. **Google Workspace Sync (Calendar, Tasks, Sheets) & Own Key Setup**:
   Reality syncs directly with your own Google Cloud to preserve privacy.
   * Go to **Google Cloud Console** online and create a new project.
   * Enable the **Calendar API**, **Drive API**, **Tasks API**, and **Docs API**.
   * Create an **OAuth 2.0 Client ID** for a **Desktop application**.
   * In Reality, open the **Settings panel -> Google Sync**. Input your generated **Client ID** and **Client Secret**.
   * Authenticate, and Reality will auto-sync calendar events, your folder task list, and nightly log sheets directly.
5. **Folder Task List Setup**: Inside the app, navigate to the Tasks tab. Folders are automatically synced with your Google Tasks lists. Create a folder (like "Work" or "Personal") and add tasks.
6. **Auto Calendar Sync Setup**: Once Google Sync is authenticated, your daily schedule from the Google Calendar will automatically sync to Reality, displaying your upcoming events in the daily agenda view.

---

## 🏆 CORE FEATURES DEEP-DIVE

### 1. 🚫 App Blocker & Strict Mode
Reality uses zero-tamper native APIs to ensure you cannot easily disable focus features mid-session.
* **Bypass Prevention**: Strict Mode utilizes Device Admin to prevent uninstallation, blocks system settings access (preventing force stops), and prevents timezone/clock manipulation.

### 2. ⚡ Tapasya (Neural Focus Timer)
Enforces deep work blocks (15-minute chunks). Opening unapproved apps immediately triggers a session penalty and voids your progress. It displays a full-screen AMOLED theme to limit visual distractions.
* **Setup**: Go to Tapasya tab, set duration, lock in.

### 3. 🌙 The Nightly Protocol
An automated evening reflection workflow.
* **Process**: AI fetches daily metrics, prompts reflection questions, scores your day, plans tomorrow in Google Tasks/Calendar, and generates a PDF report.
* **Setup**: Nightly Worker triggers automatically based on your schedule.

### 4. 🛌 Bedtime & Sleep Tracking
A local sleep manager optimized for circadian alignment. Uses Health Connect to pull sleep metrics without external wearables.
* **Setup**: Grant Health Connect permission. Use the Quick Settings Tile for one-swipe bedtime activation.

### 5. 🔔 Math-Based Wakeup Alarms
Alarms designed to prevent snoozing.
* **Logic**: Renders math problems that scale in difficulty based on how early the alarm rings (earlier = harder math).
* **Setup**: Set your wake time in the app. Strict anti-snooze logic handles the rest.

### 6. 🤖 Reality Intelligence Assistant (Jarvis-Like AI)
A local-first agent for private in-app command execution using Model Context Protocol (MCP) to automate tasks (change alarms, block apps).
* **Setup**: Integrated into AI Chat. Requires BYOC Google Key for full ecosystem access.

### 7. 🎨 Cinematic Theme Customization
Customize UI rendering, fonts, gradients, and Dark AMOLED mode for a premium look.

---

## 🔒 ZERO-TRUST SECURITY & DATA OWNERSHIP

### Bring Your Own Cloud (BYOC) Architecture
Reality operates without developer servers storing your data.
* Data is stored locally via encrypted SQLite databases.
* Cloud sync relies exclusively on your personal Google Cloud OAuth credentials (as set up above).

### Secure Identity & Encryption
* Local backups and keys are deterministically secured using JIT calculations via Cloudflare edge workers to protect against physical client-side extraction.

---

## 📞 SUPPORT & COMMUNITY

- **Website**: [https://reality.neubofy.in](https://reality.neubofy.in)
- **Email**: support@neubofy.in
- **Telegram / WhatsApp / Instagram / LinkedIn**: @pawanwashudev
- **Issues**: Report bugs to our support team

---

## ⚖️ Legal & License

This application is strictly source-available for review purposes only. We do not allow anyone to clone this repository, modify the app, or build and distribute their own version. It is strictly prohibited to make your own version of the app or to claim ownership. App stores or individuals are allowed to distribute the exact pre-compiled APK obtained directly from our GitHub release page. Our AI crawlers continuously scan the internet. If unauthorized distribution or cloning is detected, strict legal action will be taken.
