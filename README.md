<div align="center">

<img src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png" width="180" alt="Reality Logo" style="border-radius: 20px; margin-bottom: 20px;">

# Reality
### The Intelligent Life OS

**Developed by Pawan Washudev | Neubofy**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/pawanwashudev-official/Reality?style=for-the-badge&color=orange)](https://github.com/pawanwashudev-official/Reality/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/pawanwashudev-official/Reality/total?style=for-the-badge&color=success)](https://github.com/pawanwashudev-official/Reality/releases)
[![License](https://img.shields.io/github/license/pawanwashudev-official/Reality?style=for-the-badge&color=blue)](https://www.gnu.org/licenses/gpl-3.0)

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=for-the-badge)](https://www.android.com)
[![Privacy](https://img.shields.io/badge/Data-Local%20%2B%20Your%20G--Drive-lock.svg?style=for-the-badge&color=teal)]()
[![AI](https://img.shields.io/badge/AI-Bring%20Your%20Own%20Key-purple.svg?style=for-the-badge)]()
[![Ads](https://img.shields.io/badge/Ads-ZERO-red.svg?style=for-the-badge)]()
[![Trackers](https://img.shields.io/badge/Trackers-ZERO-red.svg?style=for-the-badge)]()

> **"Stop managing your life. Start commanding it."**

### 🌟 100% Open Source • No Ads • No Trackers • Any AI (BYO Key)

<p>While advanced features (like Neural Protocol, Gamification, and Google Workspace Sync) require a one-time Reality Pro payment to support ongoing maintenance, the app remains 100% open source. You can always build the APK yourself from the source code.</p>

[**🌐 Official Website**](https://reality.neubofy.in) • [**⬇️ Download Latest APK**](https://github.com/pawanwashudev-official/Reality/releases)

</div>

## Complete App Documentation & Feature Analysis

**Version:** 1.0.5 | **Platform:** Android 26+ | **Language:** Kotlin (99%) | **License:** Open Source
**Developer:** Pawan Washudev (Neubofy) | **Website:** https://reality.neubofy.in

---

## 📋 TABLE OF CONTENTS
1. [Executive Summary](#executive-summary)
2. [Complete Feature Matrix](#complete-feature-matrix)
3. [Deep Technical Architecture](#deep-technical-architecture)
4. [All Hidden & Advanced Features](#all-hidden--advanced-features)
5. [Competitive Comparison](#competitive-comparison)
6. [System Requirements & Permissions](#system-requirements--permissions)

---

## 🎯 EXECUTIVE SUMMARY

**Reality** is a military-grade productivity operating system that combines:
- **App Blocking Engine** (accessibility service-based protection)
- **Gamification System** (XP/Streak-based discipline tracking)
- **AI-Powered Neural Protocol** (daily reflection, planning, & reporting)
- **Google Workspace Integration** (Tasks, Calendar, Drive, Docs, Sheets)
- **Health Connect Sync** (steps, calories, sleep tracking)
- **Intelligent Alarm System** (adaptive wake-up with math challenges)

**Core Philosophy:** *Stop managing your life. Start commanding it.*

---

## 🏆 COMPLETE FEATURE MATRIX

### 1. **FOCUS & APP BLOCKING** 🚫

#### Standard Focus Mode
- **Blocklist Management** (`UnifiedBlocklistActivity`)
  - Add/remove apps to block list
  - Category-based blocking (Social, Gaming, Shopping, etc.)
  - Quick-toggle blocking on/off
- **Real-Time Blocking**
  - AccessibilityService monitors active window
  - O(1) "SettingsBox" lookup for settings app protection
  - Prevents app launching via multiple bypass methods:
    - Direct intent hijacking
    - Overlay attacks
    - Background process resurrection
  - Floating timer overlay visible across all apps
  - Custom block messages with action URLs

#### Armored Strict Mode 🔒
- **Device Admin Protection**
  - Prevents uninstall during active focus
  - Blocks settings access (Settings Protection)
  - Two-pass accessibility scanning (50ms + 300ms retry)
- **Time Tampering Detection**
  - Detects system clock manipulation attempts
  - Blocks timezone/NTP exploitation
- **Loop Hole Closers**
  - Catches intent hijacking from accessibility overlays
  - Blocks package manager access
  - Prevents accessibility service disable attempts
  - Monitors for adb shell bypasses

#### Neural Focus Mode (Ultra-Focus) 🔥
- Named after Sanskrit for "deep, burning discipline"
- **Auto-activation** on scheduled focus times
- **State Persistence** across device reboots (via BootReceiver)
- **Warning Sounds** when focus period is ending
- **Aggressive Protection** - the most extreme focus mode

---

### 2. **INTELLIGENT SCHEDULING & REMINDERS** ⏰

#### Schedule Management (`ScheduleListActivity`)
- **Auto-Focus Hours**
  - Set recurring daily focus blocks
  - Multiple time slots per day
  - Syncs with Google Calendar
- **Bedtime Mode** (`BedtimeActivity`)
  - Custom bedtime routines
  - Device restriction during sleep hours
  - Sleep tracking via Health Connect
  - Smart wake-up alarm integration
- **Emergency Mode** (`EmergencyModeActivity`)
  - Extreme restrictions for critical situations
  - Override capabilities with emergency unlock codes
- **Usage Limits** (`UsageLimitActivity`)
  - Per-app daily time limits
  - Per-app group limits (social apps limit)
  - XP penalties for exceeding limits
  - Grace period before enforcement

#### Reminder System
- **Multi-Source Reminders**
  - Local alarms (via AlarmManager)
  - Google Tasks integration
  - Google Calendar events
  - Custom reminders with HTTP actions
- **Intelligent Alarm Activity** (`AlarmActivity`)
  - Full-screen interrupt overlay
  - Snooze functionality with countdown
  - **Math Challenge Verification** (prevents sleepy tapping)
  - Custom URL actions (open apps, web links, "reality://" deep links)
  - Auto-snooze timer for repeated alarms
  - Action logging for dismissed/snoozed alarms

---

### 3. **GAMIFICATION & XP SYSTEM** 🏆

#### Dynamic XP Mechanics
- **XP Earnings**
  - Base XP for blocking attempts (users resist temptation)
  - Screen time bonus for staying under daily limits
  - Streak bonuses (consecutive days of discipline)
  - Schedule adherence XP (attending planned focus blocks)
- **XP Penalties**
  - Negative XP for exceeding usage limits
  - Breaking focus mode penalty
  - Screen time overage penalty
  - Accessing blocked apps penalty
- **Leveling System**
  - User level reflects actual digital discipline
  - Level-based unlocks for features
  - Leaderboard readiness (framework in place)

#### Streak Tracking
- Consecutive days of focus
- Resets on breaking focus rules
- Visual motivation cards
- XP tier system (Bronze → Silver → Gold → Platinum)

---

### 4. **🌙 NIGHTLY PROTOCOL** (Reality Pro - AI-Powered Evening Ritual)

The cornerstone of Reality's intelligence. A fully autonomous 4-phase evening workflow:

#### **Phase 1: Data Collection** 📊
`NightlyPhaseData.kt` - Collects your entire day's digital footprint:

- **Step 1: Fetch Google Tasks**
  - Syncs all task lists from Google Tasks API
  - Categorizes: Due Today, Completed, Pending
  - Task statistics (pending count, completion %)

- **Step 2: Fetch Calendar + Neural Focus Sessions**
  - Google Calendar events for the day
  - Neural Focus deep-work session logs
  - Event duration & completion status

- **Step 3: Calculate Screen Time & Health Metrics**
  - Total screen time (via UsageStatsManager)
  - Per-app usage breakdown
  - Health Connect data:
    - Steps walked
    - Calories burned
    - Sleep quality & duration (if wearing fitness tracker)
  - Converts to XP (10 min under limit = +XP bonus)

- **Step 4: AI Reflection Questions**
  - Generates dynamic questions based on your actual day
  - Example: "You spent 4h on Instagram but 30min studying. Why?"
  - Uses OpenAI/Gemini/Groq API (bring your own key)
  - Questions personalized from AI Settings

- **Step 5: Create AI Diary in Google Docs**
  - Automatic Google Docs creation in Drive
  - AI-written reflections saved to your document
  - Timestamps & metrics embedded
  - Shareable diary (invite friends for accountability)

#### **Phase 2: Analysis & AI Coaching** 🤖
`NightlyPhaseAnalysis.kt`:

- **Reflection Analysis**
  - Re-analyzes your responses to AI questions
  - Identifies patterns (procrastination, peak focus hours, etc.)
  - Generates personalized coaching advice
- **XP Finalization**
  - Calculates screen time bonuses/penalties
  - Applies streak multipliers
  - Updates level progression

#### **Phase 3: Planning** 📅
`NightlyPhasePlanning.kt` - AI generates tomorrow's schedule:

- **Step 8: Create Plan Document**
  - New Google Docs file in Drive (dated)
  - Folder structure: Reality > Plans > YYYY > MM > YYYY-MM-DD

- **Step 9: AI Parse Plan to JSON**
  - AI converts plan narrative to structured JSON:
    ```json
    {
      "tasks": [
        {"title": "Study Math", "duration": 90, "priority": "high"},
        {"title": "Gym", "duration": 60, "priority": "medium"}
      ],
      "events": [
        {"title": "Meeting", "time": "14:00", "duration": 60}
      ],
      "alarms": [
        {"title": "Wake Up", "time": "06:30", "challenge": "math"}
      ]
    }
    ```

- **Step 10: Create Google Tasks & Calendar Events**
  - Auto-creates tasks in Google Tasks (default list)
  - Auto-creates calendar events in your primary calendar
  - Links back to plan document

- **Step 11: Generate AI Report**
  - Daily PDF report with:
    - Yesterday's achievements & failures
    - XP gained/lost
    - Health metrics summary
    - AI coaching insights
  - Uploads to Google Drive

- **Step 12: Create PDF Report**
  - Uses iText7 (`html2pdf:4.0.5`)
  - Styled markdown → PDF conversion
  - Multi-page support with charts

- **Step 13: Set Wake-up Alarm**
  - System alarm created for tomorrow morning
  - Math challenge configured
  - Snooze intervals set (3-5 minutes)
  - Max attempts limit (5 tries before force-open)

#### **Phase 4: Reporting & Notifications** 📈
- Dashboard updates with today's XP gain
- Widgets refresh (Neural Report Widget, Plan Widget)
- Optional email reports (Gemini workspace integration)

---

### 5. **HEALTH & WELLNESS INTEGRATION** 💪

#### Health Connect Sync
- **Steps Tracking**
  - Daily step count
  - Weekly/monthly trends
  - XP bonus for hitting step goals (10k steps = +50 XP)

- **Sleep Monitoring**
  - Sleep duration tracking
  - Sleep quality metrics
  - Bedtime reminders
  - Wakeup verification (sleep quality check-in)

- **Calorie Burn Tracking**
  - Daily calorie expenditure
  - Activity-based metrics
  - Health dashboard integration

#### Smart Sleep Features (`SmartSleepActivity`)
- **Sleep Verification Dialog**
  - Triggered on wake-up alarm
  - Confirms user slept well
  - Updates health metrics
- **Deep Link Support**
  - `reality://smart_sleep` - direct sleep check-in
  - Accessible from alarm actions

---

### 6. **GOOGLE WORKSPACE INTEGRATION** ☁️

#### Google Tasks API
`GoogleTasksManager.kt` - Full tasks management:
- List all task lists
- Create/update/delete tasks
- Task list synchronization
- Today's task statistics
- Supports multiple task lists (default & custom)

#### Google Calendar API
`GoogleCalendarManager.kt`:
- List calendars
- Create events with automatic scheduling
- Sync scheduled focus blocks
- View upcoming events (nightly planning reference)

#### Google Docs Integration
`GoogleDocsManager.kt`:
- Create diary documents
- Create plan documents
- Auto-formatting with headings/lists/tables
- Insert images from Drive
- Batch updates for performance

#### Google Drive Integration
`GoogleDriveManager.kt`:
- Automatic folder creation (Reality > Diary, Plans, Reports)
- PDF report upload
- Automatic organization by date (YYYY > MM > DD structure)
- File search & versioning
- Trash management (archival)

#### Google Sheets Support
`GoogleSheetsManager.kt`:
- Daily stats logging (optional)
- Historical data aggregation
- CSV export capability

---

### 7. **INTELLIGENT ALARM SYSTEM** ⏰

#### Alarm Types
`AlarmActivity.kt` & `WakeupAlarmRingingActivity.kt`:

- **Standard Reminders**
  - Simple dismissal
  - Snooze options (5, 10, 15 min)
  - Custom URL actions

- **Wakeup Alarms** (with challenges)
  - Math challenge (arithmetic problems)
  - Forces mental engagement before dismissal
  - Progressive difficulty
  - Max attempts limit

- **Multi-Source Alarms**
  - Local database alarms (custom created)
  - Google Tasks deadlines (synced)
  - Google Calendar notifications (synced)
  - Neural Protocol alarms (AI-generated)

#### Alarm Features
- **Full-Screen Interrupt** (notification policy access required)
- **Vibration & Sound**
  - Customizable ringtone
  - Vibration patterns
  - Audio focus control
- **Auto-Snooze Timer**
  - Configurable snooze interval (default 3 min)
  - Max attempts before force-open app
  - Sound escalation

#### Alarm Persistence
- **BootReceiver** reschedules alarms after device reboot
- **AlarmScheduler** handles both AlarmManager & WorkManager
- Heartbeat Worker refreshes alarm state every 15 minutes

---

### 8. **PERSONALIZATION & APPEARANCE** 🎨

#### Customizable Themes (`AppearanceActivity`)
- **Color Palettes**
  - Light mode & dark mode independent colors
  - Accent colors (Teal, Orange, Purple, Blue, Green, Red)
  - Custom color picker integration

- **Background Patterns**
  - Zen (minimalist)
  - Tech (grid)
  - Nature (organic)
  - Urban (modern)

- **Typography Settings**
  - Font size adjustment
  - Font family selection
  - Line spacing control

- **Visual Effects**
  - Blur intensity
  - Corner radius
  - Animations toggle
  - Floating timer styling

#### Dynamic Localization
- **16+ Languages Supported**
  - European: French, German, Spanish, Italian, Portuguese
  - Asian: Hindi, Tamil, Telugu, Kannada, Marathi, Bengali
  - Others: Dutch, Polish, Russian, Japanese, Chinese
- **Per-App Locale** (Android 13+)
  - Change language without app restart
  - Instant language switching

---

### 9. **DEVICE CONTROL & ADMINISTRATION** 🔐

#### Device Admin Features
- **Armored Strict Mode Protection**
  - Prevents app deletion during active focus
  - Requires security unlock

- **Settings Protection** (Strict Mode)
  - Blocks access to system settings
  - Two-pass detection system:
    - Fast pass (50ms) for unique class detection
    - Retry pass (300ms) for keyword-based pages

- **Battery Optimization Bypass**
  - Allows app to survive battery saver mode
  - Maintains background functionality

#### Accessibility Service Integration (`AppBlockerService`)
- Monitors window changes in real-time
- Detects blocked app launches
- Intercepts and blocks app opens
- Logs all block attempts
- Shows penalty overlays
- Detects settings app access attempts

---

### 10. **BACKUP & DATA MANAGEMENT** 💾

#### Cloud Backup System (`BackupManager.kt`)
- **17 Backup Categories**
  - Blocklist & Focus Mode
  - Schedules & Auto-Focus Hours
  - Bedtime Mode Settings
  - Emergency Mode Config
  - Usage Limits
  - Strict Mode Rules
  - Gamification (XP/Streak)
  - Neural Protocol Settings
  - AI Model Preferences
  - Theme & Appearance
  - Block Messages
  - Reminders
  - App Groups
  - Per-App Limits
  - Neural Focus Sessions
  - Daily Statistics
  - App Blocking Configurations

#### Selective Restore
- Choose which categories to restore
- Granular control (don't overwrite everything)
- Restore from any backup point
- Timestamp tracking

#### Google Drive Integration
- Backup file: `reality_backup.json`
- Hidden folder: `.reality_backup`
- Version tracking
- Metadata preservation (app version, device model)

---

### 11. **ADVANCED FEATURES - HIDDEN GEMS** 🔮

#### Neural Focus Sessions (`TapasyaActivity`)
- **Deep Work Time-Boxing**
  - Start focused work session
  - Customizable duration (30min - 4h)
  - Auto-starts focus mode
  - Blocks all non-focus apps
  - Progress timer
  - Completion rewards (XP bonus)

#### Floating Action Buttons & Widgets
- **AI Chat Widget** (Popup overlay)
  - Quick access AI chat from any app
  - Lightweight widget
  - Persistent across activities

- **Neural Report Widget**
  - Shows yesterday's XP earned
  - Daily achievements summary
  - Quick-click to view full report

- **Plan Widget**
  - Today's scheduled tasks
  - Upcoming calendar blocks
  - XP progress bar

- **Neural Focus Widget**
  - Current session status
  - Time remaining
  - One-tap start/stop

#### Quick Settings Tile
`RealitySleepTileService`:
- System-level quick tile for sleep mode
- One-tap bedtime activation
- Status indicator in notification shade

#### App Groups & Family Controls
`AppGroupsActivity` & `AppLimitsActivity`:
- Create app categories (Social, Gaming, Work, etc.)
- Set group-level time limits
- Apply rules to entire categories
- Per-app exceptions

#### Block Messages (`BlockMessagesActivity`)
- Custom messages when users hit blocks
- Motivational quotes
- Action buttons (open calendar, view stats)
- Message scheduling by time of day

#### Active Blocks Dashboard
`ActiveBlocksActivity`:
- Live view of currently blocked apps
- Active focus session status
- Manual unblock with confirmation
- Block history

---

### 12. **AI & AUTOMATION SYSTEM** 🤖

#### Hybrid AI Architecture
- **Bring Your Own Key Model**
  - Supports: OpenAI (GPT-4), Google Gemini, Anthropic Claude, Groq, Mistral
  - Any OpenRouter-compatible model
  - Free fallback: GPT-OSS-120B

- **AI Settings** (`AISettingsActivity`)
  - Model selection
  - API key input (encrypted storage)
  - Temperature & response customization
  - System prompts for reflection/planning

#### AI-Powered Features
- **Nightly Reflection** - Dynamic questions based on actual data
- **Plan Generation** - Tomorrow's schedule auto-created
- **Coaching** - Personalized motivation & advice
- **Analytics** - Pattern recognition in behavior

#### Preset Models
- Comes with "Reality-Optimized" GPT-OSS-120B ready to use
- One-click model switching
- Model-specific prompt optimization

---

### 13. **NOTIFICATIONS & USER ENGAGEMENT** 📲

#### Notification Channels
- **High Priority Alarms** (full screen, sound)
- **Reminders** (heads-up, vibration)
- **Focus Started/Ended** (silent, persistent)
- **XP Updates** (silent, dismissible)
- **Nightly Completion** (high priority)

#### Notification Types
- Block attempt notifications
- Focus mode status
- Alarm & reminder notifications
- XP milestone celebrations
- Daily summary notifications

---

### 14. **STATISTICS & ANALYTICS** 📊

#### Daily Stats Tracking
- **Screen Time Analytics**
  - Total daily screen time
  - Per-app breakdowns
  - Trends over time
  - Comparison to limits

- **Usage Patterns**
  - Most used apps
  - Peak usage hours
  - App usage by category
  - Time-of-day heatmaps

- **Focus Metrics**
  - Focus blocks completed
  - Focus blocks missed
  - Average focus duration
  - Consistency score

- **Health Metrics**
  - Steps
  - Calories
  - Sleep hours
  - XP correlation with health

#### Data Retention
- Detailed stats: 3 days (local)
- Summary stats: 30 days (database)
- Historical data: Synced to Drive monthly

---

### 15. **ONBOARDING & PERMISSIONS** 🚀

#### Progressive Onboarding System
`OnboardingActivity` & `OnboardingPermissionsFragment`:

- **Welcome Screen**
  - App introduction
  - Core values explanation
  - Permission overview

- **Permission Checklist** (5 Required, Optional)
  - **Required:**
    1. Accessibility Service (app blocker core)
    2. Overlay Permission (floating timer)
    3. Usage Stats Permission (screen time)
  - **Optional:**
    1. Notifications (alarms & alerts)
    2. Battery Optimization Bypass (reliability)

- **Security Intro** (`SecurityIntroActivity`)
  - Device Admin explanation
  - Anti-bypass measures overview
  - Commitment confirmation

- **Pro Feature Setup**
  - Google Workspace credentials
  - AI model selection
  - Initial plan creation

---

## 🏗️ DEEP TECHNICAL ARCHITECTURE

### Technology Stack
```
Platform:       Android 26+ (API 26 to 35)
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
| | Lottie | 6.0.0 | Animations |
| | Coil | 2.6.0 | Image loading |
| **Markdown** | Markwon | 4.6.2 | Content rendering |
| **PDF** | iText7 html2pdf | 4.0.5 | Report generation |
| **QR Code** | ZXing + ML Kit | 3.5.3, 17.3.0 | QR scan/generate |
| **Other** | JSoup | 1.17.2 | HTML parsing |

### Database Schema
- **Focus Mode**: Blocked apps, block history
- **Schedules**: Auto-focus hours, bedtime times
- **Usage Limits**: Per-app limits, thresholds
- **XP & Gamification**: XP history, streaks, levels
- **Reminders**: Local alarms, dismissed status
- **Nightly**: Step data, reflection responses, plan JSON
- **App Groups**: Category definitions, memberships
- **Settings**: User preferences, theme, language

### Security Model
| Layer | Implementation |
|-------|-----------------|
| **Data at Rest** | Encrypted SharedPreferences |
| **Google API Auth** | OAuth 2.0 with refresh tokens |
| **Local Blocking** | Accessibility Service + Device Admin |
| **Bypass Prevention** | Multi-layer validation (5+ checks) |
| **Permissions** | Runtime + static manifest declarations |

---

## 🌍 COMPETITIVE COMPARISON: Reality vs World-Class Apps

### vs Forest (Popular Productivity App)
| Aspect | Reality | Forest |
|--------|---------|--------|
| **Price** | 🟢 Open Source (Pro Optional) | 🔴 Paid ($3.99) |
| **Open Source** | 🟢 100% | 🔴 Closed |
| **Ads/Trackers** | 🟢 Zero | 🔴 Has Ads |
| **Data Storage** | 🟢 Your Drive | 🔴 Their Servers |
| **App Blocking** | 🟢 Military-Grade | 🟡 Visual Metaphor Only |
| **AI Agent** | 🟢 Autonomous (Plans/Alarms/Reports) | 🔴 None |
| **Google Integration** | 🟢 Full Ecosystem (Tasks, Cal, Drive, Docs) | 🔴 None |
| **XP System** | 🟢 Dynamic (bonus/penalty) | 🟡 Simple Counter |
| **Neural Protocol** | 🟢 4-Phase AI Ritual | 🔴 None |
| **Health Sync** | 🟢 Steps, Calories, Sleep | 🔴 None |
| **Battery Impact** | 🟢 <1% | 🟡 Standard |

### vs Freedom (Enterprise Blocking)
| Aspect | Reality | Freedom |
|--------|---------|---------|
| **Price** | 🟢 Open Source | 🔴 Subscription ($5-15/mo) |
| **Blocking Tech** | 🟢 Native Accessibility | 🔴 VPN-Based (high battery) |
| **Bypass Protection** | 🟢 Loop Hole Closers (5+ checks) | 🟡 Good but VPN-limited |
| **AI Planning** | 🟢 Yes (Neural Protocol) | 🔴 No |
| **Google Sync** | 🟢 Full | 🔴 None |
| **Data Privacy** | 🟢 Yours Alone | 🟡 Via VPN servers |
| **Open Source** | 🟢 Yes | 🔴 No |

### vs AppBlock (Simple Blocker)
| Aspect | Reality | AppBlock |
|--------|---------|----------|
| **Price** | 🟢 Open Source | 🟡 Freemium + Paid |
| **Blocking** | 🟢 Advanced (Strict Mode) | 🟡 Standard |
| **Gamification** | 🟢 Advanced XP/Levels | 🔴 None |
| **AI Features** | 🟢 Full Neural Protocol | 🔴 None |
| **Workspace Sync** | 🟢 Full Google Integration | 🔴 None |
| **Health Integration** | 🟢 Yes | 🔴 No |
| **Report Generation** | 🟢 PDF + Docs | 🔴 Basic Stats |
| **Customization** | 🟢 Extensive | 🟡 Limited |

### vs Notion (Productivity Hub)
| Aspect | Reality | Notion |
|--------|---------|--------|
| **Price** | 🟢 Open Source | 🟡 Subscription |
| **App Blocking** | 🟢 Yes | 🔴 No |
| **AI Agent** | 🟢 Autonomous Planner | 🟡 AI Assistant Only |
| **Mobile-First** | 🟢 Yes | 🟡 Web-Focused |
| **Offline Use** | 🟢 100% | 🟡 Limited |
| **Daily Automation** | 🟢 Full (Nightly) | 🔴 Manual |
| **Health Sync** | 🟢 Yes | 🔴 No |
| **Battery Impact** | 🟢 <1% | 🟡 N/A |

---

## 🔐 SECURITY & PRIVACY ARCHITECTURE

### Zero-Trust Security Model
1. **No Backend Servers** - Reality has zero infrastructure
2. **Local-First Processing** - All blocking logic runs on-device
3. **Your Drive Only** - Backups stored in your personal Google Drive
4. **Open Source Audit** - Every line of code publicly visible
5. **Encrypted Credentials** - OAuth tokens stored securely

### Bypass Prevention Layer (Reality's Secret Sauce)
```kotlin
// Multi-layer validation:
1. AccessibilityService window monitoring (real-time)
2. SettingsBox O(1) lookup (instant page detection)
3. Keyword scanning fallback (for ambiguous settings pages)
4. Device Admin enforcement (prevents uninstall)
5. Time validation (detects clock tampering)
6. Intent hijacking detection (catches overlay attacks)
7. Package manager access blocking (prevents uninstall via ADB)
```

---

## 📱 SYSTEM REQUIREMENTS & PERMISSIONS

### Minimum Requirements
- **Android Version:** 8.0 (API 26) - Android 15 compatible
- **RAM:** 256MB minimum (typical 50-100MB usage)
- **Storage:** 150MB app + database
- **Connectivity:** Optional (works offline, Google Sync requires internet)

### Required Permissions (3)
| Permission | Purpose |
|-----------|---------|
| `ACCESSIBILITY_SERVICE` | Real-time app blocking |
| `SYSTEM_ALERT_WINDOW` | Floating timer overlay |
| `PACKAGE_USAGE_STATS` | Accurate screen time |

### Optional Permissions (2)
| Permission | Purpose |
|-----------|---------|
| `POST_NOTIFICATIONS` | Alarms & alerts |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Background reliability |

### Full Manifest Permissions
- Health Connect (steps, calories, sleep)
- Camera (QR scanner)
- Record Audio (voice input for AI)
- Internet (Google APIs)
- Network State (connectivity check)
- Device Admin (anti-uninstall)
- Schedule Exact Alarm (Neural Protocol)
- Query All Packages (app listing)
- Usage Stats (screen time)
- Access Notification Policy (DND mode)

---

## 🎮 HIDDEN FEATURES YOU HAVEN'T DISCOVERED

1. **Deep Link Support**
   - `reality://smart_sleep` - quick sleep mode toggle
   - `reality://block_<appName>` - block specific app
   - Accessible from any app via custom URLs

2. **Terminal Logger**
   - Detailed debug logs for power users
   - Export logs for troubleshooting
   - Hidden terminal view

3. **ADB Setup Assistant**
   - Guides developers through adb commands
   - One-click permission granting
   - Device integration setup

4. **QR Code Scanner**
   - Built-in barcode scanning
   - Create shareable focus schedules via QR
   - Scan competitor's settings to compare

5. **Shortcut System**
   - Android app shortcuts for quick actions
   - Start focus via home screen icon
   - Custom automation routines

6. **Health Dashboard**
   - Advanced health metrics visualization
   - Correlation with XP & focus
   - Historical health trends

7. **Reflection Detail View**
   - Deep dive into nightly reflections
   - View AI coaching history
   - Behavior pattern analysis

8. **Planning Pad Activity**
   - Free-form planning interface
   - Voice-to-text input
   - Auto-sync to Google Docs

9. **Math Challenge Difficulty Levels**
   - Difficulty adjusts based on time of morning
   - Earlier wake = harder math
   - Prevents easy dismissal

10. **Penalty Overlay**
    - Shows when blocked app accessed
    - Custom penalty duration
    - Can be extended for repeated violations

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
5. **Configure AI** - Add OpenAI key (optional, has free default)

### Power User Setup (15 minutes)
1. Connect Google Account (Tasks, Calendar, Drive)
2. Configure Neural Protocol settings
3. Set up Neural Focus sessions
4. Enable Health Connect sync
5. Customize appearance theme
6. Import backup (if migrating from another device)

---

## 📞 SUPPORT & COMMUNITY

- **GitHub**: https://github.com/pawanwashudev-official/Reality
- **Website**: https://reality.neubofy.in
- **Issues**: Report bugs on GitHub
- **Contributing**: PRs welcome for features/improvements

---

## 📄 LICENSE & TERMS

- **License**: Open Source (Apache 2.0)
- **Developer**: Pawan Washudev (Neubofy)
- **Status**: Active Development
- **Version**: 1.0.5 (Stability)

---

<div align="center">

**"Your data. Your focus. Your life. On YOUR terms."**

*Made with ❤️ and Intelligence by Pawan Washudev*

Built by someone who lost control of their own fingers. Designed for those who want it back.

<br><br>

[**🌐 Official Website**](https://reality.neubofy.in) • [**⬇️ Download Latest APK**](https://github.com/pawanwashudev-official/Reality/releases)

<br><br>

<a href='https://reality-digital-wellbeing-and-focus.en.uptodown.com/android' title='Download Reality - The Intelligent Life OS' >
    <img src='https://stc.utdstc.com/img/mediakit/download-aao-big.png' alt='Download Reality - The Intelligent Life OS'>
</a>

</div>
