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

[**🌐 Official Website**](https://reality.neubofy.in) • [**⬇️ Download Latest APK**](https://github.com/pawanwashudev-official/Reality/releases)

</div>

---

## 🎯 EXECUTIVE SUMMARY

**Reality** is a military-grade productivity operating system designed to eliminate digital distractions and act as a relentless partner in achieving your goals. It is not just an app blocker; it is an intelligent, completely integrated local-first system that merges task management, calendar sync, screen-time control, sleep tracking, and agentic AI.

---

## 🏆 CORE FEATURES DEEP-DIVE

### 1. **APP BLOCKER & STRICT MODE** 🚫
Reality features a military-grade blocking engine that operates locally via Android's AccessibilityService. Unlike basic blockers that can be uninstalled or force-stopped, Reality employs an almost impenetrable **Strict Mode**.
*   **How it Works**: It performs O(1) "SettingsBox" lookup combined with keyword scanning to instantly detect and block attempts to modify device settings. It monitors active windows in real-time.
*   **Why it's the Best**:
    *   **Device Admin Protection**: Enforces security policies that prevent uninstalling the app while a focus session is active.
    *   **Anti-Tamper**: Detects and blocks clock manipulation (Time Tampering Detection).
    *   **Loop Hole Closers**: Closes overlay attacks, intent hijacking, and ADB package manager commands.
    *   **Ratchet Logic**: Once Strict Mode is activated, it mathematically restricts you from turning it off without waiting out a timer or completing a severe cooldown/password penalty.

### 2. **🌙 THE NIGHTLY PROTOCOL (6 Unified Steps)**
The cornerstone of Reality's intelligence. A fully autonomous 6-step evening workflow designed to build discipline.
*   **Step 1: Fetch Analytics**: Gathers digital usage metrics, Google Tasks (what was pending vs. completed), Google Calendar events, and queries Health Connect for steps and sleep statistics.
*   **Step 2: Create Diary**: AI reads today's raw usage/events metrics and generates hard-hitting reflection questions to check your discipline. Sets up your diary document inside Google Drive.
*   **Step 3: Save Today Analytics (Reflection Grader)**: AI grades your diary responses, computes daily performance scores, and finalizes your XP delta.
*   **Step 4: Create Plan**: Autonomously creates tomorrow's empty plan document layout inside your Google Drive.
*   **Step 5: Apply Plan**: AI extracts tasks and events scheduled in your plan and enqueues them directly into Google Tasks/Calendar. Dynamically configures morning alarms.
*   **Step 6: Report & Finalize**: Generates a professional progress report PDF, emails summary updates, and backs up raw execution logs to your Google Reality Sheet.
*   **Data Privacy**: All data handling is Local-First. API calls to AI models only send sanitized contexts. No developer servers sit in the middle.

### 3. **⚡ TAPASYA (Neural Focus)**
Tapasya is not a normal timer or stopwatch; it is an aggressive deep-work state.
*   **Uniqueness**: Named after the Sanskrit word for "deep, burning discipline," Tapasya tracks "Effective Time" in 15-minute chunks, ensuring that short bursts of distraction completely penalize your session. It prevents any active timer starts unless a previous timer is formally stopped, and generates shareable QR codes containing your session state to sync with the Neural Focus Web App.

### 4. **🤖 INBUILT AI ASSISTANT (Reality AI Pro)**
Reality does not use a basic chatbot. It features a fully Agentic System operating on a generic Model Context Protocol (MCP) architecture (`ToolRegistry.kt`, `AgentTools.kt`).
*   **How it differs**: It acts autonomously. It can fetch your calendar, toggle alarms, block apps on command, and query the web. It is a Jarvis-like floating modal (`PopupAIChatActivity`) that allows you to use the phone simultaneously, serving as a Jarvis for in-app features.
*   **Supported Models**: 100% private architecture using our hosted open-source gpt-oss-20b model on Cloudflare Workers. Your data is NEVER sent to third-party services like OpenAI or Groq.
*   **Data Handling**: Uses a `ConversationMemoryManager` for context sliding windows and token counting. All requests are authenticated securely using your local Reality Identity credentials. The AI can explicitly store long-term facts about you into local Room storage using the `save_memory` tool.

### 5. **🔔 REMINDERS**
*   **What it is**: A local, hyper-reliable notification system intertwined with your digital ecosystem.
*   **Integration**: Seamlessly maps to the Google Tasks and Calendar events generated by the AI Nightly Protocol. It serves as the aggressive counterpart to your focus blocks, ensuring you transition out of your focus state when the block is over.

### 6. **🛌 SLEEP & ALARM (Formerly Smart Sleep)**
*   **Not a Normal Alarm**: The Wakeup Alarm is explicitly designed to stop you from hitting snooze and going back to sleep.
*   **Math Challenges**: To dismiss a ringing alarm, you must long-press the dismiss button to trigger an in-place arithmetic problem. **Earlier wake-up times automatically generate significantly harder math challenges**.
*   **Snooze Control**: Auto-snoozes in exactly 90 seconds.
*   **Integration**: Integrated with the Bedtime routine that dims the screen, restricts device access, and utilizes Android Health Connect to evaluate your sleep quality natively without external wearables.

### 7. **📊 HEALTH DASHBOARD**
*   **Uniqueness**: Physical health directly impacts mental focus. The Health Dashboard securely reads from `Health Connect Client` (steps, calories, sleep) and correlates these physical metrics directly with your Digital XP System. It proves mathematically that days where you hit your step goals are days where your screen time drops and focus increases.

---

## 🚀 GETTING STARTED

### Installation
1. Download latest APK from GitHub Releases
2. Enable installation from unknown sources
3. Tap APK to install
4. Open Reality and complete onboarding
5. Grant required permissions

### Initial Setup (5 minutes)
1. **Security Intro** - Understand features
2. **Permissions** - Grant accessibility + overlay + usage stats
3. **Add Blocklist** - Select apps to block
4. **Set Focus Hours** - Recurring daily schedule
5. **Configure AI** - No API keys required, works privately out-of-the-box.

### Pro Feature Setup
1. **Activate Reality Elite Member**: Go to Settings -> Reality Elite Member. Sign in with Google and submit a UPI payment (or use instant UPI activation on the same device).
   * **License Terms & Strict Mode Synergy**: Your Reality Elite Member license is valid for the duration you purchase. If you uninstall the app or clear its data (bypassing Strict Mode), **your Pro access will be permanently lost and you will need to buy it again**. However, if you legitimately log out and log back in with the same Google Account, your access will be restored. This ensures we can maintain low subscription costs while heavily discouraging bypass attempts.
2. **Google Workspace Sync**: Go to your Profile page and sign in with Google to grant Calendar, Drive, and Tasks permissions. This allows Reality to log your plans and fetch tasks automatically.
3. **AI Privacy**: Navigate to Settings -> AI Settings to customize your personal introduction and review data access permissions. No API keys are required.

---

## 🏗️ DEEP TECHNICAL ARCHITECTURE

### Technology Stack
```
Platform:       Android 26+ (API 26 to 36)
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

### Security Model
| Layer | Implementation |
|-------|-----------------|
| **Data at Rest** | Encrypted SharedPreferences |
| **Google API Auth** | OAuth 2.0 with refresh tokens |
| **Local Blocking** | Accessibility Service + Device Admin |
| **Bypass Prevention** | Multi-layer validation (5+ checks) |
| **Permissions** | Runtime + static manifest declarations |

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

- **Developer**: Pawan Washudev (Neubofy)
- **Status**: Active Development

### Secure Identity & Encryption
Instead of relying on legacy client-side identifiers (like MD5) or manual user password management for backups, Reality employs a **Just-In-Time (JIT) secure edge architecture**:
- Both the unique `userId` and automated `backupPassword` keys are generated deterministically on our Cloudflare Worker edge nodes.
- This is achieved via server-side HMAC-SHA256 calculations using an isolated secret pepper environment string, eliminating all local cryptographic vulnerabilities and client-side exploits.
- These keys are injected directly into Android's EncryptedSharedPreferences upon Google Login or critical lifecycle events, providing frictionless background encryption without exposing sensitive configuration files.
