# US-09 doctor directory

A signed-in Receptionist can open **Find a doctor** from their home page or the sidebar. The directory at `#/doctors` supports selecting a department, viewing only its clinicians, and searching their names. Its scope is listing and filtering. [Frontend issue #205](https://github.com/HabibYazar/SoftwareEngineering321/issues/205) · [Parent story #201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) · [Backend PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229)

## Directory behavior

- The initial request loads the clinician catalog. Distinct department names become the selector options. **All Departments** shows the full list. Choosing a department sends its original name to the API and defensively excludes any returned rows from other departments.
- Name search is local to the visible result set. It trims the query and tolerates accents and Turkish dotless i. The directory distinguishes an empty catalog or filtered response from a search with no matches.
- Loading, network errors, access errors, server errors, and malformed responses are displayed explicitly. **Try again** repeats the failed query. The current request is canceled if the user leaves, changes department, or starts another refresh. Results from canceled requests cannot replace the new results.
- Shared **Refresh** reloads the clinician catalog from the configured service and refetches the selected department. It preserves the search and keeps the department when the latest catalog still contains it. If the department disappears, **All Departments** becomes active. Refresh stays on the current page and does not change browser history.

The directory uses the shared `Button`, `TextField`, `Card`, `Alert`, `AppLayout`, themes, and responsive styles. Other roles are redirected to their own home and signed-out visitors are redirected to sign-in. [Directory](../src/staff/DoctorDirectory.tsx) · [Query hook](../src/staff/useClinicians.ts) · [Filters](../src/staff/filtering.ts)

## Staff API

| Request | Purpose |
| --- | --- |
| `GET /api/v1/staff` | Load the clinician catalog and discover its departments. |
| `GET /api/v1/staff?department=<name>` | Fetch the exact selected department. The service URL-encodes the query value. |

Both successful responses are HTTP `200` with a JSON array. Each clinician has a UUID string `id` and string `displayName` and `department`. The frontend checks these fields, rejects blank names or departments and duplicate IDs, and treats an empty array as a successful result. The contract also lists `401` and `403`. [OpenAPI contract](../../api/openapi.yaml) · [HTTP service](../src/staff/httpStaffService.ts)

Departments with zero clinicians cannot be discovered from clinician rows. The audited seed snapshot contained clinicians in all eight listed departments. An independent department catalog would be needed if empty departments must be selectable. [Synthetic seed PR #228](https://github.com/HabibYazar/SoftwareEngineering321/pull/228)

OpenAPI declares Bearer authentication for the staff endpoint. The imported Spring security configuration permits public reads and the current staff service sends neither a Bearer token nor cookies. The Receptionist guard limits the UI only. Before relying on this workflow for real access control, align the server authorization policy and the client with the agreed contract. [Spring security configuration](../../backend/src/main/java/org/hmscommunity/security/HttpSecurityConfiguration.java)

## Demo and verification

Use `VITE_STAFF_MOCK=true` in Vite development mode to opt into a synthetic directory. `VITE_AUTH_MOCK` is independent. Both flags can be off, and the production bundle uses HTTP. A failed HTTP request never changes the service selection. [Staff service selection](../src/staff/service.ts) · [Synthetic staff service](../src/staff/mockStaffService.ts)

The verified baseline has eight automated staff tests for query encoding, filtering, empty arrays, cancellations, response validation, HTTP/network errors, demo data, and unauthorized routes. Controlled HTTP browser checks covered successful filtering, unrelated records in a filtered response, delayed and empty results, failure, invalid JSON, and retry. The controlled server used synthetic fixtures. These checks do not establish real Spring Boot/PostgreSQL integration. PR #229 has been imported locally, but its author used separate Compose and synthetic seed changes in #227 and #228 for real backend validation. Running the stack with the seed, verifying its responses, and checking server authorization remain integration tasks. [Staff tests](../tests/staff.test.mjs) · [Backend verification note](https://github.com/HabibYazar/SoftwareEngineering321/pull/229#issuecomment-7017463990)

To repeat the navigation regression checks, sign in with the demo Receptionist, open the directory, choose a department and type part of a name. Use Refresh and check the filter, search text, and role remain. Confirm the same route using app Back/Forward and native Back/Forward around a Home visit, which remounts the directory when revisited. At phone width, use Tab, Enter, and Escape on the page controls and collapsed sidebar. Repeat in both themes.

### Reproduce controlled HTTP refresh checks

From the frontend directory, start `node tests/fixtures/staff-refresh-server.mjs` in one terminal. In another terminal, set `VITE_AUTH_MOCK=true` and `VITE_STAFF_MOCK=false`, then start Vite. The fixture binds to the local API port used by the development proxy. Sign in as the demo Receptionist and select **Cardiology**.

| Type in the fixture terminal | Expected result after **Refresh** |
| --- | --- |
| `changed` | The clinician's updated name appears. The active department and search text remain. |
| `empty` | The active department stays selected and the empty results message appears. |
| `error` | The filtered request shows an error. Set `normal` and choose **Try again** to recover. |
| `catalog-error` | The catalog shows an error. Set `normal` and refresh to recover the selection. |
| `removed` | The catalog no longer lists Cardiology. **All Departments** becomes active. |

The fixture sends synthetic records only. Stop it with Ctrl+C when finished. [Controlled test fixture](../tests/fixtures/staff-refresh-server.mjs)

Local backend integration commit `848cdc0937d41c994a89057f36e3c78505f5dea1` imports PR #229. Local frontend commit `b7f5cf04c99c1a57aee04ae30b3a3734ffe5103c` is the current navigation baseline.
