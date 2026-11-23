# Sprint 2 — Local Docker Demo and QA Checklist

**Project:** HMS Community — Team 15
**Environment:** Local educational demonstration, synthetic data only

## 1. Requirements

- Docker Desktop with Docker Compose
- Available local ports (defaults: HTTP 80, backend 8080)
- Repository checkout with the integrated Sprint 2 code

## 2. Administrator account

The six synthetic staff accounts are inserted by the local seed
migration. An Administrator is deliberately NOT automatically seeded.

For a brand-new database, configure the first administrator before
starting the backend. In PowerShell, run from the repository root:

```powershell
$env:HMS_HTTP_PORT = "18091"
$env:HMS_BACKEND_PORT = "18090"
$env:HMS_BOOTSTRAP_ADMIN_USERNAME = "admin.demo"

$secure = Read-Host "Choose a local admin password (12+ characters)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)

try {
    $env:HMS_BOOTSTRAP_ADMIN_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)

    docker compose -p hms_sprint2_review up --build -d

    if ($LASTEXITCODE -ne 0) {
        throw "Docker startup failed"
    }
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    Remove-Item Env:HMS_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:HMS_BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
}
```

Open http://127.0.0.1:18091 and sign in with `admin.demo`
and the password you selected.

**Important:**
- Bootstrap creates an administrator only when none exists.
- Starting an existing database will not reset an existing admin password.
- Do not put admin passwords in Git, screenshots or release notes.
- Do not use `docker compose down -v` unless intentionally deleting
  the local demonstration database.
- If using `.env`, keep it ignored by Git.

## 3. Synthetic staff accounts

The local Docker seed creates:

| Username | Role |
|---|---|
| reception.demo | Receptionist |
| doctor.demo | Physician |
| nurse.demo | Nurse |
| care.demo | Patient Care Assistant |
| lab.demo | Lab Technician |
| cleaning.demo | Cleaning Staff |

Their initial password is `DemoOnly!321` in the local synthetic seed.
Existing accounts and their passwords are not overwritten.

The `admin.demo` account mentioned in frontend development documentation
is also a MOCK fixture. It is not created automatically in Docker.

## 4. Sprint 2 QA checklist

| Check | Expected result | Evidence to record |
|---|---|---|
| Docker startup | Database and backend healthy; frontend available | `docker compose ps` |
| Admin login | Administrator role and workspace displayed | Local browser check |
| Staff login/logout | Valid login succeeds, logout invalidates the session | Browser and API check |
| Role-based access | Restricted routes/API operations reject unauthorized roles | HTTP 401/403 tests |
| Patient registration | Valid synthetic patient saved; invalid inputs rejected | Browser test |
| Patient search | Registered patient found by supported search criteria | Browser test |
| Patient profile/update | Saved edits visible after retrieval | Browser test |
| Doctor directory | Department list and filtered doctors displayed | Browser test |
| Audit events | Login and data actions recorded in PostgreSQL | Dedicated DB tests |
| CI | Backend, frontend and OpenAPI checks pass | GitHub Actions run |

Do not mark an item PASS until it has been executed and verified.

## 5. Intentional Sprint 2 limitations

- **Audit Logs page:** Audit event persistence exists, but the
  administrator listing API is not included in the approved Sprint 2
  scope. The UI displays an unavailable notice.
- **Staff Management page:** Administration simulation exists for
  frontend development, but the production Docker build has no
  approved staff-management API.
- **Appointments, examinations and medical records:** planned for
  Sprint 3 or later, not claimed as Sprint 2 implementations.
- Full PostgreSQL integration-suite results, browser-level E2E
  coverage and formal Product Owner acceptance require separate evidence.

## 6. Submission evidence

- [SRS](../SRS.md)
- [Refined backlog](refined-backlog.md)
- [NFR and quality measures](quality-measures.md)
- [C4 and UML](architecture.md)
- [Integration report](README.md)
- [Teams submission index](teams-submission.md)

The final `sprint-2` release must be tagged from `main` only after
all approved Sprint 2 pull requests have been merged.