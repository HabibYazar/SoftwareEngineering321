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

- **FR-09.7 (Department Selection):** The frontend shall provide a keyboard-accessible department selector that presents available departments to an authorized Receptionist.
- **FR-09.8 (Doctor Filtering):** When a department is selected, the frontend shall display only clinicians associated with that department, including their display names and departments.
- **FR-09.9 (Results and Feedback):** The frontend shall display an appropriate loading indicator, a clear empty-results message when no clinicians match the selected department, and an error message when a request fails.
- **FR-09.10 (Access Control):** The frontend shall restrict the doctor directory to roles explicitly permitted by the approved role-permission matrix. Until additional permissions are approved, the verified Receptionist workflow shall be enabled and other roles denied.

**Verification Method:** Test department selection, filtered clinician results, empty responses, request failures, keyboard interaction and unauthorized navigation using synthetic demonstration data.

**Pending Integration:** The frontend currently derives department options from clinician records. A department without clinicians may not appear. Complete department coverage and the final API contract require backend/Product Owner confirmation.
---

### 3.2 Additional User Stories *(To Be Filled by Assigned Members)*

#### US-XX: [Story Title] (`#XXX` — Owner: `@handle`)
* **User Story:** As a [role], I want [capability] so that [benefit].
* **Functional Requirements:**
  * **FR-XX.1:** The system shall...
* **Acceptance Criteria:**
  * [ ] Criterion 1

---
#### US-01: Staff Login (`#107` — Frontend `#122`)

**Owner:** `@ardaservetusta-lang`

**User Story:** As a staff member, I want to log in with my username and password so that I can access the system according to my role.

**Functional Requirements:**

- **FR-01.1 (Login Fields):** The frontend shall provide labeled username and password input fields and a sign-in action for staff members.
- **FR-01.2 (Successful Authentication):** After receiving a successful authentication response containing a valid session token and supported role, the frontend shall navigate the user to the appropriate role-specific home screen.
- **FR-01.3 (Invalid Credentials):** The frontend shall display an error message when authentication fails and shall not grant access to protected pages.
- **FR-01.4 (Logout):** The frontend shall provide a logout action that clears the active client session and prevents access to protected pages through browser history after logout.
- **FR-01.5 (Login Feedback):** The frontend shall display a loading state during authentication and prevent duplicate login submissions while a request is pending.

**Verification Method:** Test successful and unsuccessful login, session handling, logout, role-based redirection, network errors and protected navigation using synthetic staff accounts.

**API Dependency:** `POST /api/v1/auth/login` and `POST /api/v1/auth/logout`. Real backend authentication and token invalidation remain pending.

---

#### US-02: Role-Based Access Control (`#130` — Frontend `#135`)

**Owner:** `@ardaservetusta-lang`

**User Story:** As an administrator, I want each function to be restricted by role so that users can only perform authorized actions.

**Functional Requirements:**

- **FR-02.1 (Supported Roles):** The frontend shall recognize seven roles: Administrator, Receptionist, Physician, Nurse, Patient Care Assistant, Lab Technician and Cleaning Staff.
- **FR-02.2 (Permission Matrix):** The frontend shall determine access permissions using the Product Owner-approved role-permission matrix. Any unspecified permission shall be denied by default.
- **FR-02.3 (Menu Visibility):** The frontend shall hide navigation items and workflow actions that are not authorized for the signed-in user's role.
- **FR-02.4 (Protected Routes):** The frontend shall prevent unauthorized access through direct URLs, browser history navigation and page refresh.
- **FR-02.5 (Authorization Errors):** The frontend shall handle applicable HTTP 401 responses by invalidating the current client session and HTTP 403 responses by displaying an access-denied message without incorrectly logging the user out.

**Verification Method:** Test all seven roles, menu visibility, direct URL access, refresh, browser navigation, session expiration and HTTP 401/403 responses.

**Pending Dependency:** The complete role-permission matrix requires Product Owner approval. Currently, patient registration, patient search, patient profile editing and the doctor directory are available to Receptionist only. Server-side authorization remains a backend responsibility.

---

#### US-06: Register Patient (`#175` — Frontend `#182`)

**Owner:** `@ardaservetusta-lang`

**User Story:** As a receptionist, I want to register a new patient so that their identity, contact and relative information are stored.

**Functional Requirements:**

- **FR-06.1 (Required Fields):** The frontend shall collect the patient's first name, last name, 11-digit synthetic national ID, date of birth, gender, phone number, address and condition level.
- **FR-06.2 (Condition Levels):** The frontend shall provide three selectable condition levels: Mild, Moderate and Advanced.
- **FR-06.3 (Advanced Condition):** When Advanced is selected, the frontend shall require the relative's full name, relationship degree and phone number before allowing submission.
- **FR-06.4 (Optional Relative Information):** When Mild or Moderate is selected, relative information shall be optional. Any provided relative information shall be validated.
- **FR-06.5 (Field Validation):** The frontend shall reject missing or invalid required values and display field-level validation errors.
- **FR-06.6 (Duplicate National ID):** When the registration service reports an existing national ID, the frontend shall display a duplicate-record error and shall not report successful registration.
- **FR-06.7 (Submission Feedback):** The frontend shall display loading, success and error states and prevent duplicate submissions while a registration request is pending.

**Verification Method:** Test all required fields, invalid data, 11-digit identity validation, condition levels, relative contact requirements, duplicate identity handling and submission feedback.

**API Dependency:** `POST /api/v1/patients`. Real server persistence and national ID uniqueness enforcement remain pending.

---

#### US-07: Search Patient (`#186` — Frontend `#189`)

**Owner:** `@ardaservetusta-lang`

**User Story:** As a receptionist, I want to search patients by national ID or name so that I can quickly find an existing record.

**Functional Requirements:**

- **FR-07.1 (National ID Search):** The frontend shall allow an authorized Receptionist to search for a patient using an exact 11-digit synthetic national ID.
- **FR-07.2 (Partial Name Search):** The frontend shall support partial-name searches and display all matching patient records returned by the search service.
- **FR-07.3 (Empty Results):** When no matching patients are found, the frontend shall display a clear "No patient found" message.
- **FR-07.4 (Search Feedback):** The frontend shall display appropriate loading and error states and distinguish failed requests from successful searches with no results.
- **FR-07.5 (Patient Selection):** The frontend shall allow an authorized Receptionist to select a matching patient and navigate to that patient's profile.

**Verification Method:** Test exact national ID lookup, partial-name queries, multiple results, empty results, network failures and navigation to a selected patient's profile.

**API Dependency:** `GET /api/v1/patients?nationalId={nationalId}` and `GET /api/v1/patients?name={name}`. Real backend search integration remains pending.

---

#### US-08: Update Patient Information (`#193` — Frontend `#200`)

**Owner:** `@ardaservetusta-lang`

**User Story:** As a receptionist, I want to update a patient's information so that their records stay current.

**Functional Requirements:**

- **FR-08.1 (Patient Profile):** The frontend shall allow an authorized Receptionist to select an existing patient from search results and open the corresponding patient profile.
- **FR-08.2 (Immutable National ID):** The frontend shall display the patient's national ID as read-only information and shall not permit modification of that value.
- **FR-08.3 (Editable Information):** The frontend shall allow editing of permitted patient details and apply the same validation rules used by US-06.
- **FR-08.4 (Advanced Condition Validation):** When the patient's condition level is Advanced, including when changed from Mild or Moderate, the frontend shall require valid relative contact information before allowing an update.
- **FR-08.5 (Update Feedback):** The frontend shall display saving, success and error states, prevent duplicate update submissions and warn users before discarding unsaved changes.
- **FR-08.6 (Updated Patient Information):** After a successful update, the frontend shall display the updated patient information in subsequent authorized patient searches and profile views.

**Verification Method:** Test patient selection, read-only national ID, editable fields, shared validation rules, Advanced relative contact requirements, synthetic patient updates, updated search results, unsaved changes warnings and access restrictions.

**API Dependency:** `GET /api/v1/patients/{patientId}` is specified for patient retrieval. A patient update endpoint has not yet been approved in the OpenAPI contract. Real update operations remain pending. Development-only synthetic update testing uses `VITE_PATIENT_PROFILE_MOCK=true`.

**Backend Dependency (US-03 / US-08):** The original US-08 acceptance criteria require every successful patient update to be recorded in a server-side audit log with before-and-after values. This requirement is outside the frontend implementation scope and remains pending backend verification.


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
