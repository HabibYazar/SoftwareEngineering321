# Sprint 2 — Non-Functional Requirements and Quality Measures

**Project:** HMS Community — Team 15
**Baseline:** 23 November 2026
**Scope:** Educational, synthetic-data prototype
**Source:** [SRS](../SRS.md) and [data requirements](../SRS-data-requirements.md)

This specification separates required quality targets from observed verification results. An unmeasured target is not reported as satisfied.

## NFR-01 — Performance Efficiency

**Requirement:** Under 50 concurrent virtual users, 10 requests/second for 60 seconds (600 total requests), with 50 departments and 1,000 doctors, the department and clinician read APIs shall achieve:

- p95 response latency <= 200 ms.
- p99 response latency <= 500 ms.
- HTTP 5xx error rate = 0%.

**Interfaces:** `GET /api/v1/departments` and `GET /api/v1/staff?department=...`.

**Measurement:** k6 or JMeter. Report request count, concurrency, p95/p99, HTTP status distribution and seeded database size.

**Traceability:** US-09 / #201, backend #204.

**Status:** Target defined; no controlled load-test result verified. A successful smoke test does not establish latency compliance.

## NFR-02 — Container Operability

**Requirement:** With pre-cached base images and dependencies under an explicitly recorded local hardware configuration, the Docker stack shall build and reach usable frontend/backend states in <= 60 seconds.

**Measurement:** Time `docker compose up --build -d`, inspect PostgreSQL and backend health, verify frontend HTTP 200, and record host CPU/RAM plus cache conditions.

**Traceability:** Sprint 2 infrastructure and Docker Compose documentation.

**Status:** Local first-build image creation took approximately 247 seconds. The smoke test subsequently confirmed healthy services; the specified controlled, pre-cached timing condition has not been tested. Do not claim that the <= 60-second target passed.

## NFR-03 — Data Integrity and Reliability

**Requirements:**

- The PostgreSQL schema shall be installed through Flyway migrations with no duplicate migration versions.
- The application shall use JDBC with Flyway-managed schema validation rather than claiming Hibernate/JPA schema validation.
- Restarting the Docker services without removing volumes shall preserve all persisted records and create no duplicate synthetic seed records.
- A failed schema migration shall prevent an unsafe startup.

**Measurement:** Inspect `flyway_schema_history`, execute start/stop/restart sequences without `down -v`, compare record counts and identifiers, and run disposable PostgreSQL integration tests.

**Traceability:** US-01, US-03, US-06, US-08 and US-09.

**Status:** Flyway V1–V5 and synthetic seed insertion passed the local Docker smoke test. Persistence across container recreation and the full PostgreSQL test suite were not measured in that smoke test.

## NFR-04 — Security and Privacy

**Requirements:**

- The PostgreSQL service shall publish no host TCP port.
- Published application ports for local demo shall bind to `127.0.0.1`.
- The backend application runtime shall run as a non-root user.
- Access control shall be enforced server-side, with unknown permissions denied.
- Passwords shall be stored as secure hashes and real clinical data shall never be used for demonstration.
- Frontend runtime process privileges shall be evaluated independently; non-root frontend operation is a hardening target, not an already verified fact.

**Measurement:** Inspect `compose.yaml`, container user IDs, repository seed records and representative HTTP 401/403 authorization tests.

**Traceability:** US-01, US-02, US-03, US-06 and US-09.

**Status:** Compose configuration isolates PostgreSQL from host ports and binds frontend/backend to loopback. Backend Dockerfile uses UID 10001. Six demo accounts were verified with BCrypt hashes. The frontend Dockerfile uses the standard nginx image without an explicit non-root user; frontend non-root compliance must not be claimed. Exhaustive authorization checks remain a QA task.

## NFR-05 — Maintainability and Regression Control

**Requirements:**

- Every PR targeting a protected integration branch shall pass required backend, frontend and OpenAPI checks.
- The project shall aim for >= 50% backend unit line and branch coverage.
- Unit/integration test failures shall block merging when included in required CI.

**Measurement:** GitHub Actions checks, Maven tests, JaCoCo reports and a configured `jacoco:check` threshold.

**Traceability:** Cross-cutting; US-01, US-02, US-06–US-09.

**Status:** Three required CI checks passed on `main` commit `82cc567` in [run #45165435064](https://github.com/HabibYazar/SoftwareEngineering321/actions/runs/45165435064). JaCoCo reporting is configured, but a minimum coverage threshold is not currently enforced by the CI workflow. PostgreSQL-tagged integration tests require a disposable database and are not automatically included in default CI.

## NFR-06 — Audit Integrity and Footprint

**Requirements from the Data workstream:**

- Live audit storage including indexes <= 300 bytes/event.
- Packed audit storage <= 5 bytes/event.
- Packing 100,000 events <= 5 seconds.
- Opening the packed month as CSV <= 1 second.
- SHA-256 verification required before an archive replaces or restores live data.

**Measurement:** Monthly partition size queries and the documented `scripts/audit_archive.py` export, verify and open procedures.

**Traceability:** US-03 / #140, US-04 / #152.

**Previously reported measurements:** The Data requirements document reports 144 bytes heap + 109 bytes indexes per event, 1.8 packed bytes/event, about 1.2 seconds packing, 0.4 seconds verification and 0.35 seconds CSV opening on its stated PostgreSQL test environment.

**Status:** These measurements are sourced from the Data workstream documentation; they were not independently reproduced in the 23 November local Docker smoke test. Hardware/environment differences must be recorded in future repetitions.

## Quality verification matrix

| ID | Target defined | Evidence available | Independent verification |
|---|---|---|---|
| NFR-01 | Yes | No load-test report | Pending |
| NFR-02 | Yes | Healthy stack, first build ~247 s | Controlled timing pending |
| NFR-03 | Yes | Flyway V1–V5 passed | Restart/retention pending |
| NFR-04 | Yes | Docker, passwords, loopback checks | Partial; frontend user check pending |
| NFR-05 | Yes | Three successful GitHub Actions jobs | Coverage gate pending |
| NFR-06 | Yes | Prior Data workstream measurements | Independent reproduction pending |

## Verification references

- [GitHub Actions CI](https://github.com/HabibYazar/SoftwareEngineering321/actions/runs/45165435064)
- [Sprint 2 Docker smoke-test report](README.md)
- [Database schema documentation](../database/README.md)
- [GitHub Actions workflow](../../.github/workflows/ci.yml)
- [Backend test instructions](../../backend/README.md)

Quality targets must be re-evaluated before any real-world hospital deployment. This educational prototype must not be presented as clinically certified or production-ready.