# About Reality

Reality is a military-grade productivity operating system designed to eliminate digital distractions and act as a relentless partner in achieving your goals. It is an intelligent, completely integrated local-first system that merges task management, calendar sync, screen-time control, sleep tracking, and agentic AI.

## Important Links
- **Official Website (Architecture & Detail Info):** [https://reality.neubofy.in/](https://reality.neubofy.in/)
- **Download Best/Latest Version:** [https://reality.neubofy.in/download](https://reality.neubofy.in/download)
- **Privacy Policy:** [https://reality.neubofy.in/privacypolicy](https://reality.neubofy.in/privacypolicy)
- **Terms of Service:** [https://reality.neubofy.in/termsofservice](https://reality.neubofy.in/termsofservice)
- **Reality Elite Members:** [https://reality.neubofy.in/promembers](https://reality.neubofy.in/promembers)

## Core Features & Setup Guide

### 1. 🚫 App Blocker & Strict Mode
A zero-tamper blocking system using Android's native Accessibility Service and Device Admin permissions. It prevents uninstallation, system settings tampering, and clock manipulation to guarantee you stick to your focus rules.
* **Setup:** Grant Accessibility and Device Admin permissions in the onboarding flow. Select apps to block in the "App Constraints" or "Grouped App limit" menus.

### 2. ⚡ Tapasya (Neural Focus Timer)
Enforces deep, uninterrupted focus blocks. Managed by a strict 15-minute chunk system. Opening unapproved apps voids your session.
* **Setup:** Navigate to the Tapasya tab, set your session duration, and lock in. A custom AMOLED theme will prevent visual clutter.

### 3. 🌙 Nightly Protocol & Analytics
An automated evening reflection and planning workflow. AI reads your daily metrics, asks personalized questions, and autonomously drafts tomorrow's plan layout into your Google Drive, Tasks, and Calendar.
* **Setup:** The Nightly Worker runs automatically in the evening. Complete the reflection diary to earn XP and generate your plan.

### 4. 🛌 Smart Sleep & Bedtime
A local Sleep and Bedtime manager optimized for circadian alignment, pulling daily metrics from Android Health Connect natively without external cloud wearables.
* **Setup:** Grant Health Connect permissions. Swipe down for the Quick Settings tile to initiate bedtime routines instantly.

### 5. 🔔 Math-Based Wakeup Alarms
Alarms designed to prevent snoozing and sleep-inertia by rendering math problems whose difficulty scales based on how early the alarm rings.
* **Setup:** Set alarms in the app. The Wakeup Alarm Service handles snooze restrictions automatically.

### 6. 🤖 Reality Intelligence Assistant (Jarvis-Like AI)
A local-first agent for secure, private in-app command execution (e.g., adding tasks, changing alarms) rather than a generic text-generation bot.
* **Setup:** Requires Google Workspace Sync for full capability. Uses private, secure edge servers with Bring-Your-Own-Key (BYOK) support.

### 7. ☁️ Google Workspace Sync & BYOC (Bring Your Own Cloud)
Reality works completely without developer servers, syncing directly to your own Google Cloud.
* **Setup (Your Own Key):**
  1. Go to Google Cloud Console, enable **Calendar, Drive, Tasks, and Docs APIs**.
  2. Create OAuth 2.0 Client ID for a **Desktop application**.
  3. Enter the generated **Client ID** and **Client Secret** in Reality's Settings under Google Auth.
  4. Once linked, Reality automatically syncs your Tasks, Calendar events, and Daily Report Sheets.

Everything is processed on-device , preserving total user privacy.
