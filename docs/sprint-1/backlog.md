
# HMS Community — Sprint 1 Product Backlog

**Version:** V3  
**Project:** HMS Community — Hospital Management System  
**Course:** SENG 321 / BM 321  
**Product Owner:** @huseyin-tutak  
**Reference:** Product Backlog V3 and RACI V5 Final

## Backlog Overview

The backlog is organized into eight epics. Each user story has a priority, story-point estimate, planned iteration, acceptance criteria, and an assigned Product Owner.

The Product Owner is accountable for requirements and acceptance according to RACI V5. Technical implementation is distributed through linked Data, Backend, Frontend, and QA sub-issues.

## Prioritized User Stories

| Story ID | Epic | User Story | Priority | SP | Iteration | Owner | GitHub Issue |
|---|---|---|---|---:|---|---|---|
| US-01 | EP-08 | Staff login | Must | 5 | Sprint 2 | @huseyin-tutak | [#107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107) |
| US-02 | EP-08 | Role-based access control | Must | 8 | Sprint 2 | @huseyin-tutak | [#130](https://github.com/HabibYazar/SoftwareEngineering321/issues/130) |
| US-03 | EP-08 | Logging of all actions | Must | 5 | Sprint 2 | @huseyin-tutak | [#140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140) |
| US-04 | EP-08 | View audit log | Should | 3 | Final | @huseyin-tutak | [#152](https://github.com/HabibYazar/SoftwareEngineering321/issues/152) |
| US-05 | EP-08 | Manage staff accounts and permissions | Should | 8 | Final | @huseyin-tutak | [#163](https://github.com/HabibYazar/SoftwareEngineering321/issues/163) |
| US-06 | EP-01 | Register patient | Must | 8 | Sprint 2 | @huseyin-tutak | [#175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) |
| US-07 | EP-01 | Search patient | Must | 3 | Sprint 2 | @huseyin-tutak | [#186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) |
| US-08 | EP-01 | Update patient information | Should | 3 | Sprint 2 | @huseyin-tutak | [#193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) |
| US-09 | EP-03 | List doctors by department | Must | 3 | Sprint 2 | @huseyin-tutak | [#201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201) |
| US-10 | EP-03 | Define doctor working hours | Could | 5 | Final | @huseyin-tutak | [#192](https://github.com/HabibYazar/SoftwareEngineering321/issues/192) |
| US-11 | EP-02 | Create appointment | Must | 8 | Sprint 3 | @huseyin-tutak | [#181](https://github.com/HabibYazar/SoftwareEngineering321/issues/181) |
| US-12 | EP-02 | Cancel appointment | Must | 2 | Sprint 3 | @huseyin-tutak | [#174](https://github.com/HabibYazar/SoftwareEngineering321/issues/174) |
| US-13 | EP-02 | Reschedule appointment | Should | 3 | Sprint 3 | @huseyin-tutak | [#97](https://github.com/HabibYazar/SoftwareEngineering321/issues/97) |
| US-14 | EP-02 | Physician daily schedule | Must | 3 | Sprint 3 | @huseyin-tutak | [#101](https://github.com/HabibYazar/SoftwareEngineering321/issues/101) |
| US-15 | EP-04 | Start and complete examination | Must | 3 | Sprint 3 | @huseyin-tutak | [#106](https://github.com/HabibYazar/SoftwareEngineering321/issues/106) |
| US-16 | EP-04 | Patient check-in | Could | 2 | Final | @huseyin-tutak | [#111](https://github.com/HabibYazar/SoftwareEngineering321/issues/111) |
| US-17.1 | EP-05 | Examination note and diagnosis | Must | 5 | Sprint 3 | @huseyin-tutak | [#116](https://github.com/HabibYazar/SoftwareEngineering321/issues/116) |
| US-17.2 | EP-05 | Medical history and allergies | Must | 3 | Sprint 3 | @huseyin-tutak | [#123](https://github.com/HabibYazar/SoftwareEngineering321/issues/123) |
| US-17.3 | EP-05 | Medication history and reports | Should | 5 | Sprint 3 | @huseyin-tutak | [#129](https://github.com/HabibYazar/SoftwareEngineering321/issues/129) |
| US-18 | EP-05 | View patient history | Should | 3 | Sprint 3 | @huseyin-tutak | [#138](https://github.com/HabibYazar/SoftwareEngineering321/issues/138) |
| US-19 | EP-06 | Request laboratory tests | Must | 5 | Final | @huseyin-tutak | [#144](https://github.com/HabibYazar/SoftwareEngineering321/issues/144) |
| US-20 | EP-06 | Enter laboratory results | Must | 5 | Final | @huseyin-tutak | [#151](https://github.com/HabibYazar/SoftwareEngineering321/issues/151) |
| US-21 | EP-06 | View laboratory results | Must | 2 | Final | @huseyin-tutak | [#158](https://github.com/HabibYazar/SoftwareEngineering321/issues/158) |
| US-22 | EP-07 | View bed occupancy | Could | 5 | Final | @huseyin-tutak | [#164](https://github.com/HabibYazar/SoftwareEngineering321/issues/164) |
| US-23 | EP-07 | Admit and discharge patient | Won't (this term) | 8 | Out of scope | @huseyin-tutak | [#173](https://github.com/HabibYazar/SoftwareEngineering321/issues/173) |

## Acceptance Criteria and Dependencies

The detailed acceptance criteria and dependencies for each user story are maintained in its linked GitHub parent issue.

Each parent issue should include:
- A user story describing the actor, capability, and intended value.
- Testable acceptance criteria.
- Epic, priority, and planned iteration.
- Story-point estimate.
- Dependencies on other stories, data, or services.
- Links to technical sub-issues.
- Definition of Done.

The parent issues are the acceptance reference. Technical sub-issues organize implementation and testing responsibilities under RACI V5.

## Definition of Ready

A user story is ready when:
- The actor, capability, and business value are clear.
- Acceptance criteria are testable.
- Dependencies and API impact are understood.
- The Product Owner and technical responsibility are identified.
- The work is reasonably sized for its planned iteration.

## Definition of Done

A story is complete when:
- All acceptance criteria pass and are accepted by the Product Owner.
- Relevant code and automated tests are committed.
- A peer-reviewed Pull Request is merged.
- Required CI checks pass.
- OpenAPI and supporting documentation are updated where necessary.
- Synthetic demonstration data is used.
- Linked technical sub-issues are completed.

The project targets at least 50% unit-test coverage by Sprint 3, with integration and end-to-end evidence by Final.

## Scope and Release Planning

**Sprint 2:** Authentication, authorization, audit foundations, patient management, and clinician directory.

**Sprint 3 — Week 8 MVP:** Appointment management, physician schedule, outpatient examination, and core electronic medical records.

**Final — Week 10:** Laboratory request, result entry, and physician result review. Additional Should/Could items depend on available capacity.

**Out of Scope:** US-23 — Admit and discharge patient is explicitly deferred beyond the current term.

## RACI V5 Ownership

- Product Backlog and Requirements — @huseyin-tutak
- Architecture and Backend Accountability — @HabibYazar
- Agile Tracking — @aliardilerkol8-eng
- Quality Assurance — @Ekejje
- DevOps and CI/CD — @Zmly007
- Frontend and Integration — @ardaservetusta-lang

Story Points are counted only on parent user stories, not again on their technical sub-issues.
