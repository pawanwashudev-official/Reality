1. **Refresh Token Flow on the Website:**
   - Create a new API route `/api/auth/refresh/route.ts` to handle refreshing tokens.
   - Update `website/src/app/tapashya/page.tsx` to save the `refresh_token` from the URL hash into `localStorage`.
   - Update `fetchTodayEvents` in `website/src/app/tapashya/page.tsx` to handle a 401 response by trying to refresh the access token using the stored `refresh_token`. If successful, retry the fetch. If it fails, log out the user.

2. **Mini Mode (Floating Clock) in Tapashya Web:**
   - Add a `isMiniMode` state variable.
   - When active, hide the rest of the page and render a smaller, floating clock that uses fixed positioning. Make it draggable using basic mouse/touch events.
   - Add a button/icon to toggle mini mode on and off.

3. **Edit Start Time in Tapashya Web:**
   - Add an edit icon next to the start time/duration of past sessions.
   - When clicked, open a dialog to let the user input a new start time (e.g., date and time picker).
   - Update the session data in `localStorage` and refresh the display.

4. **Tapasya XP Logic on Web:**
   - Add logic to calculate XP: `effectiveTimeMs / (15 * 60 * 1000)` fragments. The sum of XP is `sum(i * 15)` for `i=1` to `fragments`.
   - Display this XP on the session card or globally.

5. **Pre-commit Steps:**
   - Ensure the code conforms to ESLint guidelines, specifically using proper Next.js escaping inside JSX strings.
   - Run linter and check for build success.
