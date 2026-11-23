# Software Requirements Specification (SRS) & Product Backlog

> **Status: Sprint 2 requirements baseline (v1.0, 23 November 2026).** Implementation evidence and Product Owner acceptance are reported separately.

> Any changes to this document must be traceable. Entries must have a clear reason for existing / traceable link to the required functionality.

---

## 1. Document Control & Team Ownership

| Field | Value |
|---|---|
| Project | HMS Community — Hospital Management System |
| Course | BM321 / SENG321, Team 15 |
| Baseline | Sprint 2, version 1.0 |
| Baseline date | 23 November 2026 |
| Requirements owner | Hüseyin Tutak — Product Owner |
| Architecture owner | Habib Yazar — Technical Lead |
| QA owner | Yusuf Arda Şanlı |
| Repository | https://github.com/HabibYazar/SoftwareEngineering321 |
| API contract | [OpenAPI 3.1](../api/openapi.yaml) |
| Refined backlog | [Sprint 2 Refined Backlog](sprint-2/refined-backlog.md) |
| Quality specifications | [NFR and Quality Measures](sprint-2/quality-measures.md) |
| Verification | [Sprint 2 Integration Report](sprint-2/README.md) |

### 1.1 Purpose and scope

This SRS establishes the functional and quality requirements for the educational HMS Community prototype. Sprint 2 focuses on staff authentication, authorization, audit infrastructure, patient registration, search and update, and the clinician directory. Appointment scheduling, clinical examinations and laboratory workflows belong to later iterations.

All demonstration data must be synthetic. The application is not approved for clinical deployment.

### 1.2 Status interpretation

- **Implemented:** corresponding code exists in the integrated repository.
- **Verified:** a specified automated or local test has produced evidence.
- **Accepted:** the Product Owner has formally accepted the complete story.
- A merged PR or a passing build does not automatically mean that every acceptance criterion is verified or that the parent Issue should be closed.

The GitHub Issue states in this baseline were checked on 23 November 2026. Issue status and implementation evidence are deliberately tracked separately.

---

### 1.3 Stakeholders and operating context

- **Receptionists:** register, search and update patients; browse doctors.
- **Administrators:** access permitted administration functions.
- **Physicians and other staff:** authenticate and use functions allowed by the role-permission matrix.
- **Product Owner:** validates requirements and acceptance criteria.
- **QA team:** verifies requirements through reproducible tests.

### 1.4 External interfaces

- **Web interface:** React and TypeScript, served by Nginx.
- **REST API:** Spring Boot, JSON, `/api/v1` endpoints.
- **Authentication:** username/password login and Bearer session tokens.
- **Database:** PostgreSQL 17 with JDBC and Flyway migrations.
- **Future external systems:** laboratory devices and national healthcare integrations are outside the Sprint 2 implementation.

### 1.5 Assumptions and constraints

- Only synthetic demonstration records may be used.
- The prototype is not approved for clinical deployment.
- Protected operations require appropriate staff permissions.
- National ID uniqueness is enforced in the database.
- Architecture is a modular monolith with one backend deployment.
- Sprint 2 does not implement appointment booking, examinations or laboratory workflows.
- Quality targets must not be reported as achieved without measurement evidence.

### 1.6 Requirement traceability

Every Sprint 2 parent story is associated with its GitHub Issue, implementation files, verification tests and relevant integration evidence in the [requirements traceability matrix](sprint-2/traceability-matrix.md).

## 2. Refined Product Backlog (Sprint 2)

The Sprint 2 baseline contains seven parent user stories totaling **35 Story Points**: 32 Must-priority points and 3 Should-priority points. Technical sub-issues do not add separate Story Points.

| Story | Parent Issue | Epic | Priority | SP | Issue status | Implementation evidence |
|---|---|---|---|---:|---|---|
| US-01 Staff Login | [#107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107) | EP-08 | Must | 5 | Open | Login API and Docker authentication smoke test |
| US-02 Role-Based Access | [#130](https://github.com/HabibYazar/SoftwareEngineering321/issues/130) | EP-08 | Must | 8 | Open | Security filters and automated tests |
| US-03 Audit Logging | [#140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140) | EP-08 | Must | 5 | Closed | Database audit schema and functions |
| US-06 Register Patient | [#175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) | EP-01 | Must | 8 | Open | Patient API, frontend and validation tests |
| US-07 Search Patient | [#186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) | EP-01 | Must | 3 | Open | Search API and frontend tests |
| US-08 Update Patient | [#193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) | EP-01 | Should | 3 | Open | Update API, frontend and audit-related code |
| US-09 Doctor Directory | [#201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) | EP-03 | Must | 3 | Open | Directory API and Docker smoke test |

**Acceptance status:** An open parent Issue must not be represented as formally accepted solely because its implementation was merged. Full acceptance remains subject to the Product Owner and QA review.

Detailed prioritization, dependencies, acceptance checkpoints and traceability are recorded in [refined-backlog.md](sprint-2/refined-backlog.md).

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
* **FR-09.2 (List and Filter Clinicians Endpoint):** The backend REST API shall expose a `GET /api/v1/staff` endpoint supporting an optional `department` query parameter (`GET /api/v1/staff?department={name}`) that queries the database and returns a JSON array of matching clinician records with HTTP `200 OK`.
* **FR-09.3 (Empty Filter Result Handling):** When filtering by a department that has no assigned clinicians or does not exist, the backend shall return HTTP `200 OK` with an empty JSON array (`[]`) rather than failing or throwing an unhandled exception.

##### Frontend Requirements (`#205` — Owner: `@ardaservetusta-lang`)

- **FR-09.7 (Department Selection):** The frontend shall provide a keyboard-accessible department selector that presents available departments to an authorized Receptionist.
- **FR-09.8 (Doctor Filtering):** When a department is selected, the frontend shall display only clinicians associated with that department, including their display names and departments.
- **FR-09.9 (Results and Feedback):** The frontend shall display an appropriate loading indicator, a clear empty-results message when no clinicians match the selected department, and an error message when a request fails.
- **FR-09.10 (Access Control):** The frontend shall restrict the doctor directory to roles explicitly permitted by the approved role-permission matrix. Until additional permissions are approved, the verified Receptionist workflow shall be enabled and other roles denied.

**Verification Method:** Test department selection, filtered clinician results, empty responses, request failures, keyboard interaction and unauthorized navigation using synthetic demonstration data.

**Integration status:** The merged API returns departments from PostgreSQL independently of doctor records. The local Docker smoke test returned 10 departments and 7 cardiology doctors. Additional browser accessibility acceptance remains a separate QA activity.
---

### 3.2 Authentication, Audit and Patient Management

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

**Integration status:** `POST /api/v1/auth/login` and `POST /api/v1/auth/logout` are implemented. Seven role-based login cases (six demo accounts and one administrator) passed the local Docker smoke test. Logout revocation requires its own acceptance evidence.

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

**Integration status:** The backend enforces database-backed permissions using Spring Security. Role-aware frontend routing is implemented. Full seven-role negative authorization testing and Product Owner acceptance remain separate checkpoints.

---

#### US-03: Logging of All Actions ([#140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140))

**Owner:** `@huseyin-tutak` (data requirements); QA: `@Ekejje`.

**User Story:** As a hospital administrator, I want system activity to be logged so that authorized personnel can investigate changes and maintain accountability.

**Functional Requirements:**

- **FR-03.1 Event Content:** An audit event shall record its actor, actor role, action, outcome, affected record type and identifier, session and timestamp. Updates shall record relevant before/after information; deletions shall record the reason.
- **FR-03.2 Coverage:** Patient, staff, account, department, permission and session changes shall generate audit records. Login, failed login, logout and session revocation shall also be auditable.
- **FR-03.3 Attribution:** Business-data changes requiring an acting user shall reject missing actor context.
- **FR-03.4 Immutability:** Ordinary application users shall not modify or delete audit events. Audit-read access shall be restricted to the approved permission.
- **FR-03.5 Privacy:** Passwords, password hashes, access tokens and secrets shall not appear in audit details.
- **FR-03.6 Archive Integrity:** Closed audit periods shall support verifiable archival and recovery with cryptographic integrity checks.

**Acceptance checkpoints:**

1. Required audit attributes are persisted.
2. Security events and supported data mutations are traceable.
3. Unauthorized audit modification is rejected.
4. Sensitive credentials are excluded from audit payloads.
5. Archive integrity can be verified.

**Verification:** Database migration V2, audit-related database tests and [data requirements](SRS-data-requirements.md). The complete acceptance checklist still requires QA/PO confirmation; closure of the technical issue alone is not evidence of end-to-end coverage.

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

**Integration status:** `POST /api/v1/patients` is implemented with PostgreSQL-backed persistence and uniqueness constraints. The local Docker smoke test confirmed rejection of an invalid gender with HTTP 400; the complete valid-registration acceptance matrix requires separate verification.

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

**Integration status:** Both `GET /api/v1/patients?nationalId=...` and `GET /api/v1/patients?name=...` are implemented. Automated tests exist; the local Docker smoke test did not independently exercise all search cases.

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

**Integration status:** `GET /api/v1/patients/{patientId}` and `PUT /api/v1/patients/{patientId}` are implemented. The frontend also supports opt-in development mock mode. Real API integration and complete update acceptance require browser/database verification.

**Audit dependency:** US-08 requires before/after audit records for successful updates. Audit infrastructure is present in PostgreSQL, but the complete acceptance criterion must be confirmed with a targeted update-and-audit integration test.


## 4. Non-Functional Requirements (NFRs) & Quality Measures

The authoritative Sprint 2 quality specification is maintained in [quality-measures.md](sprint-2/quality-measures.md). Each requirement identifies a measurable target, test environment, verification method, evidence and current verification status.

| ID | Quality attribute | Measurable objective | Current verification |
|---|---|---|---|
| NFR-01 | Performance efficiency | Department/doctor reads: p95 <= 200 ms, p99 <= 500 ms under specified load | Load test pending |
| NFR-02 | Operability | Pre-cached local Docker stack operational within 60 seconds | Controlled timing test pending |
| NFR-03 | Reliability and integrity | Flyway-controlled schema, no unintended data loss on restart | Migrations verified; persistence restart test pending |
| NFR-04 | Security and privacy | Isolated database, non-root runtime, synthetic data only | Partial; frontend runtime privilege check required |
| NFR-05 | Maintainability | Required CI gates pass; target >= 50% unit line/branch coverage | CI passed; coverage threshold not enforced |
| NFR-06 | Audit integrity and efficiency | Immutable, verifiable, compact audit storage | Data workstream measurements documented; independent verification pending |

**Evidence distinction:** Targets are specifications, not automatically achieved results. The Docker smoke test verifies start-up, schema, authentication, clinician directory and a negative gender-validation case. It is not a replacement for load tests, accessibility tests, volume-restart tests or the complete PostgreSQL integration suite.

See [Sprint 2 verification report](sprint-2/README.md).
