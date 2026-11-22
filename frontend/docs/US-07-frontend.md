# US-07 patient search

US-07 lets a Receptionist find a synthetic patient by exact national ID or find every matching patient by partial name. An empty search result displays **No patient found**. The page shows names and national IDs from the existing Patient DTO. Patient editing and other patient workflows are outside this story. [Parent issue #186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) · [Frontend issue #189](https://github.com/HabibYazar/SoftwareEngineering321/issues/189)

## Search and navigation

Open **Search patients** from the Receptionist home or sidebar. The route is `#/patients/search`. Signed-out visitors are redirected to login. Other roles are redirected to their own workspace. Sidebar visibility and direct route access share the US-02 permission rule. The backend remains responsible for authorizing requests. [US-02](US-02-frontend.md)

- **National ID** accepts exactly 11 ASCII digits, preserving leading zeros.
- **Name** accepts a nonblank name or fragment up to 150 characters. Leading and trailing whitespace is trimmed. Spaces inside the name are preserved and URL-encoded.
- **Search** submits the current mode. Enter works from the query input. **Clear** removes the query and result and returns focus to the input.
- The search distinguishes the initial, loading, success, empty, invalid input, access-denied, and service error states. Field errors are linked to the query input. Status changes are announced, and transient network or server failures offer **Try again**.
- Editing a criterion or leaving the page cancels the active request. Responses from canceled requests cannot overwrite the current result.
- The shared **Refresh** repeats the most recently submitted query while the criteria remain unchanged. It is disabled before the first valid submission or after editing or clearing the criteria. Home, Back, and Forward use the existing navigation. The page does not retain its criteria after unmounting.

The page uses the shared layout, sidebar, cards, fields, buttons, alerts, token colors, and focus indicators. Cards wrap into a single column when space is limited. The search does not add the criteria or results to browser storage or application history.

## HTTP contract

The OpenAPI contract describes `GET /api/v1/patients` with either `?nationalId=<11-digits>` or `?name=<fragment>`. A request uses one parameter and the existing in-memory Bearer token. The client expects HTTP `200` with an array of `Patient` records. `[]` is a successful empty result. Each `Patient` contains a UUID `id` plus the `NewPatient` fields from US-06, including optional `relativeInformation`. US-07 reuses that type and runtime validator. [Patient fields](US-06-frontend.md#api-contract)

The server determines partial-name matching. The UI displays all valid records returned by that query, without client-side filtering or pagination. An ID response is rejected if it contains an unrelated ID or multiple patients. Invalid JSON, malformed records, conflicting response IDs and duplicate record IDs are handled as response errors. The client handles documented `400`, `401`, and `403` statuses, other HTTP errors, a 15-second timeout, and network failures without displaying raw server details. A current `401` from the protected request prompts sign-in and clears the visible results. A `403` shows access feedback and keeps the session. [OpenAPI](../../api/openapi.yaml) · [HTTP search service](../src/patients/search/httpSearchService.ts)

The endpoint is listed as planned and is absent from the local backend baseline. Live verification requires the implemented search endpoint, a valid Receptionist login, server-side authorization and a synthetic patient dataset. Verify exact ID lookup, partial-name behavior, and the complete result set against the real backend once available.

## Development demo

Use existing dependencies and run from the frontend directory.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_PATIENT_SEARCH_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

Use the existing README's synthetic Receptionist account. Try synthetic ID `00000000001` or name fragment `Avery` for matches and `00000000999` for an empty result. The name mock uses case-insensitive substring matching. If `VITE_PATIENT_REGISTRATION_MOCK=true` is also set, records registered during that browser session become searchable in the shared in-memory synthetic store. A reload resets the store. The search flag defaults to false and only works in Vite development mode. HTTP errors never activate the demo. [Synthetic store](../src/patients/mockPatientStore.ts) · [Service selection](../src/patients/search/service.ts)

## Files and verification

- Added the [search page, query model, request runner, HTTP and mock services, and styles](../src/patients/search/), the [shared demo store](../src/patients/mockPatientStore.ts), this document, and the [US-07 tests](../tests/patient-search.test.mjs).
- Updated the [app routing](../src/App.tsx), [startup](../src/main.tsx), [role routes](../src/auth/routing.ts), [sidebar](../src/components/Sidebar.tsx), [Receptionist workspace](../src/pages/WorkspacePage.tsx), [mock registration](../src/patients/mockRegistrationService.ts), [registration service selection](../src/patients/service.ts), [shared identifier validation](../src/patients/validation.ts), [environment types](../src/vite-env.d.ts), and the [example environment file](../.env.example).

Run `npm.cmd test` and `npm.cmd run build`. The US-07 tests cover exact and partial search, multiple and empty results, invalid and empty input, query encoding, stale requests, error and retry states, role access, malformed responses, HTTP/mock separation, shared registration data and navigation.

The original US-07 worktree passed all 55 automated tests, including 13 for US-07, and the TypeScript/Vite production build. Production JavaScript excluded the known demo markers even with the demo flags supplied.

Browser checks used only synthetic data. They covered exact ID, partial name, multiple and empty results, invalid and empty criteria, loading, stale-result cancellation, Clear, Refresh, keyboard focus, and direct route protection. In a separate HTTP test session with the search mock disabled, a controlled local server returned a transient 503, successful retry, malformed response, and 403. US-01 sign-in/sign-out, US-06 registration followed by search, US-09 filtering and Refresh, both themes, and desktop, 768 px tablet, and 390 px mobile layouts were also verified. This did not exercise the project backend.

The US-02 follow-up passed the full 68-test suite and production build. Its browser regression covered synthetic registration followed by exact-ID search, Refresh, role restrictions and controlled `401`/`403` responses. [US-02 verification](US-02-frontend.md#dependencies-demo-and-verification)

## Dependencies

| Package | Declared range | Locked version |
| --- | --- | --- |
| React | `^19.2.0` | `19.3.1` |
| React DOM | `^19.2.0` | `19.3.1` |
| TypeScript | `~5.9.0` | `5.9.3` |
| Vite | `^7.1.0` | `7.3.8` |
| `@vitejs/plugin-react` | `^5.0.0` | `5.2.0` |
| `@types/react` | `^19.2.0` | `19.3.0` |
| `@types/react-dom` | `^19.2.0` | `19.3.0` |

The ranges come from the manifest and the exact versions come from the lockfile. Local verification used Node.js `26.13.1` and npm `11.20.0`. The tests run on Node’s built-in test runner. [Manifest](../package.json) · [Lockfile](../package-lock.json) · [Technical stack](TECH_STACK.md)

US-07 depends on US-06 for the Patient DTO, validation and synthetic record store. It uses US-01 authentication, US-02 frontend permissions and the shared layout and navigation from US-09. Only `VITE_PATIENT_SEARCH_MOCK` switches search to synthetic data. `VITE_AUTH_MOCK` and `VITE_PATIENT_REGISTRATION_MOCK` independently enable demo sign-in and registration.

## Local baseline and publication

This worktree is on `feature/189-patient-search` at baseline `14c8154fc145dc94a5613cc1116b1049fff2ee6e`. Issue #186 depends on US-06. The baseline provides the Patient DTO, registration workflow, role access, and shared UI and navigation. It also inherits the previous frontend commits and the local merge of [backend PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229). These are pre-existing changes. Before a future PR, review the target ancestry and use a base that already contains the agreed dependencies so that the PR shows the US-07 changes alone.
