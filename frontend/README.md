# HMS Community frontend

React, TypeScript and Vite. The interface is in English. Use synthetic demonstration data and accounts.

## Local development

Use the project's existing dependencies and Node 22.18 or later. From this directory in PowerShell:

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
$env:VITE_PATIENT_REGISTRATION_MOCK = 'true'
$env:VITE_PATIENT_SEARCH_MOCK = 'true'
$env:VITE_PATIENT_PROFILE_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

Open the local URL printed by Vite, usually http://127.0.0.1:5173. These flags can also be set in an ignored `.env.local`. Each flag is an independent, explicit development-only opt-in. Production builds exclude demo services. The doctor directory shows an availability message until the official staff endpoint is available. An HTTP failure displays an error and never switches services. Foundation health checks always use HTTP.

| Synthetic username | Role |
| --- | --- |
| admin.demo | Administrator |
| reception.demo | Receptionist |
| doctor.demo | Physician |
| nurse.demo | Nurse |
| care.demo | Patient Care Assistant |
| lab.demo | Lab Technician |
| cleaning.demo | Cleaning Staff |

All demo accounts use the synthetic password `DemoOnly!321`.

## US-09 doctor directory

Sign in as a Receptionist and select **Find a doctor** from the dashboard or sidebar. The route is `#/doctors`. A signed-out visit goes to login. Other roles return to their own workspace. The role restriction is enforced by the frontend. [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229) currently allows public HTTP reads of the staff endpoint.

The HTTP staff client is prepared for `GET /api/v1/staff` and `GET /api/v1/staff?department={departmentName}`. HTTP 200 returns an array, for example:

```json
[
  {
    "id": "00000000-0000-4000-8000-000000000001",
    "displayName": "Dr. Avery Rowan",
    "department": "Cardiology"
  }
]
```

The department selector uses the unique departments in the unfiltered response. Departments with zero doctors cannot be discovered through this endpoint. Department names and query values come from the API. The filtered request uses the original department value, and name search applies to the currently selected department. An empty response is `[]`.

The current integration baseline has no staff endpoint. Use the explicit staff demo flag to exercise directory filtering. The verified HTTP contracts, schema and security blockers, and local test steps are documented in the [Sprint 2 integration guide](docs/backend-integration/README.md).

## US-01 login and logout

The public routes are `#/login` and `#/foundation`. The foundation page includes the live health check. Each signed-in role has a workspace at `#/home/<Role>`.

The HTTP auth service follows [OpenAPI](../api/openapi.yaml). It posts `{ username, password }` to `/api/v1/auth/login`, expecting `{ accessToken, tokenType: 'Bearer', role, expiresAt }`, and posts to `/api/v1/auth/logout` with Bearer authorization. A 204 or 401 logout response ends the session. The auth requests time out after 15 seconds.

Access tokens live only in memory. Reloading requires another sign-in. Signing out clears local authentication immediately and reports failed server invalidation in the login view. The login form clears the password after each request.

## Shared theme

Use **Light** or **Dark** in the sidebar. On small screens, open **Menu** to reach the controls. The app follows the system scheme until the user chooses a theme. The only preference written to localStorage is `hms.theme`. Shared palettes, spacing and focus colors are defined in `src/theme/tokens.css`.

## Verification

```powershell
npm.cmd test
npm.cmd run build
```

The Node tests use injected HTTP responses. Run the [integration checks](docs/backend-integration/README.md#yerel-doğrulama) for registration, search, and profile editing. For browser checks, verify empty/invalid login, logout and direct route authorization, both themes and persistence across reload, tab focus and the narrow-screen menu, department changes, name search, empty results and retry after a failed staff response.

Acceptance references: [US-01 #122](https://github.com/HabibYazar/SoftwareEngineering321/issues/122), [US-01 #107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107), [US-09 #205](https://github.com/HabibYazar/SoftwareEngineering321/issues/205).
