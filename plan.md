1. **Remove step 14 (Task Cleanup)**:
   - Remove `STEP_NORMALIZE_TASKS` from `NightlySteps.kt`, `NightlyProtocolExecutor.kt`, `NightlyActivity.kt`, and `NightlyPhasePlanning.kt` (delete `step14_normalizeTasks()` function entirely).
   - Remove the `DEFAULT_TASK_NORMALIZER_TEMPLATE` constant from `NightlyStepModels.kt`.
   - Update any Step item lists and step UI states in `NightlyActivity.kt` to exclude Step 14.

2. **Merge Steps 9, 10, 13, and 15 into a single Step 9 (`STEP_GENERATE_PLAN`)**:
   - The user wants step 9 to do the following: fetch content from docs, send to AI with a system prompt for plan extraction, and then *apply all changes* (which includes what 10, 13, and 15 did).
   - Step 9 (`step9_generatePlan()` in `NightlyPhasePlanning.kt`) currently fetches plan doc, calls AI, and saves the JSON.
   - Step 10 (`step10_processPlan()`) processes the JSON to create Tasks and Events.
   - Step 13 (`step13_setAlarm()`) sets the alarm based on the parsed plan.
   - Step 15 (`step15_updateDistraction()`) updates distraction limits based on the parsed plan.
   - The new `step9_generatePlan()` should perform all these actions sequentially:
     1. Fetch plan document content.
     2. Call `NightlyAIHelper.analyzePlan()` to get the JSON.
     3. Extract and parse tasks, events, `wakeupTime`, `sleepStartTime`, and `distractionTimeMinutes`.
     4. (Apply Step 10 logic) Create Google Tasks and Google Calendar Events using the parsed JSON.
     5. (Apply Step 13 logic) Set the Android Alarm Manager based on `wakeupTime`.
     6. (Apply Step 15 logic) Update the distraction limit preference using `distractionTimeMinutes`.
   - Delete the separate functions: `step10_processPlan()`, `step13_setAlarm()`, and `step15_updateDistraction()` from `NightlyPhasePlanning.kt`.
   - Remove references to `STEP_PROCESS_PLAN`, `STEP_SET_ALARM`, and `STEP_UPDATE_DISTRACTION` from `NightlyStepModels.kt`, `NightlyProtocolExecutor.kt`, and `NightlyActivity.kt`.
   - Since we are merging multiple steps into step 9, make sure Step 9's status reflects the success or failure of *all* these combined actions. Detailed progress can be communicated via `saveStepState` with `STATUS_RUNNING` and `listener.onStepStarted/onStepCompleted`.

3. **Pre-commit step**:
   - Run `pre_commit_instructions` tool to make sure proper testing, verifications, reviews and reflections are done.
