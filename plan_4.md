1. **Enhance Step 9 Error Logging**:
   - Update `step9_generatePlan` in `NightlyPhasePlanning.kt` to catch exactly what plan content is being sent to the AI and what the AI returned.
   - Update `failureJson` to include `planContent` snippet to diagnose if it was indeed empty or small.
   - Update `errorDetails` to surface the AI's actual `aiResponse` snippet if it's not JSON, so the user can see what the AI said (e.g., "AI replied: I cannot help with this..." instead of just "AI response was not valid JSON.").

2. **Enhance Step 14 Error Logging**:
   - Perform a similar enhancement for `step14_normalizeTasks` in `NightlyPhasePlanning.kt`.
   - When catching the exception for parsing `aiResponse` to `responseJson`, add a custom logged error to save the `aiResponse` snippet if parsing fails, so debugging AI outputs becomes possible.

3. **Run Tests**:
   - Run `./gradlew test` to verify there are no compilation errors.

4. **Run Pre-Commit Checks**:
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

5. **Submit the Fix**:
   - Commit and submit the code with an appropriate description once changes are verified.
