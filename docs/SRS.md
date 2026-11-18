# Software Requirements Specification (SRS) & Product Backlog

> **Status: Work in Progress (v0.1)** — This is a living draft, and each team member must fill out the functional requirements, acceptance criteria, and relevant non-functional requirements for their assigned user stories before Sprint 2 freeze. 

> Any changes to this document must be traceable. Entries must have a clear reason for existing / traceable link to the required functionality.

---

## 1. Document Control & Team Ownership
TBD
---

## 2. Refined Product Backlog (Sprint 2)

| Story ID | Issue | Epic | Title | Priority | Story Points | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **US-09** | `#201` | EP-03: Doctor & Staff Management | List doctors by department | Must | 3 | In Progress |
| *US-XX* | *#XXX* | *EP-XX: [Teammate Fill]* | *[Teammate Fill]* | *Must/Should* | *X* | *Planned* |

---

## 3. Functional Requirements

### 3.1 Epic EP-03: Doctor & Staff Management

#### US-09: List Doctors by Department (`#201`)
* **User Story:** As a receptionist, I want to view doctors filtered by department so that I can choose the right doctor for a patient.
* **Parent Acceptance Criteria:**
  1. All departments are listed from seeded database records.
  2. Selecting a specific department returns and displays only the doctors associated with that department.
  3. Only synthetic demonstration data is used (no real patient or personnel records).

##### Dependent Data Requirements (`#202` — Owner: `@huseyin-tutak`)
* **FR-09.1 (Department & Doctor Schema):** The relational database schema must enforce a foreign-key relationship (`department_id`) linking each doctor record to a valid department record.

##### Backend API Requirements (`#204` — Owner: `@Zmly007`)
* **FR-09.2 (Deterministic Database Seeding):** On application startup, Flyway migrations must deterministically populate synthetic departments and doctor-to-department associations without duplicating records across container restarts.
* **FR-09.3 (List All Departments Endpoint):** The backend REST API shall expose a `GET /api/departments` endpoint that queries the database and returns a JSON array of all available departments (including `id` and `name`) with HTTP `200 OK`.
* **FR-09.4 (Filter Doctors by Department Endpoint):** The backend REST API shall expose a `GET /api/departments/{departmentId}/doctors` (or `GET /api/doctors?departmentId={id}`) endpoint that returns a JSON array containing only doctors assigned to the specified `departmentId` with HTTP `200 OK`.
* **FR-09.5 (Invalid Department Handling):** If a client requests doctors for a non-existent `departmentId`, the backend shall return HTTP `404 Not Found` with a structured error payload rather than an unhandled server exception.
* **FR-09.6 (Empty Department Handling):** If a valid department exists but has zero assigned doctors, the backend shall return HTTP `200 OK` with an empty JSON array (`[]`).

##### Frontend Requirements (`#205` — Owner: `@ardaservetusta-lang`)
* **FR-09.7:** *[To be filled by `@ardaservetusta-lang` consulted by `@HabibYazar` — describe department dropdown/selector behavior and doctor list rendering]*

---

### 3.2 Additional User Stories *(To Be Filled by Assigned Members)*

#### US-XX: [Story Title] (`#XXX` — Owner: `@handle`)
* **User Story:** As a [role], I want [capability] so that [benefit].
* **Functional Requirements:**
  * **FR-XX.1:** The system shall...
* **Acceptance Criteria:**
  * [ ] Criterion 1

---


## 4. Non-Functional Requirements (NFRs) & Quality Measures

### NFR-01: Read Endpoint Response Latency (Performance Efficiency)
* **Premise:** API Endpoints must be responsive; Clinic receptionists must be able to look up departments and filter doctors without perceptible UI lag during peak registration hours.
* **Specification:** Under a sustained load of **50 concurrent virtual users** sending **10 requests per second (600 total requests over 60 seconds)** against a database seeded with **50 departments and 1,000 doctors**, the REST endpoints `GET /api/departments` and `GET /api/departments/{departmentId}/doctors` shall return responses with a **95th percentile (p95) latency of $\le 200\text{ ms}$**, a **99th percentile (p99) latency of $\le 500\text{ ms}$**, and a **0% HTTP 5xx error rate**.
* **Traceability:** Supports `US-09` (`#201`), `#204`.
* **Verification Method:** Automated load test executed via `k6` or `Apache JMeter` against the containerized stack in CI/staging, asserting `http_req_duration{p(95)} < 200` and `http_req_failed == 0`.

### NFR-02: Cold-Start Container Orchestration Time (Portability & Operability)
* **Premise:** Any developer or automated CI runner must be able to spin up the entire multi-container application stack reliably with a single command.
* **Specification:** On a clean host environment (Linux/macOS/Windows with Docker Engine $\ge 24.0$ and pre-cached base images), executing `docker compose up --build -d` shall build all application images, apply all Flyway database migrations, and transition the `db`, `backend`, and `frontend` services to a `healthy` / `running` state within **$\le 60\text{ seconds}$**, serving HTTP `200 OK` on `http://localhost:80`.
* **Traceability:** Infrastructure baseline for all Sprint 2 deliverables (`#224`).
* **Verification Method:** Executed automatically in GitHub Actions (`.github/workflows/docker-ci.yml`) on every pull request targeting `develop`, timed via shell execution bounds and verified with `docker compose ps` and `curl -f http://localhost:80`.

### NFR-03: Schema Determinism & Volume Persistence (Reliability & Data Integrity)
* **Premise:** Database schema changes must remain strictly synchronized with application code, and stored records must never be lost or duplicated when containers restart.
* **Specification:** 
  1. Hibernate ORM shall execute with `spring.jpa.hibernate.ddl-auto=validate`, throwing a fatal startup exception (`exit code != 0`) if any JPA entity diverges from the Flyway-managed PostgreSQL schema (`0` permitted schema drift).
  2. Executing `docker compose down` followed by `docker compose up -d` (without volume deletion) shall retain **100% of persisted records** across container lifecycles without re-executing already applied Flyway migrations (`0` duplicate seed rows).
* **Traceability:** Supports `#202`, `#204`.
* **Verification Method:** Integration test in CI verifying `flyway_schema_history` row counts and querying `SELECT COUNT(*) FROM departments` and `SELECT COUNT(*) FROM doctors` before and after a full container recreation cycle.

### NFR-04: Network Isolation, Least-Privilege Execution & Synthetic Data (Security & Privacy)
* **Premise:** The database must remain isolated from external network access, containers must run without root privileges, and no real personal data may enter the repository.
* **Specification:** 
  1. The PostgreSQL container (`db`) shall bind **0 ports** to the host network interface (`0.0.0.0` / `127.0.0.1`), accepting TCP connections exclusively on port `5432` from containers attached to the internal `app-network` bridge.
  2. Both the `backend` (JVM) and `frontend` (Nginx) runtime containers shall execute under non-root OS user accounts (`UID != 0`).
  3. All Flyway seed files (`V*__*.sql`) and test fixtures shall contain **0 real patient, doctor, or staff Personally Identifiable Information (PII)** records.
* **Traceability:** Enforces `US-09` Definition of Done (`#201`), `#202`.
* **Verification Method:** Static inspection of `docker-compose.yml` (absence of `ports:` under `db`), runtime check via `docker compose exec backend id -u` (asserting output $\neq 0$), and manual PR review prior to merge.

### NFR-05: Automated Test Coverage & Regression Gate (Maintainability)
* **Premise:** Core backend endpoints and business rules must be guarded by automated tests to prevent regressions as teammates integrate new features.
* **Specification:** Backend business logic, service filters, and REST controller endpoints shall maintain a minimum line and branch test coverage of **$\ge 50\%$**, with **100% of automated unit and integration tests passing** before any Pull Request can be merged into `develop`.
* **Traceability:** Enforces `US-09` Definition of Done (`#201`), `#204`, `#206`.
* **Verification Method:** Enforced via Maven (`mvn test jacoco:check`) inside GitHub Actions CI workflows and verified by QA acceptance tests in `#206`.
