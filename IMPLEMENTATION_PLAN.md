# 🛠️ Reality App - Master Implementation Plan

**Created**: 2026-01-16 12:55  
**Based on**: Developer feedback on BUG_ANALYSIS.md  
**Status**: ✅ ALL PHASES COMPLETE | Ready for Testing  
**Last Build**: 2026-01-16 13:24 | BUILD SUCCESSFUL

---

## 🎉 ALL CHANGES COMPLETED (Build Successful)

### Phase 1: Critical Bug Fixes ✅
- [x] Fixed dismissed reminder check in `AlarmScheduler` (skip events dismissed today)
- [x] Fixed snooze cancellation (track all snooze codes, cancel them all)
- [x] Fixed snooze source field (pass source and originalId for proper dismissal)
- [x] Added Focus Mode auto-cleanup in `BlockCache.rebuildBox()`
- [x] Emergency Mode already works instantly (checked - no change needed)
- [x] Fixed source field passing through entire reminder chain (AlarmScheduler → ReminderReceiver → AlarmService → AlarmActivity)

### Phase 2: Calendar Sync Improvement ✅
- [x] Implemented smart diff sync (insert/update/delete based on changes)
- [x] Added day-change detection (clear all on new day)
- [x] Single timestamp overwrite (not accumulate)
- [x] Added deleteByEventId DAO method for proper cleanup

### Phase 3: Reminder System Overhaul ✅
- [x] Added midnight refresh alarm (schedules at 00:01 when no events left today)
- [x] Repeat reminders: dismissal marks for today only, will fire on next repeat
- [x] One-time custom reminders: deleted after dismissal
- [x] Fixed AlarmActivity to use ScheduleManager.markAsDismissed() for all sources

### Phase 4: Per-App Mode Selection ✅ (Backend Complete)
- [x] Added `BlockedAppConfig` data class in Constants.kt
- [x] Added save/load methods in SavedPreferencesLoader.kt
- [x] Modified BlockCache to filter apps by mode (Focus/Bedtime/AutoFocus/Calendar)
- [ ] UI for per-app mode selection (future enhancement)

# 🏗️ ARCHITECTURE REDESIGN

## Part 1: Reminder System Overhaul

### Current Problems:
- 2 types of reminders (Custom + Schedule-synced) not handled consistently
- Snooze reminders get deleted by cleanup logic
- Past reminders not auto-deleted
- Schedule updates don't propagate to reminders

### New Architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│                    REMINDER SOURCE TYPES                        │
├─────────────────────────────────────────────────────────────────┤
│  1. Custom Reminders     - User-created standalone reminders    │
│  2. Schedule Reminders   - Auto-generated from AutoFocusHours   │
│  3. Calendar Reminders   - Auto-generated from synced calendar  │
│  4. Snooze Reminders     - Temporary, auto-expire               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    UNIFIED REMINDER TABLE (Room DB)             │
├─────────────────────────────────────────────────────────────────┤
│  id: String (UUID)                                              │
│  sourceType: CUSTOM | SCHEDULE | CALENDAR | SNOOZE              │
│  sourceId: String (links to original schedule/calendar event)   │
│  title: String                                                  │
│  triggerTime: Long (absolute timestamp)                         │
│  status: PENDING | FIRED | DISMISSED | SNOOZED                  │
│  repeatDays: List<Int> (empty = one-time)                       │
│  snoozeUntil: Long? (for snoozed reminders)                     │
│  createdAt: Long                                                │
│  lastModified: Long                                             │
└─────────────────────────────────────────────────────────────────┘
```

### Key Behaviors:

#### A. Schedule Updates Propagation
```
When AutoFocusHours list is modified:
  1. Find all reminders with sourceType=SCHEDULE
  2. For each schedule in new list:
     - If matching reminder exists → UPDATE trigger time
     - If no matching reminder → CREATE new reminder
  3. For each existing reminder:
     - If source schedule deleted → DELETE reminder
```

#### B. Snooze Handling (Critical Fix)
```
Snooze Reminder Lifecycle:
  1. User snoozes → CREATE new reminder with:
     - sourceType = SNOOZE
     - sourceId = original reminder ID
     - triggerTime = now + snoozeMinutes
     - status = PENDING
  
  2. Snooze fires → 
     - Mark as FIRED
     - User can dismiss (DISMISSED) or snooze again (new SNOOZE)
  
  3. Cleanup Logic:
     - ONLY delete if status=DISMISSED AND triggerTime < now
     - NEVER auto-delete PENDING snoozes (they have future triggerTime!)
```

#### C. Repeat Logic (Tomorrow Scheduling)
```
When reminder is DISMISSED:
  If repeatDays.isEmpty():
    → DELETE reminder (one-time, done)
  Else:
    → Calculate next occurrence
    → UPDATE triggerTime to next occurrence
    → SET status = PENDING
```

---

## Part 2: Per-App Blocking Mode Selection

### New Data Model:

```kotlin
// In BlockedAppEntity or modify existing structure
data class BlockedAppConfig(
    val packageName: String,
    val blockInFocus: Boolean = true,      // Custom Focus Mode
    val blockInAutoFocus: Boolean = true,  // Scheduled Auto Focus
    val blockInBedtime: Boolean = true,    // Bedtime Mode
    val blockInCalendar: Boolean = true    // Calendar Events
)
```

### UI Changes:
```
App Selection Screen:
┌─────────────────────────────────────────┐
│ ☑️ Instagram                      [▼]   │  ← Tap [▼] to expand
├─────────────────────────────────────────┤
│   Block during:                         │
│   ☑️ Custom Focus                       │
│   ☑️ Auto Focus (Schedules)             │
│   ☑️ Bedtime Mode                       │
│   ☑️ Calendar Events                    │
└─────────────────────────────────────────┘
```

### BlockCache Changes:
```kotlin
// In rebuildBox()
if (isFocusActive) {
    blocklist.filter { it.blockInFocus }.forEach { add(it.packageName) }
}
if (isScheduleActive) {
    blocklist.filter { it.blockInAutoFocus }.forEach { add(it.packageName) }
}
if (isBedtimeActive) {
    blocklist.filter { it.blockInBedtime }.forEach { add(it.packageName) }
}
if (isCalendarEventActive) {
    blocklist.filter { it.blockInCalendar }.forEach { add(it.packageName) }
}
```

---

## Part 3: Emergency Mode Fix

### Current Bug:
During Focus Mode, activating Emergency doesn't clear the BlockCache immediately.

### Fix:
```kotlin
// When Emergency Mode is activated:
fun activateEmergencyMode(context: Context) {
    val prefs = SavedPreferencesLoader(context)
    val data = prefs.getEmergencyData()
    
    // Set end time
    data.currentSessionEndTime = System.currentTimeMillis() + EMERGENCY_DURATION_MS
    data.usesRemaining--
    prefs.saveEmergencyData(data)
    
    // CRITICAL: Immediately rebuild BlockCache to clear blocked apps
    CoroutineScope(Dispatchers.IO).launch {
        BlockCache.rebuildBox(context)  // This will see emergencyEndTime > now → return empty
    }
    
    // Notify service
    context.sendBroadcast(Intent("com.neubofy.reality.refresh.focus_mode"))
}
```

---

## Part 4: Smart Calendar Sync

### Current Problems:
- Deletes all events before fetching new ones
- Doesn't do intelligent diff
- Timestamps accumulate

### New Algorithm:
```
Smart Calendar Sync:
  1. Get today's date
  2. If lastSyncDate != today:
     → DELETE all calendar events (fresh day start)
     → UPDATE lastSyncDate = today (overwrite, don't append!)
  3. Fetch new events from system calendar
  4. For each fetched event:
     - Find existing by eventId
     - If found AND (title matches AND time matches):
       → No change needed (skip)
     - If found AND (title OR time changed):
       → UPDATE event (edit)
     - If NOT found:
       → INSERT new event
  5. For each existing event NOT in fetched list:
     → DELETE (calendar event was removed)
  6. Trigger reminder regeneration for updated events
```

---

## Part 5: Focus Mode Auto-Cleanup

### Current Bug:
Focus Mode `isTurnedOn` stays `true` in SharedPrefs even after expiry.

### Fix Locations:
1. **HeartbeatWorker** - Check and cleanup on every 15-min pulse
2. **BlockCache.rebuildBox()** - Check and cleanup during cache rebuild
3. **BootReceiver** - Check and cleanup on device boot

```kotlin
// Add to HeartbeatWorker.doWork() and BlockCache.rebuildBox()
private fun cleanupExpiredFocusMode(context: Context) {
    val prefs = SavedPreferencesLoader(context)
    val focusData = prefs.getFocusModeData()
    
    if (focusData.isTurnedOn && focusData.endTime <= System.currentTimeMillis()) {
        focusData.isTurnedOn = false
        prefs.saveFocusModeData(focusData)
        TerminalLogger.log("CLEANUP: Expired Focus Mode cleared")
    }
}
```

---

# 📁 FILES TO MODIFY

## Priority 1: Critical Fixes
| File | Changes |
|------|---------|
| `AlarmScheduler.kt` | Add dismissed check, fix snooze source, fix cancel logic |
| `ScheduleManager.kt` | Add intelligent delete logic, next-day scheduling |
| `BlockCache.kt` | Add per-app mode filtering, emergency instant clear |
| `ReminderReceiver.kt` | Handle snooze source correctly |

## Priority 2: New Features  
| File | Changes |
|------|---------|
| `Constants.kt` | Add `BlockedAppConfig` data class |
| `SavedPreferencesLoader.kt` | Add save/load for per-app configs |
| `CalendarSyncWorker.kt` | Implement smart diff sync |
| `HeartbeatWorker.kt` | Add focus cleanup check |

## Priority 3: Database
| File | Changes |
|------|---------|
| `AppDatabase.kt` | Add UnifiedReminder table (optional, for robust tracking) |
| `ReminderDao.kt` | New DAO for reminder operations |

---

# 🔄 ANDROID ALARM MANAGER CLARIFICATIONS

### Q: Can it set multiple alarms at once?
**A: YES.** Each alarm needs a unique `PendingIntent` request code. We can have:
- 1001 → Main reminder
- 2001 → Snooze for Reminder A
- 2002 → Snooze for Reminder B
- etc.

### Q: Can alarms sync on every change?
**A: YES.** Call `AlarmScheduler.scheduleNextAlarm()` after:
- Any reminder created/edited/deleted
- Schedule created/edited/deleted
- Calendar sync completes
- Snooze created
- Reminder dismissed

### Q: Best Practice for Our Use Case
```
Strategy: "Next Alarm Only" + Snooze Queue

1. MAIN ALARM (Request Code 1001):
   - Always holds the NEXT upcoming reminder
   - Recalculated after every change
   - When it fires → recalculate next

2. SNOOZE ALARMS (Request Codes 2000+):
   - Each snooze gets unique code based on ID hash
   - Track active snooze codes in SharedPreferences Set<Int>
   - When cancelling all → iterate and cancel each tracked code
```

---

# 🚀 IMPLEMENTATION ORDER

## Phase 1: Critical Bug Fixes (Do First)
1. [ ] Fix dismissed reminder check in `AlarmScheduler`
2. [ ] Fix snooze cancellation (track codes)
3. [ ] Fix snooze source field
4. [ ] Fix Emergency Mode instant cache clear
5. [ ] Add Focus Mode auto-cleanup

## Phase 2: Calendar Sync Improvement
6. [ ] Implement smart diff sync in `CalendarSyncWorker`
7. [ ] Add single timestamp overwrite (not accumulate)

## Phase 3: Reminder System Overhaul
8. [ ] Implement next-day scheduling logic
9. [ ] Implement repeat auto-reschedule on dismiss
10. [ ] One-time reminders auto-delete after completion

## Phase 4: Per-App Mode Selection (Feature)
11. [ ] Add `BlockedAppConfig` data model
12. [ ] Modify `BlockCache` to filter by mode
13. [ ] Update UI to show mode selection per app

---

# ✅ ACCEPTANCE CRITERIA

### Reminder System
- [ ] Dismissed reminder doesn't fire again until next repeat cycle
- [ ] Snooze can be cancelled when reminders are disabled
- [ ] Snooze can be permanently dismissed
- [ ] One-time reminders are deleted after completion
- [ ] Repeating reminders auto-schedule for next occurrence
- [ ] 11:55 PM → 6:00 AM tomorrow alarm works

### Calendar Sync
- [ ] Same-day re-sync doesn't duplicate events
- [ ] Matching title+time = no edit
- [ ] New events = create
- [ ] Missing events = delete
- [ ] Day change = fresh sync
- [ ] No timestamp accumulation

### Emergency Mode
- [ ] Activating Emergency immediately unblocks apps
- [ ] BlockCache returns empty during Emergency

### Focus Mode
- [ ] Expired Focus Mode auto-clears in background
- [ ] No stale `isTurnedOn = true` in SharedPrefs

---

**Ready to proceed?** Reply with which phase to start, or "all" to begin Phase 1.
