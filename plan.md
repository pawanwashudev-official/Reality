1. *Fix NightlyPhasePlanning.kt syntax and build errors*
   - Add missing closing braces and properly structure `step15_updateDistraction()`.
   - Update `step16_backupToSheet()` placement.
   - Address missing imports and undefined references by properly importing constants or using fully qualified names.
2. *Fix NightlyStepModels.kt*
   - Define `STEP_SET_ALARM`, `STEP_NORMALIZE_TASKS`, and `STEP_UPDATE_DISTRACTION` which were missing or commented out.
   - Map these new constants to their names in `getStepName()`.
3. *Fix NightlyProtocolExecutor.kt*
   - Re-add the execution of step 13, 14, 15 into `executeSpecificStep()` and `executePlanningPhase()`.
4. *Fix NightlyAIHelper.kt*
   - Implement `normalizeTasks()` which was missing, to be called from Step 14.
   - It will format the prompt and use `callAIWorker()` to return the JSON for task normalization.
5. *Complete pre commit steps*
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.
6. *Submit the change.*
