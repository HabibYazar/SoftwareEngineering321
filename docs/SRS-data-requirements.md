# SRS input — data requirements for US-01, US-03, US-05, US-06, US-08

Ready to paste into `docs/SRS.md` §3.2 ("Additional User Stories"). Owner of all data sub-issues: `@huseyin-tutak`. Implementation: `backend/src/main/resources/db/migration/V1..V3`, verified by `db-tests/schema_tests.sql` (139 checks).

### 3.2.0 General data rules
* **FR-00.1 (Not provided = NULL):** A value that is not provided shall be stored as `NULL`, never as an empty string or `0`. Required columns shall be `NOT NULL`; a blank value sent for an optional column shall be stored as `NULL`.
* **FR-00.2 (Administrator):** An administrator shall be able to perform every action of the system (every permission, including permissions added later, and changes to fields that are immutable for other roles). Exceptions required by US-03 / US-05: the audit log and the deletion register stay immutable, an administrator cannot deactivate or delete their own account, and the last active administrator cannot be removed.

### 3.2.1 Epic EP-08: Authentication, Authorization & Audit

#### US-01: Staff login (`#107`, data `#114`)
* **FR-01.1 (Role catalogue):** The schema shall store exactly the seven roles of US-02 with codes equal to the OpenAPI `LoginResponse.role` enum.
* **FR-01.2 (Accounts):** Each staff member shall have at most one user account with a unique, case-insensitive username that only an administrator can change.
* **FR-01.3 (Password storage):** Passwords shall be stored only as BCrypt or Argon2 hashes; any other value is rejected (`HV001`).
* **FR-01.4 (Sessions):** Sessions shall store only the SHA-256 of the token; logout shall end the session so the token is rejected immediately (`auth_validate_session` returns no row). An administrator shall be able to end any session (`auth_revoke_session`).
* **FR-01.5 (Inactive accounts):** No session can be created for a deactivated account (`HS003`).

#### US-03: Logging of all actions (`#140`)
* **FR-03.1 (Event content):** Each audit event shall record actor, actor role at that time, action, result, affected record type and id, session and time; updates shall include before/after values; deletes shall include the deletion reason.
* **FR-03.2 (Coverage):** Data changes of patients, relatives, staff, accounts, sessions, departments, doctor assignments and the role–permission matrix, plus login, failed login, logout, session revocation and audit reads, shall be logged automatically by the database; UI interactions (click, navigation, input, submit) shall be loggable through `audit_record` / `audit_record_batch`.
* **FR-03.3 (Attribution):** A business-data change without an acting user shall be rejected (`HA401`).
* **FR-03.4 (Immutability):** Audit events shall not be updatable, deletable, truncatable or droppable by any database role, administrators included (`HL001`); the backend role shall have no direct table privileges on the log; only administrators (`AUDIT_LOG_VIEW`) can read it.
* **FR-03.5 (Privacy):** Passwords, password hashes, tokens and secrets shall never be stored in audit details; record creation shall log field names only; a patient deletion shall not copy the deleted values into the log.
* **FR-03.6 (Packing):** A closed month shall be packed into a single column-wise, xz-compressed archive file (`audit_log_YYYY-MM.zip`) with SHA-256 per block, per content and per file, openable as CSV at any time and restorable into the database on demand.

#### US-05: Manage staff accounts and permissions (`#163`, data `#165`)
* **FR-05.1 (Matrix):** The role–permission matrix shall be stored as editable rows; Administrator shall hold every permission and its permissions cannot be removed (`HS005`).
* **FR-05.2 (Lifecycle):** Accounts and staff shall be deactivated and reactivated by an administrator; an administrator may also delete them permanently with a mandatory reason (`STAFF_DELETE`, FR-05.5). Deactivation, role change and password reset shall end the user's sessions.
* **FR-05.3 (Administrator safety):** An administrator cannot deactivate or delete their own account (`HS002`); the last active administrator cannot be deactivated, deleted or lose the role (`HS001`).
* **FR-05.4 (Departments):** Departments shall have unique names (case-insensitive); only staff with the Physician role can be assigned to one active department (`HS004`).
* **FR-05.5 (Deletion with reason):** Every permanent delete (patient, staff member, account) shall require a free-text reason of 1–1000 characters entered by the administrator (API field `deleteReason`); a missing reason is rejected with `HV001` (`field = deleteReason`). The reason, record type, id, actor and time shall be stored in the immutable `record_deletions` register and in the audit log.

### 3.2.2 Epic EP-01: Patient Management

#### US-06: Register patient (`#175`, data `#177`)
* **FR-06.1 (Required fields):** first name, last name, national ID (11 digits, synthetic, unique), date of birth (1900-01-01 … today), gender, phone, address and condition level (`MILD` | `MODERATE` | `ADVANCED`) shall be mandatory.
* **FR-06.2 (Blank detection):** A blank value (`NULL`, empty or whitespace) shall be rejected before storage with SQLSTATE `HV001`, reporting the failing column and its data type in the standard `COLUMN` / `DATATYPE` error fields and all failing fields (API name, column, data type, error kind, message) as JSON in `DETAIL`.
* **FR-06.3 (Storage):** All patient columns are required and therefore `NOT NULL`; optional relative information shall be a separate 1:1 record (no record = not provided), so no placeholder value is ever stored.
* **FR-06.4 (Relative rule):** For `ADVANCED` patients the relative's full name, relationship and phone shall be required, including when the level is raised later or the relative is removed.
* **FR-06.5 (Complete deletion):** An administrator shall be able to delete a patient record completely (patient and relative information) at any time with a reason (`PATIENT_DELETE`, FR-05.5); other roles are rejected (`HA403`).

#### US-08: Update patient information (`#193`, data `#196`)
* **FR-08.1 (Editable fields):** All patient fields except the national ID shall be updatable by Receptionist and Administrator (`HV001` / `IMMUTABLE` for the national ID); an administrator may correct the national ID.
* **FR-08.2 (Same validation):** FR-06.1–FR-06.4 shall apply to updates.
* **FR-08.3 (Permission):** Administrator and Receptionist shall hold `PATIENT_UPDATE`; other roles are rejected (`HA403`).
* **FR-08.4 (Audit):** Each update shall be logged with before and after values.

### NFR-06: Audit log footprint and integrity (Efficiency, Security)
* **Specification:** A live audit event shall use ≤ 300 bytes including indexes; a packed month shall use ≤ 5 bytes per event; packing a month of 100 000 events shall take ≤ 5 s and opening it as CSV ≤ 1 s; every archive shall pass SHA-256 verification before it can replace the live partition or be restored.
* **Measured** (PostgreSQL 17.12, 100 000 events, 32 cores): 144 B heap + 109 B indexes per event live; 1.8 B per event packed (25 MB live / 21.9 MB CSV → 178 KB). Pack ≈ 1.2 s (before: 34.5 s), verify ≈ 0.4 s, open as CSV ≈ 0.35 s (before: 0.71 s), restore ≈ 1.45 s (before: 3.98 s).
* **Traceability:** US-03 (`#140`, `#145`), US-04 (`#152`).
* **Verification Method:** `scripts/audit_archive.py export` / `verify --check-db` / `open` (each prints its duration); size query on the monthly partition.
