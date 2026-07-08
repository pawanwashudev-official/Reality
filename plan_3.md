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
   - Update `NightlyAIHelper.kt` prompt instructions in line 213 to more strictly enforce raw JSON output by changing `Return ONLY valid JSON format without markdown wrapping.` to `Return ONLY valid raw JSON format without markdown wrapping. Do not include any reasoning blocks or markdown code block formatting.`

5. **Fix `JSONObject` null parse error**:
   - In `NightlyPhasePlanning.kt` for `step9_generatePlan` and `step14_normalizeTasks`, when the fallback `cleanResponse` is empty or "null", it throws an exception because `JSONObject("null")` evaluates the string "null" or fails unexpectedly returning a special internal null object representation when dealing with Kotlin/Java interoperability. Update `JSONObject` parses to verify that `extractedJsonStr` is not empty or "null" string before parsing. We can replace `JSONObject(cleanResponse.trim())` and `JSONObject(extractedJsonStr.trim())` with checks or use `org.json.JSONTokener(extractedJsonStr).nextValue() as JSONObject` pattern which is safer or explicitly handle the "null" string case throwing an explicit Exception to get caught and propagated back.

6. **Run Tests**:
   - Run `./gradlew test` to ensure the changes are correct and have not introduced regressions.

7. **Run Pre-Commit Checks**:
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

8. **Submit the Fix**:
   - Commit and submit the code with an appropriate description once changes are verified.
