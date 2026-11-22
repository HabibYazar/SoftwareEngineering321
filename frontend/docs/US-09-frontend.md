# US-09 doctor directory

Receptionist and Administrator can open `#/doctors` (backend permission `DOCTOR_DIRECTORY_VIEW`, migration V4). The directory provides All Departments, department selection by name, name search and Refresh. Admin demo account management uses its own route and model. [Story #201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) · [Frontend #205](https://github.com/HabibYazar/SoftwareEngineering321/issues/205)

| Development choice | Behavior |
| --- | --- |
| Production build | Always uses `GET /api/v1/staff`, `GET /api/v1/staff?department={name}` and `GET /api/v1/departments`. Demo and preview flags are ignored. |
| `VITE_STAFF_MOCK=true` | Synthetic doctors. Departments are inferred from their records. No preview HTTP calls. |
| `VITE_STAFF_HTTP_PREVIEW=true` | With staff mock off, uses `GET /api/v1/staff` and `GET /api/v1/staff?department={name}`. |
| `VITE_DEPARTMENTS_HTTP_PREVIEW=true` | Independently enables `GET /api/v1/departments` with canonical `{ id, name }[]` validation. |
| `VITE_DEPARTMENTS_PR251_COMPAT=true` | With department preview enabled, explicitly uses the isolated temporary `{ id, department }[]` adapter. |

A valid catalog exposes every department, including those with no clinicians. Missing catalog data leaves the successful doctor results usable with inferred departments. A successful department request never masks a failed clinician request. Endpoint absence, access denial, malformed responses and transient failures have distinct messages. HTTP errors never enable demo mode. Requests are canceled when filters change or the page closes.

Backend status (Sprint 2 integration): the staff endpoint now reads the Flyway `doctors` view (`staff_members`, `doctor_departments`, `departments`) instead of the non-existent `clinicians` table; the department endpoint returns the database catalog as `{ id, name }` with stable UUIDs; public access was replaced by the `DOCTOR_DIRECTORY_VIEW` permission. Synthetic demo departments and doctors come from the optional Flyway seed `db/seed` (enabled by `compose.yaml`). The earlier seed PR #228 was closed unmerged because it created conflicting tables. [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229) · [PR #251](https://github.com/HabibYazar/SoftwareEngineering321/pull/251)

Production retains the unavailable state. Node tests use injected synthetic HTTP responses for the previews, independent failures, name encoding, zero-doctor results and stale request protection. Live endpoints were not exercised. [Preview tests](../tests/directory-preview.test.mjs) · [Directory regression tests](../tests/staff.test.mjs)
