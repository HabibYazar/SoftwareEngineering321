# HMS Community — Database schema (US-01, US-03, US-05, US-06, US-08)

Data deliverables for the Product Owner data sub-issues
[#114](https://github.com/HabibYazar/SoftwareEngineering321/issues/114) (US-01),
[#140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140) (US-03),
[#165](https://github.com/HabibYazar/SoftwareEngineering321/issues/165) (US-05),
[#177](https://github.com/HabibYazar/SoftwareEngineering321/issues/177) (US-06) and
[#196](https://github.com/HabibYazar/SoftwareEngineering321/issues/196) (US-08).
Target: PostgreSQL 17 (`postgres:17-alpine` in `compose.yaml`), Flyway, Hibernate `ddl-auto=validate` (SRS NFR-03).

| File | Content |
|---|---|
| `backend/src/main/resources/db/migration/V1__security_staff_core.sql` | roles, permissions, role–permission matrix, departments, staff, doctor–department links, user accounts, sessions, deletion register, shared field-validation framework |
| `backend/src/main/resources/db/migration/V2__audit_log.sql` | immutable audit log, login/logout/session functions, administrator-only search, monthly partitions, archive registry, restore |
| `backend/src/main/resources/db/migration/V3__patients.sql` | patients, relative information, validation, permissions for Receptionist, complete patient deletion |
| `scripts/audit_archive.py` | pack / verify / open / drop / restore / unload monthly audit archives (Python standard library only) |
| `db-tests/schema_tests.sql` | 139 acceptance checks, run inside one rolled-back transaction |
| `docs/architecture/database-er.mmd` | ER diagram (Mermaid) |
| `docs/SRS-data-requirements.md` | FR / NFR text for `docs/SRS.md` §3.2 |
| `docs/openapi-additions.yaml` | DELETE endpoints with the deletion reason, field-error schema — to merge into `api/openapi.yaml` |

**No demo data.** The migrations insert no person, patient or department rows — only reference data the stories define as part of the system (the seven US-02 roles, the permission catalogue, the initial matrix, validation and error message catalogues). Synthetic fixtures for demos (#114 "synthetic test accounts", US-09 seeding) belong in separate migrations owned by their issues. The tests create their own rows inside a transaction that is rolled back.

---

## 1. Applying the migrations

The backend does not yet depend on Flyway/JPA. Add to `backend/pom.xml`:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
```

and to `application.yml`:

```yaml
spring:
  datasource:
    url: ${HMS_DB_URL}
    username: ${HMS_DB_USER}
    password: ${HMS_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
```

Copy the three files to `backend/src/main/resources/db/migration/`. If other migrations already exist (for example US-09 seeding), keep the relative order V1 → V2 → V3 and renumber. Each migration must run in a transaction (Flyway's default for PostgreSQL).

Manual application (same result):

```bash
for f in V1__security_staff_core.sql V2__audit_log.sql V3__patients.sql; do
  docker compose exec -T db psql -U hms -d hms_community -v ON_ERROR_STOP=1 --single-transaction -f - \
    < backend/src/main/resources/db/migration/$f
done
```

**First administrator** (deployment step, not a migration because it needs a secret). Generate a BCrypt hash with Spring Security, then run once:

```sql
SELECT security_bootstrap_admin('First', 'Last', 'admin.username', '{bcrypt}$2a$10$...');
```

It works only while no administrator exists (otherwise `HS008`). The account must change its password at first login.

**Database roles.** The migrations create the NOLOGIN role `hms_app` with least privileges (no table access to `audit_log`; deletes are granted but every delete is checked against the acting user's permission by triggers). DevOps should run the backend with a login that belongs to it:

```sql
CREATE ROLE hms_backend LOGIN PASSWORD '<from secret store>' IN ROLE hms_app;
```

The local compose stack still connects as the `hms` superuser; all triggers below protect the data regardless, but only the `hms_app` login also enforces the privilege layer. When the migration user is not a superuser, the event triggers of §8 are skipped with a NOTICE; everything else is created.

## 2. Conventions

* **A value that is not provided is `NULL` — never `''` or `0`.** Required columns are `NOT NULL` and reject blanks with `HV001` (§4). Optional columns are nullable; an empty or whitespace-only value sent for them is stored as `NULL`, so "not provided" has exactly one representation. Numbers keep their real meaning (`failed_login_count = 0` = no failed attempt; `version` is JPA's).

  | Nullable column | `NULL` means |
  |---|---|
  | `staff_members.title`, `.phone`, `.email` | not provided |
  | `auth_sessions.revoked_at`, `.revoke_reason` | session still active |
  | `auth_sessions.client_ip`, `.user_agent` | not sent by the client |
  | `field_rules.max_length`, `.pattern`, `.allowed_values`, `.min_value`, `.max_value`, `.unique_mode` | rule not applied (no limit) |
  | `audit_log.actor_user_id`, `.actor_role_id`, `.session_id` | system action / unknown user (e.g. failed login) |
  | `audit_log.entity_type_id`, `.entity_id`, `.details` | event without a record / without details |
  | `audit_archives.min_id`, `.max_id` | archived month had no events |
  | `record_deletions.deleted_by` | deleted by a trusted system operation |

  Optional relations are their own table (no row = not set, e.g. `patient_relatives`).
* Enumerations are `text` + `CHECK` (maps to `@Enumerated(EnumType.STRING)` and passes `ddl-auto=validate`).
* `snake_case` names, `uuid` primary keys (`gen_random_uuid()`), `timestamptz`, `created_at`/`updated_at` maintained by triggers, `version` for JPA `@Version`.
* `staff_members.display_name` is a generated column (`title first_name last_name`, without a leading space when `title` is `NULL`) → map with `insertable = false, updatable = false`. It equals `Clinician.displayName`.

## 3. Transaction context (mandatory for writes)

Every transaction that changes data must first tell the database who is acting:

```sql
SELECT hms_set_context(:accountId, :sessionId);
```

The value is transaction-local. Without it, writes fail with `HA401`; with an account lacking the permission they fail with `HA403`. This guarantees that every change in the audit log has an actor (US-03) and that permission rules hold even if a controller check is missed (US-02, US-08).

```java
@Component
@RequiredArgsConstructor
public class DbActorContext {
    private final JdbcTemplate jdbc;

    /** Call first inside each @Transactional service method (or from an aspect). */
    public void apply(UUID accountId, UUID sessionId) {
        jdbc.queryForObject("SELECT hms_set_context(?, ?)", Object.class, accountId, sessionId);
    }
}
```

## 4. Error contract (blank and invalid fields)

All business rules raise a fixed SQLSTATE; `hms_error_codes` lists them with HTTP status and messages.

| SQLSTATE | Name | HTTP | When |
|---|---|---|---|
| `HV001` | VALIDATION_FAILED | 400 | blank / invalid / too long / duplicate / immutable field, relative missing for ADVANCED, deletion reason missing |
| `23505` | UNIQUE_VIOLATION | 409 | unique index backstop (`uq_patients_national_id`, `uq_user_accounts_username`, `uq_departments_name`) |
| `23503` | FOREIGN_KEY_VIOLATION | 409 | related record missing / still referenced |
| `HA401` | ACTOR_CONTEXT_MISSING | 401 | `hms_set_context` not called |
| `HA403` | PERMISSION_DENIED | 403 | role lacks the permission (DETAIL = permission code) |
| `HS001` | LAST_ADMINISTRATOR | 409 | last active administrator deactivated, deleted or role changed |
| `HS002` | SELF_DEACTIVATION | 409 | administrator deactivates or deletes own account / staff record |
| `HS003` | ACCOUNT_INACTIVE | 401 | session for a deactivated account |
| `HS004` | DOCTOR_ASSIGNMENT_CONFLICT | 409 | non-physician assigned / inactive department |
| `HS005` | ADMINISTRATOR_PERMISSIONS_LOCKED | 409 | permission removed from Administrator |
| `HS006` | DELETE_NOT_ALLOWED | 409 | catalogue entry for the backend's own pre-check; the database answers an unpermitted delete with `HA403` |
| `HS007` | SESSION_STATE_LOCKED | 409 | ended session changed |
| `HS008` | BOOTSTRAP_NOT_ALLOWED | 409 | administrator already exists |
| `HL001` | AUDIT_IMMUTABLE | 403 | any change / delete / truncate / drop of audit data or of the deletion register |
| `HL002` | AUDIT_UNKNOWN_CODE | 400 | unknown audit action / entity / result |
| `HL003` | AUDIT_DETAILS_TOO_LARGE | 400 | details over 8 KB, batch over 500 |
| `HL004` | AUDIT_ARCHIVE_STATE | 409 | archive step not possible for that month |

**Blank detection.** A blank value is `NULL`, `''` or whitespace only. For a required field it never reaches the table: a `BEFORE` trigger checks the row against `field_rules` and raises `HV001` (for an optional field the blank becomes `NULL`). The error carries everything the backend needs to print a field-level message:

* standard diagnostic fields: `COLUMN` = first failing column, `DATATYPE` = its type, `TABLE`, `SCHEMA`;
* `HINT` = ready Turkish message of the first error;
* `DETAIL` = JSON array of **all** failing fields:

```json
[{"field": "firstName", "column": "first_name", "dataType": "text", "error": "REQUIRED",
  "message": "Ad alanı zorunludur ve boş bırakılamaz.", "messageEn": "First name is required and cannot be blank."},
 {"field": "dateOfBirth", "column": "date_of_birth", "dataType": "date", "error": "REQUIRED",
  "message": "Doğum tarihi alanı zorunludur ve boş bırakılamaz.", "messageEn": "Date of birth is required and cannot be blank."}]
```

Error kinds: `REQUIRED`, `TOO_LONG`, `INVALID_FORMAT`, `NOT_ALLOWED`, `OUT_OF_RANGE`, `DUPLICATE`, `IMMUTABLE`, `RELATIVE_REQUIRED`. `field` is the property name of `api/openapi.yaml`, so the response can point at the form field directly.

```java
} catch (DataAccessException ex) {
    if (ex.getMostSpecificCause() instanceof PSQLException p && p.getServerErrorMessage() != null) {
        ServerErrorMessage m = p.getServerErrorMessage();
        if ("HV001".equals(m.getSQLState())) {
            String column   = m.getColumn();     // e.g. "first_name"
            String dataType = m.getDatatype();   // e.g. "text"
            List<FieldError> errors = objectMapper.readValue(m.getDetail(), new TypeReference<>() {});
            return ResponseEntity.badRequest().body(problem("VALIDATION_FAILED", errors));
        }
    }
    throw ex;
}
```

To validate a payload without writing (e.g. to show all errors before saving), call `SELECT hms_field_errors('patients', :rowAsJson::jsonb)` with column names as keys; it returns the same array (empty = valid).

The "ADVANCED needs a relative" rule is a deferred constraint (patient and relative can be saved in any order in one transaction). Its error appears at commit; call `SET CONSTRAINTS ALL IMMEDIATE` at the end of the service method to receive it there instead.

## 5. Roles and permissions (US-02, US-05, US-08)

Seven roles (`roles.code` = `LoginResponse.role` in OpenAPI).

**The administrator can do everything, everywhere.** `has_permission()` returns true for every permission when the actor is an administrator — also for permissions added by later migrations, without a matrix row. Administrators may change fields that are immutable for every other role (national ID, username, record links), create / edit / deactivate / reactivate / delete staff and accounts, end any session, edit the permission matrix of every other role, manage departments, register / update / **completely delete** patients and read the audit log. Only these rules also bind administrators, because the stories require them:

* the audit log and the deletion register cannot be edited or deleted (US-03);
* an administrator cannot deactivate or delete their own account (`HS002`) — another administrator can;
* the last active administrator cannot be deactivated, deleted or lose the role (`HS001`);
* the Administrator role's own permissions cannot be removed (`HS005`) — it always has all of them.

| Permission | Administrator | Receptionist | Others |
|---|---|---|---|
| `STAFF_ACCOUNT_MANAGE` — create / edit / deactivate staff, change role, reset password, end sessions | ✔ | | |
| `ROLE_PERMISSION_MANAGE` — edit the matrix | ✔ | | |
| `DEPARTMENT_MANAGE` — add departments, assign doctors | ✔ | | |
| `AUDIT_LOG_VIEW` — read and filter the log | ✔ | | |
| `PATIENT_CREATE` — register patients (US-06) | ✔ | ✔ | |
| `PATIENT_VIEW` — view patient information | ✔ | ✔ | |
| `PATIENT_UPDATE` — update patient information (US-08) | ✔ | ✔ | |
| `STAFF_DELETE` — delete staff and accounts permanently, with a reason | ✔ | | |
| `PATIENT_DELETE` — delete a patient completely, with a reason | ✔ | | |

Physician, Nurse, Patient Care Assistant, Lab Technician and Cleaning Staff receive permissions in the migrations of their own stories (appointments, EMR, laboratory …) or by an administrator through `role_permissions` (US-05).

Further US-05 rules enforced in the database: deactivated users cannot sign in and lose their sessions; usernames are unique (case-insensitive); only physicians can be assigned to a department and changing a physician's role removes the assignment; role changes end the user's sessions; an administrator password reset sets `must_change_password` and ends all sessions.

## 6. Deleting records (administrator, with a reason)

An administrator can delete a patient record **completely** at any time (patient + relative information, whatever the condition level), and a staff member or account permanently. A delete without a reason is rejected.

**Where the reason is entered**

| Layer | Place |
|---|---|
| UI | required multi-line text field **"Silme nedeni"** in the delete confirmation dialog (max. 1000 characters) |
| API | request body of `DELETE /patients/{patientId}`, `DELETE /staff/{staffMemberId}`, `DELETE /accounts/{accountId}`: `{"deleteReason": "…"}` (`docs/openapi-additions.yaml`) |
| Backend → DB | second argument of the delete function (below) |
| Database | `record_deletions.reason` (with entity type, id, who, when) and the `details.reason` of the audit event |

```sql
SELECT hms_set_context(:accountId, :sessionId);
SELECT patient_delete(:patientId, :deleteReason);         -- patient + relative information
SELECT staff_delete(:staffMemberId, :deleteReason);       -- staff + account + sessions + department link
SELECT user_account_delete(:accountId, :deleteReason);    -- only the login account + its sessions
```

Each function returns `true` when a row was deleted and `false` when the id does not exist (→ 404). A plain `DELETE` works too after `SELECT hms_set_delete_reason(:deleteReason)` in the same transaction (e.g. from JPA `repository.delete()`).

Reason missing, blank or over 1000 characters → `HV001` with
`{"field": "deleteReason", "column": "delete_reason", "dataType": "text", "error": "REQUIRED", "message": "Silme nedeni alanı zorunludur ve boş bırakılamaz.", …}`.
Without `PATIENT_DELETE` / `STAFF_DELETE` (e.g. a receptionist) → `HA403`.

What is kept: `record_deletions` (immutable, `HL001`) and one audit event with the reason:

* patient → `RECORD_DELETE` `{"reason": …, "fields": [names of the deleted fields]}` — no values, so a deleted patient's data does not live on in the log (the relative row is logged the same way);
* staff member → `RECORD_DELETE` `{"reason", "displayName", "role"}`; account → `ACCOUNT_DELETED` `{"reason", "username", "staffMemberId"}` — enough to tell later who had access.

Audit events written before the delete (create, updates with before/after values) stay unchanged because the log is immutable.

```sql
SELECT entity_type, entity_id, reason, deleted_by, deleted_at FROM record_deletions ORDER BY id DESC;
```

## 7. Login / logout / sessions (US-01, US-05)

1. `SELECT id, password_hash, status FROM user_accounts WHERE lower(username) = lower(?)` and verify the hash with Spring Security (`PasswordEncoder.matches`).
2. Success → `SELECT auth_login_success(:accountId, :sha256OfToken, :expiresAt, :clientIp, :userAgent)` returns the session id, or `NULL` if the account is deactivated (respond 401); `clientIp` / `userAgent` may be `NULL`. Failure → `SELECT auth_login_failure(:username, :clientIp)`. Never pass the attempted password.
3. Every request → `SELECT * FROM auth_validate_session(:sha256OfToken)` (no row = 401). Returns account, staff member, role and `must_change_password`.
4. Logout → `SELECT auth_logout(:sessionId)`; the token is invalid immediately.
5. Own password change → `SELECT auth_change_own_password(:newHash)` (keeps the current session, ends the others).
6. Administrator ends another user's session → `SELECT auth_revoke_session(:sessionId)` (`SESSION_REVOKED` event). Schedule `SELECT auth_expire_sessions();` to close expired sessions.

Only the SHA-256 of the token is stored (`bytea`, 32 bytes).

## 8. Audit log (US-03, US-04)

**Recorded automatically by triggers** (no backend code needed): every insert / update / delete of patients, relatives, staff, accounts, sessions, departments, doctor assignments and the permission matrix — with actor, actor role, time, record and, for updates, before/after values (`{"changes": {"phone": ["old", "new"]}}`); deletes with their reason; account creation, deactivation, reactivation, role change, password reset/change; login success, failure (also for unknown usernames), logout, session revocation; reading the log; every archive step. Password hashes, tokens and secrets never reach the log (keys matching password/token/secret/hash are replaced by `[REDACTED]`); creates store field names only, not values.

**Recorded by the backend** — everything else the user does, down to single clicks:

```sql
SELECT audit_record('UI_CLICK', NULL, NULL, '{"element":"save","route":"/patients/new"}');
SELECT audit_record('RECORD_VIEW', 'PATIENT', :patientId);
SELECT audit_record_batch(:eventsJsonArray);   -- up to 500 UI events forwarded by the frontend
```

Actions: `UI_CLICK`, `UI_NAVIGATION`, `UI_INPUT` (field name only, never the typed value), `UI_FORM_SUBMIT`, `UI_OTHER`, `RECORD_VIEW`, `RECORD_SEARCH`, `API_REQUEST`, `PERMISSION_DENIED`, … (`audit_actions`). A request that fails with 403 rolls its own transaction back, so log `PERMISSION_DENIED` in a separate transaction (e.g. `@Transactional(propagation = REQUIRES_NEW)`).

**Reading (administrators only):**

```sql
SELECT * FROM audit_search(p_actor_user_id => :userId, p_action => 'RECORD_UPDATE',
                           p_from => :from, p_to => :to, p_entity_type => 'PATIENT',
                           p_entity_id => :patientId, p_limit => 100, p_offset => 0);
```

It checks `AUDIT_LOG_VIEW` (otherwise `HA403`), logs the read, resolves usernames/roles/actions and includes restored archive months. Filters left `NULL` are not applied. The backend role has no table privileges on the log.

**Immutability.** `UPDATE`, `DELETE`, `TRUNCATE` and direct `INSERT` on `audit_log`, its partitions, `audit_archives`, `audit_log_restored` and `record_deletions` fail with `HL001` for every database role, including the owner and administrators. When the migrations run as a superuser (the compose default), event triggers additionally block `DROP` (also `DROP … CASCADE`) of these tables and disabling or dropping their triggers. Only a month that has been exported and verified can be removed from the live database (`audit_archive_drop_partition`). A PostgreSQL superuser can always bypass database-level protection; for production run the backend as `hms_app` and keep superuser access with the operators.

**Space.** Codes are `smallint` (action, role, entity type, result), IP and user agent are stored once per session, details are optional `jsonb` limited to 8 KB, monthly partitions keep indexes small and `toast_tuple_target = 128` compresses larger rows in place. Schedule `SELECT audit_maintain_partitions(3);` daily (creates the next months ahead).

Measured on PostgreSQL 17.12 with 100 000 realistic events (clicks, navigation, form input, patient updates with before/after values):

| Where | Size | Per event |
|---|---|---|
| live table (heap) | 14.4 MB | ≈ 144 B |
| live indexes (PK, actor, action, entity, BRIN) | 10.6 MB | ≈ 109 B |
| CSV of the month (readable, with usernames and codes) | 21.9 MB | ≈ 219 B |
| **archive `audit_log_2026-10.zip`** | **178 KB** | **≈ 1.8 B** |

The archive is ≈ 120× smaller than the CSV and ≈ 140× smaller than the live month (heap + indexes).

## 9. Packing the log (archives)

A closed month is packed into **one file** `audit_log_YYYY-MM.zip` plus a `audit_log_YYYY-MM.zip.sha256` sidecar (`sha256sum -c` format):

* rows are read with `COPY … TO STDOUT` from the view `audit_log_export` (usernames, role, action names, UTC time) — the database work takes ≈ 0.4 s;
* they are stored **column by column** in segments of 32 768 rows; each column block is compressed with xz (LZMA2 preset 5, dictionary fitted to the block) — neighbouring values of one column (sequential ids, timestamps, repeated action codes) compress far better than row-wise CSV;
* all blocks are compressed and decompressed **in parallel** (one thread per core);
* `manifest.json` holds rows, id range, columns, SHA-256 of every block and of the whole content; the content hash and the file hash are stored in `audit_archives`.

```bash
python scripts/audit_archive.py list
python scripts/audit_archive.py export  --month 2026-10 --out audit-archives
python scripts/audit_archive.py verify  audit-archives/audit_log_2026-10.zip --check-db
python scripts/audit_archive.py open    audit-archives/audit_log_2026-10.zip      # -> .csv (UTF-8 BOM, opens in Excel)
python scripts/audit_archive.py drop    --month 2026-10 --archive-dir audit-archives --yes
python scripts/audit_archive.py restore audit-archives/audit_log_2026-10.zip      # searchable again via audit_search
python scripts/audit_archive.py unload  --month 2026-10
```

Only months that are over (`audit_assert_closed_period`) can be exported; a second export of the same month, a restore while the partition is still live and a repeated restore are refused (`HL004`). `verify` detects a single changed byte (block checksum and SHA-256) and truncated files. `restore` checks the file hash against `audit_archives`, loads the rows in one transaction (`audit_restore_begin` → `COPY audit_log_restored` → `audit_restore_finish`, which compares count and id range with the registry) into an `UNLOGGED` table — the archive file stays the durable copy. Options: `--psql "<command>"` or `HMS_PSQL` for another connection (default `docker compose exec -T db psql -U hms -d hms_community`), `--workers N` for the thread count. Every command prints its duration.

**Measured** (100 000 events, PostgreSQL 17.12, 32 cores, best of several runs; previous version = row CSV in one `xz -9e` stream):

| Step | Previous | Now | Faster |
|---|---|---|---|
| pack (`export`) | 34.5 s | ≈ 1.1–1.4 s | ≈ 28× |
| archive size | 373 KB | 178 KB | 2.1× smaller |
| check (`verify`) | 0.73 s | ≈ 0.35–0.45 s | ≈ 1.8× |
| open and read (`open` → CSV) | 0.71 s | ≈ 0.33–0.40 s | ≈ 2× |
| remove from live DB (`drop`) | 0.81 s | ≈ 0.53 s | 1.5× |
| load back for search (`restore`) | 3.98 s | ≈ 1.43–1.49 s | ≈ 2.7× |
| remove restored copy (`unload`) | — | 0.26 s | |

The CSV written by `open` is identical to the CSV of the previous version.

## 10. Patients (US-06, US-08)

| Field (API) | Column | Type | Rule |
|---|---|---|---|
| `nationalId` | `national_id` | text | required, 11 digits, unique, immutable (administrators may correct it) |
| `firstName` / `lastName` | `first_name` / `last_name` | text | required, ≤ 100 |
| `dateOfBirth` | `date_of_birth` | date | required, 1900-01-01 … today |
| `gender` | `gender` | text | required, ≤ 40 |
| `phone` | `phone` | text | required, ≤ 32, digits / spaces / `+()-` |
| `address` | `address` | text | required, ≤ 500 |
| `conditionLevel` | `condition_level` | text | required, `MILD` \| `MODERATE` \| `ADVANCED` |
| `relativeInformation.fullName` / `.relationship` / `.phone` | `patient_relatives.*` | text | required inside the object, ≤ 150 / ≤ 80 / ≤ 32; row required when ADVANCED, otherwise no row |

Every field except `nationalId` can be updated by Administrator and Receptionist; the same validation applies; each update is logged with before/after values. Administrators can delete a patient completely with a reason (§6); receptionists cannot delete.

## 11. Running the tests

```bash
docker compose exec -T db psql -U hms -d hms_community -X -v ON_ERROR_STOP=1 -f - < db-tests/schema_tests.sql
```

Prints one PASS/FAIL line per acceptance check (139 checks: blanks and data types, `NULL` conventions, permissions, administrator powers and their limits, deletes with reason, immutability incl. `DROP … CASCADE`, sessions, audit search and archive states) and leaves the database unchanged (single transaction, rolled back). Exit code ≠ 0 when a check fails.

## 12. Follow-ups for other owners (ADR-0004: contract and code change together)

* **OpenAPI:** merge `docs/openapi-additions.yaml` (DELETE endpoints with `deleteReason`, `FieldError` / `ValidationProblem`); add `pattern` for `phone` / `relativeInformation.phone`; add endpoints for UI audit events (`POST /audit/events` → `audit_record_batch`), audit search (US-04), staff / role-permission / department management (US-05) and departments (#204).
* **#179:** decide which roles may set `conditionLevel`; today `PATIENT_CREATE` / `PATIENT_UPDATE` cover it.
* **#114:** synthetic staff accounts for demos are intentionally not part of these migrations.
