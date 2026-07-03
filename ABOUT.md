# About Reality

> **"Stop managing your life. Start commanding it."**

**Reality** is a military-grade productivity operating system designed to eliminate digital distractions and act as a relentless partner in achieving your goals. It is not just an app blocker; it is an intelligent, completely integrated local-first system that merges task management, calendar sync, screen-time control, sleep tracking, and agentic AI.

## 🎯 Executive Summary
Reality operates locally via Android's AccessibilityService. Unlike basic blockers that can be uninstalled or force-stopped, Reality employs an almost impenetrable **Strict Mode**. It monitors active windows in real-time, features anti-tamper protections, and enforces time penalties to ensure focus.

## 🏆 Core Features

### 🚫 Strict Mode App Blocker
- Device Admin Protection prevents uninstalls.
- Anti-Tamper detects clock manipulation.
- Blocks settings access and system-level workarounds.

### 🌙 The Nightly Protocol
An autonomous AI-powered evening workflow to build discipline:
- Pulls screen-time, tasks, health data.
- AI reflects on your daily performance.
- Automatically plans your next day and schedules alarms.

### ⚡ Tapasya (Neural Focus)
A rigorous deep-work timer tracking "Effective Time" in 15-minute chunks, strictly penalizing digital distraction.

### 🤖 Reality AI Pro
A fully Agentic Siri-like AI that can block apps, fetch calendar data, and run routines. Supports Bring-Your-Own-Key.

### 🛌 Sleep & Alarm
Math-based Wakeup Alarms that scale in difficulty, preventing you from oversleeping, tightly integrated with Sleep tracking.

---
*Made with ❤️ for Focus. Open Source for review only. No Ads. No Trackers.*

## ⚖️ Legal & License
This application is strictly open source for review purposes only. We do not allow anyone to clone this repository, modify the app, or build and distribute their own version. It is strictly prohibited to make your own version of the app or to claim ownership. App stores or individuals are allowed to distribute the exact pre-compiled APK obtained directly from our GitHub release page. Our AI crawlers continuously scan the internet. If unauthorized distribution or cloning is detected, strict legal action will be taken.

## 🐛 Support & Issues
Encountered a bug or have a feature request? You can easily report it!
Use the **"Report an Issue"** button directly within the app (About page) to seamlessly create an issue on our GitHub repository.
Alternatively, visit [Reality GitHub Issues](https://github.com/pawanwashudev-official/Reality/issues) to submit your feedback.



### Secure Identity & Encryption
Instead of relying on legacy client-side identifiers (like MD5) or manual user password management for backups, Reality employs a **Just-In-Time (JIT) secure edge architecture**:
- Both the unique `userId` and automated `backupPassword` keys are generated deterministically on our Cloudflare Worker edge nodes.
- This is achieved via server-side HMAC-SHA256 calculations using an isolated secret pepper environment string, eliminating all local cryptographic vulnerabilities and client-side exploits.
- These keys are injected directly into Android's EncryptedSharedPreferences upon Google Login or critical lifecycle events, providing frictionless background encryption without exposing sensitive configuration files.
