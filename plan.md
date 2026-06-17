1. **Update Onboarding Initial Screen (Welcome)**
   - Modify `app/src/main/res/layout/fragment_welcome.xml`:
     - Change "Take Control of Your Focus" (line 43) to "Neural Engine Activated" to fit the premium cinematic tagline.
     - Update the features list text to highlight "Cinematic UI," "Neural Engine", and "Premium Gamification" by modifying the `android:text` attribute of the three `TextView` elements inside the features `LinearLayout` (lines 71, 93, 114).
     - Fix typo in name input hint: change `android:hint="What check I call you?"` (line 125) to `android:hint="What should I call you?"`.

2. **Update Onboarding Permissions Screen**
   - Modify `app/src/main/res/layout/fragment_onboarding_permissions.xml`:
     - Update colors to use dynamic themes: replace `@color/onboarding_bg` with `?android:colorBackground`, replace `@color/onboarding_text_primary` with `?attr/colorOnSurface`, replace `@color/onboarding_text_secondary` and `@color/onboarding_text_hint` with `?attr/colorOnSurfaceVariant`, replace `@color/onboarding_card_bg` with `?attr/colorSurfaceContainer`, replace `@color/onboarding_card_stroke` with `?attr/colorOutlineVariant`, replace `@color/onboarding_icon_pending` with `?attr/colorOnSurfaceVariant`, and replace `@color/onboarding_accent` with `?attr/colorPrimary`.
     - Update the header text (line 21): change `"🔐 Setup Permissions"` to `"Neural Engine Access"`.
     - Update the subheader text (line 30): change `"Reality needs these permissions to protect your focus. Tap each card to grant."` to `"The Neural Engine requires these permissions to protect your focus. Tap each card to grant."`.

3. **Update Appearance Settings Screen**
   - Modify `app/src/main/res/layout/activity_appearance.xml`:
     - Remove `android:visibility="gone"` (line 792) from the `LinearLayout` with ID `@+id/section_elite` to expose the premium customization options.
     - Update the text "Elite Aesthetics 3.0" (line 797) inside the `@+id/section_elite` layout to "Neural Engine / Cinematic Aesthetics".

4. **Verify Edits**
   - Run `git diff` to review all XML file modifications and ensure no unintended changes were made.

5. **Test**
   - Run `./gradlew assembleDebug` to verify compilation.
   - Run `./gradlew test` to ensure no regressions.

6. **Pre-commit**
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
