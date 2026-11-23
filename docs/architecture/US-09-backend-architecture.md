# US-09 — Doctor and Department Directory Architecture

**Parent Story:** [US-09 #201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201)
**Backend Issue:** [#204](https://github.com/HabibYazar/SoftwareEngineering321/issues/204)
**Status:** Implemented on the integrated main branch; full QA/PO acceptance tracked separately.

## 1. Purpose

Provide authorized staff with the list of active hospital departments and the clinicians assigned to a selected department, using only synthetic data for local demonstrations.

## 2. Interfaces

| Endpoint | Success | Response schema |
|---|---|---|
| `GET /api/v1/departments` | HTTP 200 | `Department[]`, fields `id`, `name` |
| `GET /api/v1/staff` | HTTP 200 | `Clinician[]`, fields `id`, `displayName`, `department` |
| `GET /api/v1/staff?department=Kardiyoloji` | HTTP 200 | Filtered `Clinician[]` |
| Unknown or empty-result filter | HTTP 200 | Empty JSON list `[]` |

Both endpoints require `DOCTOR_DIRECTORY_VIEW`. Anonymous callers receive 401 and authenticated callers without permission receive 403.

## 3. Java implementation

### Departments

`DepartmentsController` calls `DepartmentsService.getDepartments()`, which calls `DepartmentsRepository.findDepartments()`.

The repository queries the `departments` table with `WHERE is_active` and returns the OpenAPI `Department(UUID id, String name)` record.

### Clinicians

`StaffController.listClinicians(String department)` calls `StaffService.getClinicians(...)`, which validates/normalizes the optional filter and delegates to `StaffRepository.findClinicians(...)`.

The repository queries the PostgreSQL `doctors` view, not a table named `clinicians`. This view is defined in Flyway V1 and connects `staff_members`, `doctor_departments`, `departments` and role/account conditions. Matching clinicians are returned as `Clinician(UUID id, String displayName, String department)`.

The department parameter is passed as a JDBC parameter to avoid SQL injection. A filter exceeding 100 characters is rejected with a field-level validation error. No matching doctors produces HTTP 200 and an empty list.

## 4. Database relationships

- `departments.id` is the department primary key.
- `staff_members.id` identifies staff members; `role_id` references a role.
- `doctor_departments.staff_member_id` references a physician and is its primary key.
- `doctor_departments.department_id` references `departments.id`.
- `doctors` is a read-only logical view for active directory entries.

See the [physical database ER diagram](database-er.mmd).

## 5. UML diagrams

- [Implementation class diagram](uml-implementation-class.mmd)
- [Doctor directory sequence](uml-directory-sequence.mmd)
- [Rendered architecture document](../sprint-2/architecture.md)

## 6. Local verification

The isolated Docker smoke test on the integrated Sprint 2 code observed:

- 10 active departments.
- 40 physician-directory entries.
- 10 department results from `GET /api/v1/departments`.
- 7 cardiology results from `GET /api/v1/staff?department=Kardiyoloji`.

See the [Sprint 2 verification report](../sprint-2/README.md). Authorization boundary tests, accessibility review and formal Product Owner acceptance are independent checkpoints.
