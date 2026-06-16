# Reality Super App: Deep Context & Refinement Guide

This document provides a comprehensive overview of the Reality app to ensure any AI agent or developer can maintain 100% context and align with the professional, open-source-first vision.

---

## 🚀 1. The Vision: "Intelligent Life OS"
Reality is moving from a "basic utility" to a "military-grade productivity operating system."
- **Professionalism:** Every UI component must feel premium, using glassmorphism, fluid animations, and high-quality typography (Outfit font).
- **Business Model:** 100% Open Source code. Anyone can build the APK. However, we charge a one-time payment for the "Official Optimized APK" to support maintenance.
- **Support:** support@neubofy.in
- **Web Presence:** reality.neubofy.in

---

## 📂 2. Core Repository Strategy (CRITICAL)
To finalize the transition to a professional repo and fix historical issues:
1. **Clean Slate:** We will delete the entire `.git` history and push as a fresh commit. This removes old basic-app history and ensures contributors are only those working on the current version.
2. **License Fix:** The repository uses GPL-3.0. The `LICENSE` and `NOTICE` files must be recognized by GitHub (ensure correct naming and header format).
3. **Build Stability:** DO NOT change the core structure of the Android or Next.js app. Changes should be limited to UI refinement and branding.

---

## 🛠️ 3. Key App Modules & Improvement Areas

### A. UI/UX Refinement (Significant Changes Needed)
- **Base Style:** Heavy use of `glass_card_background.xml` and `page_gradient_bg.xml`.
- **Transitions:** Use `fade_in.xml`, `slide_up_enter.xml` for all activity transitions.
- **Branding:** Replace all old "basic" assets with high-quality Neubofy branding. Update `app_logo.png`.
- **Typography:** Ensure `font/outfit.xml` is used consistently across all `themes.xml`.

### B. The Nightly Protocol (The Crown Jewel)
- **Logic:** `NightlyPhaseData.kt` -> `Analysis` -> `Planning` -> `Reporting`.
- **Integration:** Deep sync with Google Tasks, Calendar, Drive, and Docs.
- **Refinement:** The PDF reports generated via `html2pdf` need a more professional template (CSS-like styling in Android).

### C. Security & Strict Mode
- **Persistence:** Ensure `AppBlockerService` and `DeviceAdmin` are impossible to bypass without the designated "Emergency Mode" or "Math Challenge."
- **Anti-Uninstall:** This is a core selling point. It must be robust.

---

## 📝 4. Branding & Content Updates (TODO)

### Strings & Localizations
Update `strings.xml` in all languages:
- Change all support mentions to `support@neubofy.in`.
- Change website links to `reality.neubofy.in`.
- Remove any "basic app" phrasing; use "Life OS," "Intelligence," "Command Center."

### Website (`/website` directory)
- Current: Next.js + Tailwind.
- Action: Update `package.json` metadata, update the landing page to reflect the "Pro Optimized APK" vs "Open Source Source Code" model.

---

## ⚠️ 5. Developer Warnings (To avoid Hallucinations)
- **Weak Hardware:** The owner's computer is weak. Do not suggest heavy build tools or complex local emulators. Stick to lightweight Kotlin/XML edits.
- **API Dependencies:** Heavily dependent on Google API Client (Tasks, Drive, Docs). Any change to package names or auth flow will break the Nightly Protocol.
- **Resource Heavy:** `res/layout` has 200+ files. Always check `include_standard_header.xml` before editing headers.

---

## 🔒 6. Licensing & Legal
- **License:** GNU GPL v3.0.
- **Owner:** Pawan Washudev / Neubofy.
- **Terms:** Commercial sale of the *compiled APK* is permitted as part of the "Pro" support, while the *source* remains open.

---

*This file serves as the ground truth for the Reality project. Refer to this before any major code change.*
