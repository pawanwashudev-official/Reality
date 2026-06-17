Plan:
1. Update `fragment_welcome.xml`:
   - Change the header to "Reality" and tagline to "The Intelligent Life OS" or similar premium/cinematic taglines.
   - Update the feature list to highlight "Neural Engine," "Cinematic UI," "Premium Gamification," etc.
   - Use dynamic colors (e.g., `?attr/colorPrimary`) consistently.
   - Remove "What check I call you?" typo to "What should I call you?"

2. Update `fragment_onboarding_permissions.xml`:
   - Redesign cards to look more premium (e.g., using `?attr/colorSurfaceContainer` and better typography).
   - Add references to "Neural Engine required for..." in the descriptions.
   - Consistent typography using `?attr/colorOnSurface`.

3. Update `activity_appearance.xml`:
   - Clean up the UI, make sure "Elite Aesthetics" and "Neural Engine" themes are prominently featured or enabled by default.
   - Remove any deprecated or unused styles.

4. Apply changes, run `./gradlew assembleDebug`, verify, and submit.
