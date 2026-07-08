1. **Fix `NightlyProtocolExecutor.kt` Execution Wrapping**:
   - Wrap `executeSpecificStep(step: Int)` contents in a `try-catch` block.
   - Invoke `listener.onError()` before re-throwing the exception to ensure errors are shown in the live terminal UI instead of silently failing.

2. **Fix Diary and Plan Docs Creation Regex**:
   - Update `NightlyPhaseData.kt` and `NightlyPhasePlanning.kt` to replace the invalid `Regex("[\\]+|[**]+|[##]+")` with the corrected `Regex("[\\*#]+")`.
   - This resolves the `PatternSyntaxException` that causes docs creation to constantly fail.

3. **Improve JSON Parsing in Step 9 and Step 14**:
   - In `NightlyPhasePlanning.kt`, update JSON boundary extraction (`indexOf('{')` and `lastIndexOf('}')`) logic.
   - Wrap the boundary searches in `try-catch` with fallbacks for extracting from Markdown blocks (e.g., parsing ```json), handling `<think>` tags returned by reasoning models safely to prevent parsing crashes.

4. **Improve JSON Parsing AI Prompting in `NightlyAIHelper`**:
   - We observed that AI is still sometimes outputting invalid JSON (or no JSON) resulting in `JSONObject$1 cannot be converted to JSONObject`.
   - Update `NightlyAIHelper.kt` prompt instructions in `getDefaultPlanPromptTemplate` and `getDefaultTaskCleanupPromptTemplate` to more strictly enforce raw JSON output and add JSON structure fallbacks in `NightlyPhasePlanning`.

5. **Run Tests**:
   - Run `./gradlew test` to ensure the changes are correct and have not introduced regressions.

6. **Run Pre-Commit Checks**:
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

7. **Submit the Fix**:
   - Commit and submit the code with an appropriate description once changes are verified.
