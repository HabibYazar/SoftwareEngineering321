# US-06 patient registration

A receptionist can register a synthetic patient from the Receptionist home or sidebar at `#/patients/register`. The registration form covers the issue's initial data entry, missing and invalid field errors, condition-level rules, and duplicate national ID feedback. Changing a draft to Advanced immediately requires a relative contact. Updating a previously registered patient is a separate workflow. [Parent story #175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) · [Frontend issue #182](https://github.com/HabibYazar/SoftwareEngineering321/issues/182)

## Form and validation

| Field | Validation |
| --- | --- |
| First name and last name | Required text, up to 100 characters each |
| Synthetic national ID | Exactly 11 ASCII digits, kept as a string to preserve leading zeros |
| Date of birth | Required calendar date in `YYYY-MM-DD` format |
| Gender | Required text, up to 40 characters. The contract defines no fixed list. |
| Phone and address | Required text, up to 32 and 500 characters respectively |
| Condition level | Select Mild, Moderate, or Advanced |
| Relative contact | Full name, relationship, and phone are required for Advanced. For Mild or Moderate, use **Add relative contact** to send the group, and complete all three fields if enabled. Limits are 150, 80, and 32 characters. |

Validation messages are associated with the fields and the first invalid field receives focus. The submit action is disabled while the request is pending. A successful response clears the form. Errors leave the draft in place. A duplicate national ID focuses its field, and network, server, and malformed-response failures never report success. The form, UI messages, and tests use synthetic data. The ID format check does not establish that an entered value is synthetic.

The page uses `AppLayout`, `Sidebar`, the dashboard cards, and shared `Button`, `TextField`, `Card`, and `Alert` components. It inherits the warm beige and dark navy themes and shared focus rules. The form changes from two columns to one on a narrow screen. [Registration page](../src/patients/PatientRegistrationPage.tsx) · [Validation](../src/patients/validation.ts)

## Navigation and session

The registration link and route are available only to Receptionist. Signed-out visitors land on sign-in and other roles land on their own home. Home, Back, Forward, and other internal links ask for confirmation when leaving a dirty form. Known native history entries can restore the current entry when the warning is canceled. For an unfamiliar fragment, canceling restores the form route and restarts the app-controlled history range. Full-page reload and tab close use the browser's own unsaved-changes warning where the browser permits it. In-app Refresh is disabled on the form.

Draft details and the current session token stay in memory. A full reload requires signing in again. Leaving during a request cancels waiting in the client. The server may already have received the request. The shared protected request handler sends the current Bearer token. A current HTTP `401` prompts sign-in and unmounts the form. A `403` keeps the session and shows a local denial message. The frontend relies on server-side authorization and uniqueness enforcement. [US-02](US-02-frontend.md) · [Routing](../src/auth/routing.ts) · [Navigation history](../src/navigation/history.ts)

## API contract

`POST /api/v1/patients` uses `Content-Type: application/json` and `Authorization: Bearer <accessToken>` read from the existing in-memory session. Its `NewPatient` body contains `firstName`, `lastName`, `nationalId`, `dateOfBirth`, `gender`, `phone`, `address`, and `conditionLevel` (`MILD`, `MODERATE`, or `ADVANCED`). When included, `relativeInformation` is an object with `fullName`, `relationship`, and `phone`.

The service requires HTTP `201` and a valid `Patient` body, which adds a UUID `id` to the patient fields. The returned national ID must match the submitted value. It maps `400` to invalid submission, `401` to missing or expired authentication, `403` to denied permission, and `409` to the duplicate national-ID field message. It uses safe local messages because the documented problem body has no field-error array and may contain server details. There is a 15-second timeout and the client never automatically retries a POST. [OpenAPI](../../api/openapi.yaml) · [HTTP client](../src/patients/httpRegistrationService.ts)

## Synthetic demo and verification

Run from the frontend directory with the existing lockfile dependencies.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
$env:VITE_PATIENT_REGISTRATION_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

Sign in with the synthetic Receptionist credentials documented in the existing README. The demo starts with synthetic record `00000000001`. Use a different artificial 11-digit ID for success, then repeat it to test duplicate handling in the same page session. The mock stores demo records only in memory and resets when the app reloads. Production and development without the explicit flag use HTTP. HTTP failures never activate demo mode. [Mock service](../src/patients/mockRegistrationService.ts) · [Service selection](../src/patients/service.ts)

Use `npm.cmd test` and `npm.cmd run build`. The existing 30 tests cover authentication, doctor directory, and shared navigation. The registration tests cover form validation, synthetic duplicate protection, HTTP request and response contracts through injected fetch, network failures, route protection, and history guards. For browser checks, use both themes and repeat required-field, Advanced relative, success, duplicate, and navigation checks at desktop and phone widths. [Registration tests](../tests/registration.test.mjs) · [Technology versions](TECH_STACK.md)

In the local US-06 worktree, all 42 automated tests passed (30 existing and 12 new), and the TypeScript/Vite production build passed. With all mock flags supplied, the production bundle still excluded the known demo markers. Browser checks passed for synthetic registration, field errors and focus, duplicate handling, authorization, shared navigation, both themes, and desktop, 768 px tablet, and 390 px phone layouts. The automated history tests exercised cancellation of unsaved navigation. The browser automation could not resolve the browser-native confirmation dialog, so its accept/dismiss flow was not verified there.

The US-02 follow-up passed the full 68-test suite and production build. Its browser regression confirmed synthetic registration followed by an exact-ID search and covered navigation and the responsive layout. [US-02 verification](US-02-frontend.md#dependencies-demo-and-verification)

## Dependencies and integration

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

US-06 depends on US-02 permissions and US-03 according to its parent story. In this frontend it also reuses US-01 login, the shared US-09 layout and navigation, and the registration DTO and validator shared with US-07 search. The registration demo flag is `VITE_PATIENT_REGISTRATION_MOCK`. `VITE_AUTH_MOCK` supplies demo sign-in and `VITE_STAFF_MOCK` is optional for the directory. The documented `POST /api/v1/patients` and login are planned in the inherited backend and have not been verified live. End-to-end verification requires those operations, a valid Receptionist session, and server-side duplicate and relative-contact validation.

## Local development baseline

The local worktree is `SoftwareEngineering321-US06`, branch `feature/182-patient-registration`, based on `2011bc2a3fddb8c3127e026136fa7f5a759d2f10`. That base includes US-01, US-09, shared navigation, and the local merge `848cdc0` of the backend contributor’s [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229). These inherited changes precede US-06. Before publication, establish a clean frontend PR base containing the intended shared dependencies and verify that the prospective PR diff contains only reviewed US-06 changes. The current local branch should not be submitted directly to `develop`.
