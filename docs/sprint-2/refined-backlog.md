# Sprint 2 — Refined Product Backlog

**Project:** HMS Community — Team 15
**Baseline:** 23 November 2026
**Requirements owner:** Hüseyin Tutak (`@huseyin-tutak`)
**Iteration:** Sprint 2
**Reference:** [Software Requirements Specification](../SRS.md)

## 1. Refinement summary

Sprint 2 covers seven parent user stories, estimated at **35 Story Points**. Must-priority stories account for 32 points; US-08 is a 3-point Should-priority story. Technical implementation sub-issues are not counted again.

The backlog retains the original priorities and Story Point estimates from [Sprint 1 Product Backlog V3](../sprint-1/backlog.md). This refinement adds implementation dependencies, testable acceptance checkpoints and evidence links.

**Important:** GitHub Issue closure, code integration, QA verification and Product Owner acceptance are distinct states. The states below reflect GitHub Issue records checked on 23 November 2026.

## 2. Refined Sprint 2 stories

| Order | Story / Epic | Priority | SP | Dependencies | Issue state | Implementation evidence |
|---|---|---|---:|---|---|---|
| 1 | [US-01 Staff Login — EP-08](https://github.com/HabibYazar/SoftwareEngineering321/issues/107) | Must | 5 | User/role data, demo accounts | Open | AuthController; backend tests; Docker login smoke test |
| 2 | [US-02 Role-Based Access — EP-08](https://github.com/HabibYazar/SoftwareEngineering321/issues/130) | Must | 8 | US-01, permission matrix | Open | HttpSecurityConfiguration; role-based frontend routing |
| 3 | [US-03 Audit Logging — EP-08](https://github.com/HabibYazar/SoftwareEngineering321/issues/140) | Must | 5 | Identity/session data, database triggers | Closed | Flyway V2; audit-related tests |
| 4 | [US-06 Patient Registration — EP-01](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) | Must | 8 | US-02, US-03, patient schema | Open | PatientController; registration UI; validation smoke test |
| 5 | [US-07 Patient Search — EP-01](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) | Must | 3 | US-06 | Open | GET patients API; frontend search tests |
| 6 | [US-09 Doctor Directory — EP-03](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) | Must | 3 | Departments, doctors, permission rules | Open | Directory API; 10 departments and 40 doctors in Docker |
| 7 | [US-08 Patient Update — EP-01](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) | Should | 3 | US-06, US-07, US-03 | Open | PUT patient API; profile UI; update/audit implementation |

## 3. Testable acceptance checkpoints

### US-01 — Authentication

- Valid credentials return an access token and the correct staff role.
- Invalid credentials issue no token.
- Logout revokes the active session, preventing its reuse.
- Role-specific navigation and error feedback work in the frontend.

### US-02 — Authorization

- All seven approved staff roles are supported.
- The approved role–permission matrix defines access.
- Unauthorized protected API actions return HTTP 403 for authenticated users.
- Unauthorized actions and pages are hidden or blocked in the frontend.

### US-03 — Audit logging

- Audit events contain actor, action, result, time and affected record.
- Required login, logout and data modification operations are logged.
- Updates include permitted before/after information.
- Unauthorized modification of audit entries is rejected.
- Credential and token values are not exposed in audit details.

### US-06 — Patient registration

- All required patient fields are validated.
- National ID is an 11-digit synthetic identifier and must be unique.
- MILD, MODERATE and ADVANCED are supported condition levels.
- ADVANCED requires a complete relative contact.
- Invalid input yields field-level errors; duplicate ID is rejected.

### US-07 — Patient search

- Exact national ID search returns the matching patient.
- Partial name searches return matching patient records.
- No match returns an empty result with a clear frontend message.
- Errors are distinguishable from successful empty results.

### US-08 — Patient update

- A selected patient can be opened and editable fields updated.
- National ID remains immutable.
- Registration validation also applies to updates.
- ADVANCED-relative requirements remain enforced.
- Successful changes generate attributable before/after audit information.

### US-09 — Clinician directory

- Active departments can be listed independently of doctors.
- Filtering by department returns only associated doctors.
- Empty filters produce HTTP 200 with an empty list.
- Receptionist/Administrator permissions and relevant UI states are enforced.
- Only synthetic staff/department demonstration records are used.

## 4. Traceability and responsibilities

| Workstream | Responsible member | Evidence |
|---|---|---|
| Requirements and backlog | Hüseyin Tutak — Product Owner | Parent Issues, [SRS](../SRS.md) |
| Architecture and backend integration | Habib Yazar — Technical Lead | [PR #257](https://github.com/HabibYazar/SoftwareEngineering321/pull/257), [PR #261](https://github.com/HabibYazar/SoftwareEngineering321/pull/261) |
| QA and acceptance verification | Yusuf Arda Şanlı | [QA issue #125](https://github.com/HabibYazar/SoftwareEngineering321/issues/125), [#137](https://github.com/HabibYazar/SoftwareEngineering321/issues/137), [#206](https://github.com/HabibYazar/SoftwareEngineering321/issues/206) |
| CI/CD and infrastructure | Yılmaz Şanlı | [PR #258](https://github.com/HabibYazar/SoftwareEngineering321/pull/258), CI workflow |
| UI implementation/integration | Arda Servet Usta | [PR #253](https://github.com/HabibYazar/SoftwareEngineering321/pull/253), frontend documentation |
| Sprint coordination | Ali Ardıl Erkol | Sprint tracking, reviews and release coordination |

Workstream responsibility is based on the team's [RACI V5](../sprint-1/raci.md). A role assignment alone is not proof of a completed individual contribution.

## 5. Definition of Ready

A story is ready when its actor and value are clear, its acceptance criteria are testable, its dependencies and API implications are understood, its priority and estimate are recorded, and the Product Owner and technical owners are identified.

## 6. Definition of Done

- All parent acceptance criteria are verified and accepted by the Product Owner.
- Appropriate unit, API, integration and UI tests pass.
- Relevant OpenAPI and documentation changes are recorded.
- Changes are submitted through reviewed Pull Requests.
- Required GitHub Actions CI checks pass.
- Only synthetic demonstration data is used.
- Any incomplete QA verification is documented rather than treated as passed.

## 7. Sprint boundary and remaining work

**Sprint 3:** Appointment creation, doctor scheduling, outpatient examination and core medical records.

**Final:** Laboratory ordering, results and other approved extensions.

**Out of scope:** Full inpatient admission/discharge (US-23).

**Remaining Sprint 2 acceptance:** Parent Issue review, complete PostgreSQL integration suite, browser-level workflow checks, and documented Product Owner approval.

## 8. Evidence

- [Sprint 2 SRS](../SRS.md)
- [Sprint 2 quality specifications](quality-measures.md)
- [Local verification report](README.md)
- [Integrated main PR #263](https://github.com/HabibYazar/SoftwareEngineering321/pull/263)
- [Successful main CI run](https://github.com/HabibYazar/SoftwareEngineering321/actions/runs/45165435064)