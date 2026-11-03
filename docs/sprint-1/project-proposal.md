
# Sprint 1 — Project Proposal

**Project:** HMS Community — Hospital Management System  
**Team:** 15  
**Course:** SENG 321 / BM 321  
**Version:** Sprint 1 Final Draft

## 1. Problem Definition and Supporting Evidence

Hospitals coordinate multiple related processes, including patient registration, physician scheduling, appointments, outpatient examinations, electronic medical records, and laboratory results.

When information is distributed across disconnected workflows, staff may face duplicated data entry, inconsistent records, delayed information exchange, and difficulty maintaining a complete view of patient care.

These are potential problems motivating the project, not verified findings from a specific hospital.

The Agency for Healthcare Research and Quality (AHRQ) identifies the organized sharing of patient information among healthcare participants as an important component of safe and effective care coordination.

The World Health Organization (WHO) emphasizes interoperable, secure, and people-centered digital health systems in its Global Strategy on Digital Health.

These sources support the relevance of integrated health information management. They do not establish that HMS Community has already improved hospital performance.

### Proposed Solution

HMS Community is a browser-based, on-premises hospital management prototype designed for a single hospital.

The system will provide a modular-monolith architecture and integrate selected hospital workflows through a common backend, database, role-based authorization, and documented REST API.

The academic prototype will use synthetic patient data exclusively.

### Supporting Sources

1. World Health Organization. *Global Strategy on Digital Health 2020–2027*.  
   https://www.who.int/publications/i/item/9789240116870

2. Agency for Healthcare Research and Quality. *Care Coordination*.  
   https://www.ahrq.gov/ncepcr/care/coordination.html

## 2. Stakeholders and Their Needs

| Stakeholder | Main Needs | Project Relevance |
|---|---|---|
| Reception Staff | Register and search patients, create and manage appointments | Core workflow user |
| Physicians | View schedules, conduct examinations, record diagnoses, access patient history | Core clinical workflow user |
| Laboratory Staff | Receive laboratory requests and enter synthetic results | Final extension user |
| Hospital Administrators | Manage staff access and operational visibility | Administrative stakeholder |
| Patients | Continuity, privacy, and accuracy of their information | Indirect beneficiary |
| Hospital IT / Compliance | Access control, auditability, maintainability, and deployment security | Technical and governance stakeholder |
| Course Instructor / Academic Jury | Verifiable deliverables, traceable teamwork, working demonstrations | Academic evaluator |

### Stakeholder Influence and Validation

Reception staff and physicians are primary users for the Sprint 3 MVP. Their workflows determine essential functional requirements.

Administrators and hospital IT influence authorization, security, and operational constraints.

Laboratory staff become primary users during the Week 10 laboratory extension.

The instructor and academic jury validate whether the academic deliverables satisfy course requirements, but do not substitute for clinical approval.

No actual hospital interviews or clinical observations are claimed. Stakeholder needs will be assessed using representative personas, synthetic demonstrations, publicly available guidance, and instructor feedback.

## 3. Project Objectives and Success Criteria

The primary objective is to demonstrate a consistent, role-aware workflow connecting patient registration, appointments, outpatient examinations, and medical records.

### Week 8 MVP — Sprint 3

Staff login → patient registration → appointment creation → physician daily schedule → examination → examination note and diagnosis → medical history review.

### Week 10 Final

Extend the MVP with laboratory test requests, synthetic result entry, and physician review of laboratory results.

### Acceptance Measures

The prototype should demonstrate:

- Successful completion of planned workflows using synthetic data.
- Rejection of invalid input.
- Rejection of unauthorized operations.
- Prevention of conflicting appointment bookings.
- Correct access to patient and examination records according to roles.
- Traceable automated builds, tests, and API validation.
- No manual database modification required to complete the demonstrated workflow.

Numeric performance targets will be defined only after the test environment and measurement baseline are established.

These are planned acceptance measures, not claims of completed implementation.

## 4. Technical Feasibility and Constraints

**Team:** Six students, Team 15.  
**Schedule:** Ten-week academic development period.

**Technology stack:**

- Backend: Java 21, Spring Boot
- Frontend: React, TypeScript
- Database: PostgreSQL
- Deployment: Docker Compose
- API: OpenAPI 3.1
- Architecture: Modular monolith
- Version control and CI: GitHub, GitHub Actions

The modular-monolith approach is selected to limit infrastructure complexity while preserving clear module boundaries.

### Constraints

- Academic prototype only.
- Synthetic test data only.
- No clinical deployment.
- No production hospital integration.
- No real patient records.
- No assumption of access to external government or medical services.
- Compliance certification and operational readiness are outside the course scope.

## 5. Project Scope

### Sprint 3 MVP — Week 8

The planned Must-have functionality includes:

- Staff authentication and role-based access control.
- Essential audit logging.
- Patient registration and search.
- Physician directory.
- Appointment creation and cancellation.
- Appointment conflict prevention.
- Physician daily schedule.
- Outpatient examination workflow.
- Examination notes and diagnosis.
- Medical history and allergies.

### Final Extension — Week 10

- Laboratory test requests.
- Synthetic laboratory result entry.
- Physician review of laboratory results.

Other Should/Could items may be implemented depending on available capacity.

### Out of Scope

- Full inpatient admission and discharge workflow (US-23).
- Emergency department management.
- Radiology/PACS.
- Pharmacy management.
- Operating room and ICU management.
- Billing and insurance.
- Advanced analytics.
- Public-facing patient portal.
- Regulated external integrations.

## 6. Risks and Mitigation Strategies

| Risk | Impact | Mitigation |
|---|---|---|
| Limited access to real hospital stakeholders | Requirements may not represent actual clinical practice | Use synthetic scenarios, representative personas, public guidance, and instructor review |
| Medical data privacy | Potential exposure of sensitive information | Use synthetic data only and prohibit real patient records |
| Appointment booking conflicts | Inconsistent scheduling | Validate availability and test concurrent requests |
| Scope overload | MVP may not be completed on time | Prioritize Must-have stories and defer laboratory functionality to Final |
| Integration complexity | Delays across frontend/backend modules | Define interfaces through OpenAPI and maintain modular boundaries |
| Unclear team responsibilities | Delays and duplicated work | Apply RACI V5 and issue-based ownership |
| Deployment and security limitations | Prototype unsuitable for hospital use | Restrict deployment to academic environments and document limitations |

## 7. Licensing and Ownership

**Proposed Community license:** Apache License 2.0.

The Community edition is intended as an open-source academic project, subject to verification of university policies and contributor rights.

Any separately developed Enterprise modules are outside the course deliverables and must remain separate from the Community repository.

Final public distribution requires checking:

- University intellectual-property policies.
- Contributor permissions.
- Third-party dependency licenses.
- Required notices and attribution.

See [ADR-0003 — Open-Core Boundary](../adr/0003-open-core-boundary.md).

## 8. Sprint 1 Deliverables and Traceability

- [System Vision and Boundary](vision-and-boundary.md)
- [OpenAPI 3.1 Contract](../../api/openapi.yaml)
- [RACI V5](raci.md)
- [Git-Flow and Pull Request Workflow](git-workflow.md)
- [Product Backlog V3](backlog.md)
- [Sprint 1 Submission Checklist](submission-checklist.md)

All implementation remains subject to the academic project plan, requirements validation, and future sprint acceptance.
