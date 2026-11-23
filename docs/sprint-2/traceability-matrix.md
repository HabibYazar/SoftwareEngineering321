# Sprint 2 — Requirements Traceability Matrix

**Project:** HMS Community — Team 15
**Baseline:** 23 November 2026

This matrix connects Sprint 2 user stories to implementation code, automated test locations and GitHub evidence. The existence of tests and merged code does not automatically establish full Product Owner acceptance.

## Functional requirement traceability

| User Story | Parent Issue | Implementation | Test evidence |
|---|---|---|---|
| US-01 Staff Login | [#107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107) | [AuthenticationService](../../backend/src/main/java/org/hmscommunity/security/authentication/AuthenticationService.java) | [Authentication API integration tests](../../backend/src/test/java/org/hmscommunity/security/authentication/AuthenticationApiIntegrationTest.java) |
| US-02 Role-Based Access | [#130](https://github.com/HabibYazar/SoftwareEngineering321/issues/130) | [HttpSecurityConfiguration](../../backend/src/main/java/org/hmscommunity/security/HttpSecurityConfiguration.java) | [Role-based access integration tests](../../backend/src/test/java/org/hmscommunity/security/authorization/RoleBasedAccessIntegrationTest.java) |
| US-03 Audit Logging | [#140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140) | [AuditService](../../backend/src/main/java/org/hmscommunity/security/audit/AuditService.java), [Flyway V2](../../backend/src/main/resources/db/migration/V2__audit_log.sql) | [PostgreSQL audit tests](../../backend/src/test/java/org/hmscommunity/security/audit/PostgresAuditTest.java) |
| US-06 Patient Registration | [#175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) | [PatientService](../../backend/src/main/java/org/hmscommunity/patients/application/PatientService.java) | [Registration integration tests](../../backend/src/test/java/org/hmscommunity/patients/api/PatientRegistrationIntegrationTest.java) |
| US-07 Patient Search | [#186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) | [PatientController](../../backend/src/main/java/org/hmscommunity/patients/api/PatientController.java) | [Search integration tests](../../backend/src/test/java/org/hmscommunity/patients/api/PatientSearchIntegrationTest.java) |
| US-08 Patient Update | [#193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) | [PatientService](../../backend/src/main/java/org/hmscommunity/patients/application/PatientService.java) | [Update integration tests](../../backend/src/test/java/org/hmscommunity/patients/api/PatientUpdateIntegrationTest.java) |
| US-09 Doctor Directory | [#201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) | [StaffRepository](../../backend/src/main/java/org/hmscommunity/staff/StaffRepository.java) | [StaffControllerTest](../../backend/src/test/java/org/hmscommunity/staff/StaffControllerTest.java) |

## Integration and architectural evidence

- [Sprint 2 integrated code PR #257](https://github.com/HabibYazar/SoftwareEngineering321/pull/257)
- [Doctor data PR #258](https://github.com/HabibYazar/SoftwareEngineering321/pull/258)
- [Gender validation and demo accounts PR #259](https://github.com/HabibYazar/SoftwareEngineering321/pull/259)
- [Final requirements PR #264](https://github.com/HabibYazar/SoftwareEngineering321/pull/264)
- [Architecture PR #265](https://github.com/HabibYazar/SoftwareEngineering321/pull/265)
- [Main integration PR #267](https://github.com/HabibYazar/SoftwareEngineering321/pull/267)
- [Verified Docker smoke-test results](README.md)
- [Refined backlog with acceptance criteria](refined-backlog.md)
- [NFR and quality-measure specifications](quality-measures.md)

## Acceptance and QA interpretation

As of the requirements baseline, US-03 was closed and the other six parent issues remained open. Their final acceptance status must be verified in GitHub by the Product Owner before those stories are reported as accepted.

PostgreSQL-tagged integration test classes exist, but the local smoke test and default CI do not establish that the complete dedicated PostgreSQL test suite was executed. Record a separate run when completing full QA verification.

## Requirement quality controls

- Each story has a named actor, business value, priority, estimate and testable acceptance criteria.
- Every requirement connects to at least one implementation component or test.
- All changes remain attributable through Git commit and PR history.
- Unverified quality targets and formal acceptance are reported separately.
