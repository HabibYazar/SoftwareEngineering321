# US-08 patient profile editing

A Receptionist can update a patient’s information to keep the record current. The story depends on US-06 patient registration and US-03 audit logging. US-07 supplies patient selection, US-02 controls role access, and US-01 supplies the current session. [Parent issue #193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) · [Frontend issue #200](https://github.com/HabibYazar/SoftwareEngineering321/issues/200)

## Acceptance criteria

- Every patient detail except the national ID can be updated.
- Updates use the US-06 validation rules.
- Each update must be recorded in the server audit log with its before and after values.

The frontend supports the first two criteria with synthetic data. The third requires the backend and data implementation. Complete product acceptance also depends on the approved update contract and real-service verification.

## Workflow

From **Search patients**, submit an exact synthetic national ID or partial name. Choose **Edit profile** beside the intended patient. The profile route is `#/patients/edit/<uuid>`. The link carries the record UUID only. Receptionist is the only role with this grant. Other roles have no edit link and are redirected to their own workspace when they open a protected address. Signed-out visitors reach sign-in. The route provides a **Back to search** action if the UUID is missing, invalid, or unavailable.

The page loads the selected record before rendering fields and validates that the response matches its UUID. The synthetic national ID is displayed as text. It has no editable control and is absent from the draft and update model. The demo store selects allowed fields explicitly and preserves both stored identifiers.

The search criterion, selected search mode and results remain in memory while editing. Returning after a successful save repeats the last submitted query. A name change can move the record out of the previous name search. Leaving both screens or signing out clears that search state. Browser Back and Forward use the existing role checks. In-app Refresh is disabled on the profile. A full browser reload requires signing in again.

The editor shows loading, validation, saving, success, and failure states. It rejects duplicate submissions while a save is pending. Cancel, shared navigation, and sign-out use the existing unsaved form check. Full-page reload and tab close use the browser’s unsaved-change prompt where supported. A failed save preserves the draft, and an obsolete response cannot change a closed editor. [Routing](../src/auth/routing.ts) · [Editor](../src/patients/profile/PatientProfilePage.tsx) · [Save handling](../src/patients/profile/saveRunner.ts)

## Shared US-06 validation

| Editable field | Rule |
| --- | --- |
| First and last name | Required, up to 100 characters each |
| Date of birth | Required valid calendar date in `YYYY-MM-DD` format |
| Gender | Required free text, up to 40 characters |
| Phone and address | Required, up to 32 and 500 characters respectively |
| Condition level | Mild, Moderate, or Advanced |
| Relative contact | Full name (150), relationship (80), and phone (32). All three are required for Advanced and when optional contact information is included for Mild or Moderate. |

Moving to Advanced requires a valid complete contact before saving. Existing contact information is prefilled and remains when the condition changes. To remove it while the condition is Mild or Moderate, select **Remove relative contact** and save. The UI explicitly confirms what the next save will remove. The internal update model uses `null` for this intentional removal. US-06 keeps its separate 11-digit national ID validation when registering a new patient. [Shared fields](../src/patients/PatientDetailFields.tsx) · [Validation](../src/patients/validation.ts) · [US-06](US-06-frontend.md)

## Services and APIs

The typed `PatientProfileService` exposes `load`, `update`, and a `canUpdate` capability. `PatientProfileChanges` is a frontend model for allowed fields. It is independent of a future HTTP update DTO.

| HTTP operation | Use and current contract |
| --- | --- |
| `GET /api/v1/patients/{patientId}` | Profile load using the UUID path parameter and the current Bearer token. Expects `200 Patient` (UUID, national ID, required patient details and optional relative contact). `401`, `403` and `404` are handled separately. This operation is documented as planned in the local backend baseline. |
| `GET /api/v1/patients` | Inherited US-07 search using one `nationalId` or `name` query value and Bearer authorization. Expects `200 Patient[]`. |
| `POST /api/v1/patients` | Inherited US-06 creation with `NewPatient` JSON and Bearer authorization. Expects `201 Patient`. |
| `POST /api/v1/auth/login` and `POST /api/v1/auth/logout` | Inherited US-01 session operations. |

OpenAPI has no approved update operation at the profile path. HTTP mode uses the documented GET only and shows read-only values if GET succeeds. It disables Save and reports why, and a direct service update call rejects locally without a request. A current `401` clears the appropriate session. A `403` reports denied access and keeps the session. Late or canceled requests cannot invalidate a newer session. HTTP errors stay in HTTP mode. [OpenAPI](../../api/openapi.yaml) · [HTTP profile service](../src/patients/profile/httpProfileService.ts) · [US-02](US-02-frontend.md) · [US-07](US-07-frontend.md)

The backend must enforce access, national ID and record UUID immutability, US-06 validation, and any concurrency or update-conflict rules in the approved contract. US-03 and US-08 must record authoritative before-and-after values for each committed update on the server. The frontend does not emit an audit record. Live verification requires implemented retrieval, update and audit operations, synthetic data and a valid Receptionist session.

## Development demo

Use a local Vite development session and synthetic accounts. These flags default to false. Enable all three patient flags to register, search for and edit the same transient records.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_PATIENT_REGISTRATION_MOCK = 'true'
$env:VITE_PATIENT_SEARCH_MOCK = 'true'
$env:VITE_PATIENT_PROFILE_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

The staff flag is optional for this workflow. The shared store contains only synthetic demo records. A page reload resets the demo data. The mocks are excluded from production. [Environment example](../.env.example) · [Demo store](../src/patients/mockPatientStore.ts)

## Toolchain and verification

| Package | Declared range | Locked and locally installed version |
| --- | --- | --- |
| React | `^19.2.0` | `19.3.1` |
| React DOM | `^19.2.0` | `19.3.1` |
| TypeScript | `~5.9.0` | `5.9.3` |
| Vite | `^7.1.0` | `7.3.8` |
| `@vitejs/plugin-react` | `^5.0.0` | `5.2.0` |
| `@types/react` | `^19.2.0` | `19.3.0` |
| `@types/react-dom` | `^19.2.0` | `19.3.0` |

The frontend reuses React, the browser Fetch and History APIs, the shared UI and theme tokens, and Node’s built-in test runner. Local verification used Node.js `26.13.1` and npm `11.20.0`. [Manifest](../package.json) · [Lockfile](../package-lock.json) · [Technology stack](TECH_STACK.md)

```powershell
npm.cmd test
node --test tests/patient-profile.test.mjs
npm.cmd run build
```

The automated suites cover Receptionist access, disallowed roles, search-to-profile selection, mocked loading and updates, every allowed field, immutable IDs, condition and relative rules, failed or duplicate saves, canceled requests, unknown patients, HTTP response handling, the unavailable update contract, and unsaved history navigation. Full regression passed 87 of 87 tests, including 19 profile tests, and the TypeScript/Vite production build passed. [US-08 tests](../tests/patient-profile.test.mjs)

Desktop verification used synthetic accounts. It confirmed sign-in and sign-out, rejection of a signed-out and Administrator direct URL, keyboard profile selection, prefilled values, immutable national ID, Advanced field validation and focus, relative contact retention when moving to Moderate, a successful mock save, refreshed search results with the previous criterion, and app and native Back/Forward.

The final browser audit confirmed the profile at 768 px in dark mode and at 390 px in light mode, with two columns on tablet, one column on mobile and no horizontal overflow. It also verified mobile save and return to refreshed search, Escape focus in the sidebar, registration followed by profile selection, and doctor filtering and Refresh. In the native unsaved-change confirmation, dismissing the dialog kept the draft and accepting it returned to the previous search without saving the edit.

With the profile mock disabled, a separate local HTTP fixture returned synthetic responses. The browser verified a read-only profile and disabled Save on `200`, denied access with the session intact on `403`, the missing-profile state on `404`, and a return to sign-in on `401`. Browser Back after `401` did not restore the editor. The project backend and a real update or audit-log operation were not exercised.

## Local baseline

The local worktree `SoftwareEngineering321-US08` and branch `feature/200-patient-profile-editing` start at `9548979918731ec36aaa7b9c22191e9e38d0a1a0` to reuse the completed US-02 permission and session architecture plus US-01, US-06, US-07 and US-09. This history includes the earlier frontend changes and the local merge of backend [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229). A future `develop` PR requires separate review of its base and prospective diff.