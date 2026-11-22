# US-01 staff sign-in and sign-out

US-01 covers the staff login form, clear validation and authentication errors, login/logout calls, and routing to the authenticated staff member's existing role home. The role landing pages are workspaces and do not implement the postponed US-02 permission matrix. [Frontend issue #122](https://github.com/HabibYazar/SoftwareEngineering321/issues/122) · [Parent story #107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107)

## Behavior

- `LoginPage` requires a username and password, trims the username and focuses the first missing field. It supports show/hide password, prevents duplicate submission while busy, clears the password after a request, and presents actionable messages for invalid credentials, rejected requests, service errors, and unexpected responses.
- `AuthProvider` checks the response through the authentication service and holds the returned token in a ref. The accepted role opens `#/home/<Role>`. Protected screens check the active role on direct visits and browser Back/Forward. The public Architecture page remains accessible without signing in.
- Signing out removes the local token and role immediately, starts a new app history range, and shows the sign-in page. The service attempts server invalidation and reports when confirmation fails. Browser Back or Forward after sign-out cannot bring back an authenticated screen.
- Browser reload, including F5, creates a new in-memory session, so the user must sign in again to use protected pages. Theme choice is retained independently.

The page uses `TextField`, `Button`, `Card`, and `Alert` inside shared `AppLayout`. The sidebar provides the role-aware Home action. Page Refresh is disabled for sign-in and role landing screens and does not submit the form. [Login form](../src/auth/LoginPage.tsx) · [Auth provider](../src/auth/AuthProvider.tsx) · [Route protection](../src/auth/ProtectedRoute.tsx) · [Layout](../src/components/AppLayout.tsx)

## API contract

| Operation | Request | Expected result |
| --- | --- | --- |
| `POST /api/v1/auth/login` | JSON with required string `username` and `password` | `200` with `accessToken` (nonempty string), `tokenType: "Bearer"`, and a supported `role`. The contract lists `400` and `401`. |
| `POST /api/v1/auth/logout` | Current token in `Authorization: Bearer <token>` | `204` confirms invalidation. The client accepts `401` as an already-invalid session. Other failures leave the user signed out locally and produce a notice. |

The client uses a 15-second timeout and sends the auth requests without cookies. It rejects malformed responses and does not store the token in URLs, cookies, or browser storage. [HTTP service](../src/auth/httpAuthService.ts) · [OpenAPI contract](../../api/openapi.yaml)

## Demo and verification

Set `VITE_AUTH_MOCK=true` when running Vite in development to use the synthetic accounts in the existing README. This mock recognizes the demo roles, rejects invalid credentials, and only simulates logout. Production uses HTTP and a failed real request never enables the mock. The imported backend does not implement the login and logout operations, so live token issuance and server-side revocation have not been verified. [Demo accounts](../README.md) · [Mock auth service](../src/auth/mockAuthService.ts)

The verified baseline has 15 auth tests covering payloads and status handling, malformed responses, network failures, demo roles, sign-out, and role redirects. The navigation tests exercise role transitions, unauthorized visits, native history after logout, full-document refresh behavior, and theme preference retention. Run `npm.cmd test` and `npm.cmd run build` from the frontend directory. [Auth tests](../tests/auth.test.mjs) · [Navigation tests](../tests/navigation.test.mjs)

For a browser regression check, submit an empty form, verify the first invalid field receives focus, then try invalid and valid synthetic credentials. Visit Architecture and the doctor directory as a Receptionist. Use app and native Back/Forward, then Home. Sign out and revisit the protected route through native Back/Forward. Finally, sign in again, choose a theme, and reload a protected route. The app should return to sign-in with the theme intact. Repeat keyboard navigation and Menu/Escape at phone width.

Local commit `8b097140387e256f704b6a685665803152d9ea60` introduced the US-01 UI. Local commit `b7f5cf04c99c1a57aee04ae30b3a3734ffe5103c` added the shared themes and directory. The current navigation work is based on that commit.
