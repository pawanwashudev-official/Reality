# App Codebase Improvements List

Based on a review of the Android codebase, here is a list of recommended improvements for the app:

## 1. UI / UX Polish & Modernization
- **Edge-to-Edge Design**: While some padding is calculated in `BaseActivity`, a full migration to Jetpack Compose or ensuring consistent Material 3 usage across all XML layouts would provide a more modern, cohesive look.
- **Animations**: Add subtle transitions (e.g., using `MaterialSharedAxis` or Jetpack Navigation animations) between activities and fragments. Currently, transitions might feel rigid.
- **Typography & Theming**: Standardize text sizes and styles in a centralized `styles.xml` or Compose Typography definition. Ensure dark mode and light mode contrast ratios meet accessibility guidelines.

## 2. Code Architecture & Maintainability
- **Migrate to Jetpack Compose**: The current app relies heavily on XML layouts and View-based UI. Gradually migrating screens (starting with simpler ones like Profile or Settings) to Jetpack Compose would significantly reduce boilerplate and improve UI reactivity.
- **Dependency Injection**: Implement Hilt/Dagger for dependency injection instead of manual instantiation. This will make testing easier and manage the lifecycle of singletons (like `GoogleAuthManager`, `FeatureManager`) more safely.
- **MVI / MVVM Pattern**: Ensure all business logic is decoupled from Activities/Fragments. Move logic into `ViewModel` classes, exposing state via `StateFlow` or `LiveData`. This reduces the size of classes like `ProfileActivity` and `NightlyProtocolActivity`.

## 3. Performance & Battery Optimization
- **Background Work Limitations**: Review the usage of `AlarmManager` and `JobScheduler`. Ensure they strictly adhere to Android 14+ background execution limits, using `WorkManager` for deferrable, guaranteed background tasks (like syncing data to Google Drive).
- **Service Lifecycles**: Ensure foreground services (like `TapasyaService`, `AppBlockerService`) are started and stopped gracefully, and handle potential `ForegroundServiceStartNotAllowedException` on newer Android versions.

## 4. Google Integration & Security
- **BYOK Flow Polish**: The current Desktop OAuth intercept flow (`ServerSocket` on localhost) can be fragile on some devices. Adding a more robust manual fallback UI or using AppAuth library (if custom URI schemes become an option) would be more stable.
- **Token Management**: Ensure access/refresh tokens are securely stored using `EncryptedSharedPreferences` rather than standard `SharedPreferences` to prevent unauthorized access on rooted devices.

## 5. Testing & CI/CD
- **Unit Tests**: Increase code coverage by adding comprehensive unit tests for business logic classes (`LevelManager`, `XPManager`, `NightlyPhasePlanning`).
- **UI Tests**: Add automated Espresso or Compose UI tests for critical flows (like the setup process and Nightly Protocol).

## 6. Accessibility
- **Content Descriptions**: Ensure all images, ImageButtons, and interactive custom views have localized `contentDescription` attributes for screen readers.
- **Touch Targets**: Verify that all clickable elements are at least 48x48dp as per Material Design guidelines.
