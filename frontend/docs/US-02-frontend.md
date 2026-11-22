# US-02 role-based access control

US-02 restricts staff navigation and workflows to the permissions of the signed-in role. This frontend implements the verified rules for the existing screens. Full acceptance depends on the Product Owner’s approved permission matrix and backend enforcement. [US-02 parent #130](https://github.com/HabibYazar/SoftwareEngineering321/issues/130) · [Frontend #135](https://github.com/HabibYazar/SoftwareEngineering321/issues/135) · [Backend #132](https://github.com/HabibYazar/SoftwareEngineering321/issues/132)

## Verified permissions

The complete approved matrix was unavailable in the repository and reviewed issues. The three grants below come from the relevant user stories. The dash indicates that the frontend currently denies access pending a documented grant. No role implicitly inherits another role’s permissions.

| Role | Own workspace | Register patients (US-06) | Search patients (US-07) | Doctor directory (US-09) |
| --- | --- | --- | --- | --- |
| Administrator | Yes | — | — | — |
| Receptionist | Yes | Yes | Yes | Yes |
| Physician | Yes | — | — | — |
| Nurse | Yes | — | — | — |
| Patient Care Assistant | Yes | — | — | — |
| Lab Technician | Yes | — | — | — |
| Cleaning Staff | Yes | — | — | — |

Architecture remains public. The six roles without verified workflow grants have their existing home and session controls. The Product Owner must confirm the full per-function matrix, including any additional or cross-role grants for the three existing workflows and the screen and action permissions for other functions. [Registration #175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) · [Search #186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) · [Doctor directory #201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201)

## Routing and authentication

The baseline already validates the seven US-01 role values, opens each role’s workspace after login, protects the three Receptionist workflows, and uses hash routes and browser history. US-02 centralizes the permission grants and the workflow definitions. The sidebar and workspace use the same filtered definitions. Route resolution and the render guard check the same permission before mounting a protected screen. A signed-out visitor reaches sign-in for protected addresses. An authenticated visitor without access reaches their own workspace. Direct links, browser Back/Forward, and app Back/Forward use the same checks. The sidebar Home link opens the active role’s workspace. In-app Refresh still repeats an eligible read, while browser reload requires a new sign-in. The existing unsaved registration guard remains in place for user-initiated navigation and sign-out. [Permissions](../src/auth/permissions.ts) · [Routing](../src/auth/routing.ts) · [History](../src/navigation/history.ts)

US-01 login accepts only a valid Bearer session and a recognized role. A single in-memory session supplies both React and the protected business HTTP clients. Login is busy while its request is pending and the protected screens remain unmounted until authentication is known. Logout clears the local session immediately and attempts server invalidation. Passwords, role data and tokens are kept out of browser storage. Theme preference remains local. [Session store](../src/auth/session.ts) · [Auth provider](../src/auth/AuthProvider.tsx) · [US-01](US-01-frontend.md)

A real protected request requires a session and the relevant frontend permission before it reaches the network. The client sends the current Bearer token. HTTP `401` from a current, uncanceled protected request clears the session and displays a sign-in notice. A stale or canceled response cannot clear a later session. HTTP `403` preserves the session and displays the service’s access message. Error messages come from local code and omit raw server error bodies. The client has no documented token-expiry timestamp or refresh endpoint. It detects server expiry or revocation on the next protected request that returns `401`. [Protected request transport](../src/auth/authorizedRequest.ts)

## API contracts and backend responsibilities

| Request | Frontend contract and local implementation status |
| --- | --- |
| `POST /api/v1/auth/login` | Sends JSON `{ username, password }`. Expects HTTP `200` and JSON `{ accessToken, tokenType: "Bearer", role }`. Invalid credentials return `401`. Planned in this backend baseline. |
| `POST /api/v1/auth/logout` | Sends a Bearer token. Expects `204`. A `401` also completes local sign-out. Planned. |
| `POST /api/v1/patients` | Sends Bearer and `NewPatient` JSON and expects `201 Patient`. Handles `400`, `401`, `403` and duplicate `409`. Planned. |
| `GET /api/v1/patients` | Sends Bearer and a single `nationalId` or `name` query value. Expects `200 Patient[]` and handles `400`, `401` and `403`. Planned. |
| `GET /api/v1/staff` | Sends Bearer, optionally with `department`. Expects `200` and an array of `{ id, displayName, department }`. The inherited PR #229 implements the directory and currently permits this endpoint without authentication. |
| `GET /api/v1/health` | The public architecture health check expects `200 Health`. Present in the scaffold and unaffected. |

Backend issue #132 owns role enforcement. The backend needs to validate tokens, return `401` for missing or invalid authentication, return `403` for authenticated callers without the required grant, and enforce authorization on every protected request. This includes aligning the inherited public staff endpoint with the agreed contract and verifying real login, logout, registration, and search with synthetic data. The frontend router, filters, and client-side checks govern the official UI. Server access requires separate enforcement. [OpenAPI](../../api/openapi.yaml) · [Backend #132](https://github.com/HabibYazar/SoftwareEngineering321/issues/132) · [US-06](US-06-frontend.md) · [US-07](US-07-frontend.md) · [US-09](US-09-frontend.md)

## Dependencies, demo and verification

| Package | Declared in package.json | Resolved in package-lock.json |
| --- | --- | --- |
| React | `^19.2.0` | `19.3.1` |
| React DOM | `^19.2.0` | `19.3.1` |
| TypeScript | `~5.9.0` | `5.9.3` |
| Vite | `^7.1.0` | `7.3.8` |
| `@vitejs/plugin-react` | `^5.0.0` | `5.2.0` |
| `@types/react` | `^19.2.0` | `19.3.0` |
| `@types/react-dom` | `^19.2.0` | `19.3.0` |

This change reuses React Context, the browser History and Fetch APIs, shared layout, theme tokens and controls, and Node’s `node:test`. No package was added. Local verification used Node.js `26.13.1` and npm `11.20.0`. [Manifest](../package.json) · [Lockfile](../package-lock.json) · [Technical stack](TECH_STACK.md)

Use synthetic accounts only. In Vite development mode set `VITE_AUTH_MOCK=true` for the seven demo roles and independently set `VITE_PATIENT_REGISTRATION_MOCK=true`, `VITE_PATIENT_SEARCH_MOCK=true` and `VITE_STAFF_MOCK=true` for synthetic workflows. Run `npm.cmd run dev -- --host 127.0.0.1` from the frontend. The mocks exercise the UI without backend tokens or server grants. HTTP clients use the selected service throughout a run. An HTTP error does not switch a service to demo mode.

Run `npm.cmd test`, `node --test tests/permissions.test.mjs`, and `npm.cmd run build` from the frontend. The permission tests cover all seven roles, denied and unknown paths, shared menu selection, browser history, local session changes, the protected request rules, and current, stale and canceled `401` responses. The existing suites retain US-01, US-06, US-07 and US-09 regressions. [Permission tests](../tests/permissions.test.mjs)

The full frontend suite passed 68 of 68 tests, including 13 permission tests, and the TypeScript/Vite production build passed. Browser checks used only synthetic accounts. At 1280 px, they checked all seven role homes, hidden links and direct-access denials for the six roles without grants, Receptionist registration and search, doctor filtering, Home, app and browser Back/Forward, Refresh and logout. At 768 px and 390 px, the tested pages had no horizontal overflow. The browser checks covered both themes, theme persistence across reload, keyboard search submission, and sidebar Escape and focus restoration. A separate loopback HTTP fixture demonstrated search `403` without exposing server details and `401` returning the current user to sign-in. The project backend was not exercised.

## Local development baseline

This worktree, `SoftwareEngineering321-US02`, starts from `386cec673c61a5d231d4c57ace4716ead9501921` on the local `feature/135-role-navigation-completion` branch. The base already provides US-01 login, US-06 registration, US-07 search, US-09 directory, shared theme and navigation. It inherits earlier frontend commits and the local merge of backend [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229). A future PR needs an agreed base containing the intended dependencies and a check that its diff contains only the intended US-02 changes.