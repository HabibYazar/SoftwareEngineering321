# Sprint 1 — Initial prioritized product backlog

Proposed estimates (Fibonacci story points), **not a committed capacity plan**. Assign names/GitHub Issues before submission.

| Priority | Epic | Story ID | User story | Acceptance evidence | Points | Owner | Dependency |
|---:|---|---|---|---|---:|---|---|
| 1 | Identity / Security | US-01 | As staff, I can sign in to the demo with a role. | valid, invalid, forbidden actions | 5 | TBD | Roles definition |
| 2 | Patients | US-02 | As reception, I can register a synthetic patient. | required fields; invalid and duplicate cases | 5 | TBD | US-01 |
| 3 | Staff | US-03 | As reception, I can list available clinicians. | filter by department; empty result | 3 | TBD | Seed fixtures |
| 4 | Appointments | US-04 | As reception, I can book a time slot. | created 201; collision 409; concurrent bookings | 8 | TBD | US-02, US-03 |
| 5 | Outpatient | US-05 | As a physician, I can open a scheduled encounter. | authorized transition and wrong-state rejection | 5 | TBD | US-04 |
| 6 | Records | US-06 | As a physician, I can record a note for an encounter. | saved/retrieved; unauthorized denied; audit | 5 | TBD | US-05 |
| 7 | Laboratory | US-07 | As a physician, I can create a lab order. | created order; invalid test code | 5 | TBD | US-05 |
| 8 | Laboratory | US-08 | As lab staff, I can submit a synthetic lab result. | result appears on encounter; authorization; audit | 5 | TBD | US-07 |
| 9 | Audit | US-09 | As an auditor, I can review demo change events. | actor and time; no secrets in log | 5 | TBD | US-01, US-02 |
| 10 | Inpatient | US-10 | As staff, I can view draft bed occupancy. | planned after first MVP, not Sprint 3 critical path | 8 | TBD | Schema decisions |

**Definition of Ready:** user/value clear, acceptance criteria testable, API impact known, dependencies known, fits one sprint, owner assigned.

**Definition of Done:** code + tests + reviewed PR + API/docs updated + CI green + synthetic-data demo + issue traceability. Coverage >=50% by Sprint 3, integration/E2E evidence by Final.
