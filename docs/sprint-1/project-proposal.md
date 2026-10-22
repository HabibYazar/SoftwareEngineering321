# Sprint 1 — Project Proposal (draft)

> Academic template for review with real course stakeholders. No claims of measured hospital performance or validated requirements.

## 1. Problem definition and evidence

Hospitals coordinate patient registration, scheduling, outpatient encounters and lab results across different workflows. Fragmented or inconsistent record handoffs **may** cause duplication, delays or missing context. This is a **hypothesis**, not a statement about a specific hospital. Evidence is to be gathered from public workflow descriptions, synthetic walkthroughs and ethically approved stakeholder interviews; no real patient records will be collected.

## 2. Stakeholders

- **Reception staff:** register patients, book appointments.
- **Physicians:** conduct encounters, order/review tests, write notes.
- **Lab staff:** track orders and produce/review results.
- **Administrators:** manage users, personnel and operating policies.
- **Patients:** indirectly affected by continuity and privacy.
- **Hospital IT / compliance:** security, backups, integration, operational review.
- **Course instructor / academic jury:** evaluate verifiable implementation and teamwork.

## 3. Proposed outcome / success hypotheses

Build a connected **synthetic-data demo** of register → appointment → encounter → lab request → lab result. Proposed acceptance measure: the full happy path executes without manual DB changes, while invalid inputs, unauthenticated operations and double-bookings are demonstrably rejected. Define numeric response-time targets only after baseline and test environment are agreed.

## 4. Feasibility and constraints

Six-person team; ten-week sprint schedule. Stack: Java 21 Spring Boot modular monolith; React TypeScript; PostgreSQL; Docker Compose. No clinical deployment, production data, government integration credentials, medical devices or external provider availability are assumed. Real-life operational and legal compliance work exceeds academic release scope.

## 5. First release scope

**In:** patient registration; clinician directory; appointments (conflict detection); basic outpatient encounter; electronic note; laboratory order and synthetic result; role-controlled operations and audit trail foundations; API-first contract, tests, Docker demo.

**Deferred:** inpatient & bed workflow (Community roadmap), emergency, radiology/PACS, pharmacy, OR, ICU/nursing, billing/insurance, inventory/procurement, advanced analytics, regulated interfaces, public-facing portal, offline continuity.

## 6. Risks and validation

| Item | Type | Response |
|---|---|---|
| No access to real hospital stakeholders | Assumption | Validate with instructor / mock persona review; state limitations |
| Medical-data exposure | Risk | Synthetic records only; never commit real PHI |
| Booking race conditions | Risk | Transaction-safe conflict model; concurrency tests in Sprint 3 |
| Scope overload | Risk | Prioritize one vertical workflow; defer inpatient and enterprise |
| Role/ownership ambiguity | Risk | RACI with named owners; review contributor and school policies |
| External interoperability | Risk | Mock adapters only, document assumptions |

## 7. License decision

**Proposed:** Apache-2.0 for Community code. Permissive reuse and explicit patent terms support Open-Core separation. The Enterprise extension is a separate private project, not a course deliverable. Final publication requires checking university policy, contributors' rights, notices and dependency licenses. See [ADR-0003](../adr/0003-open-core-boundary.md).
