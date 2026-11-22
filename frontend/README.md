# HMS Community frontend

HMS Community is an educational hospital-management prototype. The React frontend shares a role-aware layout, warm neutral and navy themes, and accessible forms. Use synthetic accounts and records in every demo.

| Story | Frontend workflow |
| --- | --- |
| US-01 and US-02 | Staff sign-in/sign-out and role-aware navigation |
| US-06, US-07 and US-08 | Register, search and edit patients |
| US-09 | Doctor directory with a synthetic demo and opt-in development HTTP previews |
| US-04 | Administrator audit viewer with synthetic, read-only events |
| US-05 | Administrator staff, role and department assignment simulation |

## Local development

Use Node 22.18 or later. With the existing locked dependencies available, run the commands below from `frontend`. Developers setting up the project separately can use `npm.cmd ci` to install the locked packages.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_PATIENT_REGISTRATION_MOCK = 'true'
$env:VITE_PATIENT_SEARCH_MOCK = 'true'
$env:VITE_PATIENT_PROFILE_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
$env:VITE_ADMIN_AUDIT_MOCK = 'true'
$env:VITE_ADMIN_STAFF_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

Open the loopback URL Vite prints. Flags default to false and are opt-in for development builds only. You can set them in an ignored `.env.local` for your own development session. Enable the three patient demo flags together to share the synthetic patient store across registration, search and editing.

| Synthetic username | Role |
| --- | --- |
| admin.demo | Administrator |
| reception.demo | Receptionist |
| doctor.demo | Physician |
| nurse.demo | Nurse |
| care.demo | Patient Care Assistant |
| lab.demo | Lab Technician |
| cleaning.demo | Cleaning Staff |

The sign-in fixtures share the synthetic password `DemoOnly!321`. Demo staff-management changes cannot change these sign-in accounts, passwords, app route permissions or any server state.

## HTTP mode and previews

With mock flags off, authentication and patient workflows use the merged versioned contracts through the existing `/api` proxy when a compatible backend is available. Search requires a national ID or name. The recent patient list contains only records registered in the current session. The server does not offer full patient listing.

To prepare US-09 against a compatible local development backend, disable `VITE_STAFF_MOCK` and enable the preview flags you need.

- `VITE_STAFF_HTTP_PREVIEW=true` enables the unmerged staff GET contract from PR #229.
- `VITE_DEPARTMENTS_HTTP_PREVIEW=true` enables the unmerged department GET contract from PR #251 and expects `{ id, name }`.
- `VITE_DEPARTMENTS_PR251_COMPAT=true` works only with the department preview flag. It explicitly selects the temporary `{ id, department }` adapter for PR #251.

An HTTP failure stays in HTTP mode and never enables mock data. These previews are disabled for production. The PRs still require schema, response and authorization alignment. Audit listing and staff account management have no approved HTTP endpoints. [Backend integration and blockers](docs/backend-integration/README.md)

## Verification

```powershell
npm.cmd test
npm.cmd run build
```

Tests use Node’s runner, injected HTTP responses and local Vite rendering. [Technology and verified versions](docs/TECH_STACK.md) · [US-04](docs/US-04-frontend.md) · [US-05](docs/US-05-frontend.md)
