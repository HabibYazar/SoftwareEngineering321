# Sprint 2 — Integration and Verification Report

**Project:** HMS Community — Hospital Management System
**Course:** SENG 321 / BM 321 — Team 15
**Date:** 23 November 2026
**Status:** Integrated into `main`, with successful CI and local Docker smoke tests.

## Requirements and quality deliverables

- [Software Requirements Specification](../SRS.md)
- [Refined Product Backlog](refined-backlog.md)
- [Non-Functional Requirements and Quality Measures](quality-measures.md)
- [C4 and UML architecture index](../architecture/README.md)
## 1. Delivered functionality

Sprint 2 integration includes:

- US-01: Staff authentication and logout.
- US-02: Role-based access control.
- US-03: Authentication/session audit infrastructure.
- US-06: Patient registration and validation.
- US-07: Patient search.
- US-08: Patient profile retrieval and update.
- US-09: Department and physician directory.
- Synthetic role-based staff accounts for local demonstrations.
- Flyway migrations and a local PostgreSQL-backed Docker stack.

Some administrative UI functions still use development-only synthetic implementations. This report does not claim that every planned HMS module is operational.

## 2. Integration evidence

| Pull request | Description | Outcome |
|---|---|---|
| [#257](https://github.com/HabibYazar/SoftwareEngineering321/pull/257) | Sprint 2 integration | Merged |
| [#258](https://github.com/HabibYazar/SoftwareEngineering321/pull/258) | Additional department and physician seed work | Merged into develop |
| [#259](https://github.com/HabibYazar/SoftwareEngineering321/pull/259) | Demo accounts and patient gender changes | Merged |
| [#261](https://github.com/HabibYazar/SoftwareEngineering321/pull/261) | Reconciliation of develop and main | Merged |
| [#260](https://github.com/HabibYazar/SoftwareEngineering321/pull/260) | Develop-to-main integration | Merged |

Verified main integration commit: `6f1b5e42fd85e33e10fcaee0a466a3b44e309091`.

## 3. Continuous integration

GitHub Actions CI completed successfully on the merged main commit.

- Backend build and automated tests: passed.
- Frontend TypeScript and production build: passed.
- OpenAPI validation: passed.

Evidence: [GitHub Actions run #45159729164](https://github.com/HabibYazar/SoftwareEngineering321/actions/runs/45159729164).

The normal backend CI configuration does not automatically run the PostgreSQL-tagged integration suite unless a dedicated database is configured.

## 4. Local Docker smoke test

**Environment:** Windows PowerShell, Docker Compose, PostgreSQL 17, Java 21 backend, React frontend.

**Test project:** `hms_sprint2_verify_20261123`.

The test stack used isolated Docker resources, with the backend bound to `127.0.0.1:18080` and frontend to `127.0.0.1:18081`. No existing database volume was deleted.

| Test case | Observed result | Status |
|---|---|---|
| Backend image build | Completed | PASS |
| Frontend image build | Completed | PASS |
| PostgreSQL startup | Healthy | PASS |
| Backend health | `UP` | PASS |
| Flyway migrations | V1–V5 successful | PASS |
| Synthetic staff accounts | 6 active accounts with BCrypt hashes | PASS |
| Staff authentication | 6/6 demo roles logged in | PASS |
| Administrator bootstrap/login | 1/1 successful | PASS |
| Active department data | 10 departments | PASS |
| Physician directory data | 40 doctors | PASS |
| GET `/api/v1/departments` | 10 entries; expected `{id, name}` fields | PASS |
| GET `/api/v1/staff?department=Kardiyoloji` | 7 cardiology doctors | PASS |
| Invalid patient gender (`Other`) | HTTP 400; gender field rejected | PASS |
| Frontend root request | HTTP 200 | PASS |

The initial department API assertion stopped because the PowerShell test harness incorrectly counted a JSON array. The test was corrected and repeated successfully without changing application code.

## 5. Database and demo-data approach

Versioned Flyway migrations V1–V5 define and update the core schema, permissions and patient gender constraints.

Repeatable migrations under `backend/src/main/resources/db/seed` are enabled for the local demonstration through `HMS_FLYWAY_LOCATIONS`. They provide synthetic staff accounts and department/physician directory records.

A duplicate Flyway V4 version was resolved during integration. The additional physician seed was retained in a separate local-demo repeatable migration rather than a competing V4 schema migration.

Synthetic passwords and demo records must not be used in any deployed clinical environment.

## 6. Boundaries and remaining verification

The completed smoke tests establish that the integrated stack starts, database migrations run, synthetic accounts authenticate, the directory APIs return data, invalid gender values are rejected and the frontend is served.

They do not establish complete end-to-end coverage of every UI workflow, production security readiness, all valid patient-registration paths, or performance and load requirements.

Further work should include:

- Running the database-backed PostgreSQL integration test suite in a disposable environment.
- Browser-based walkthroughs of patient registration, search, update and role restrictions.
- Reviewing accessibility and error handling.
- Checking sprint backlog completion and contributor evidence.
- Confirming the instructor's submission format and release-tag requirements.

## 7. Relevant references

- [Repository README](../../README.md)
- [Backend setup and tests](../../backend/README.md)
- [Frontend setup and tests](../../frontend/README.md)
- [OpenAPI contract](../../api/openapi.yaml)
- [Database documentation](../database/README.md)

**Conclusion:** Sprint 2 code integration, required CI checks and the documented local Docker smoke tests have passed. Additional database-suite and browser-level verification remain separate activities.