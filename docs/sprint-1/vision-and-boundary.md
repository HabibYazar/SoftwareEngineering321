# Sprint 1 — System Vision & Boundary (draft)

## Vision
For authorized hospital staff who need an auditable patient journey, HMS Community provides a unified intranet-based workflow for registration, appointments, outpatient encounters and synthetic laboratory results. This semester's result is a demonstrable teaching prototype, not a hospital-certified clinical product.

## System responsibilities (first release)

1. Store and retrieve demo patient profiles.
2. Manage clinicians and appointment availability with conflict validation.
3. Track outpatient encounter state, notes and diagnostics orders.
4. Record and display synthetic lab results.
5. Apply role-aware authentication/authorization and audit events to sensitive operations (to be implemented).
6. Expose documented versioned REST contracts and automated tests.

## Beyond first release

Community roadmap: inpatient/bed management, broader clinical workflows. Future commercial/private projects may independently develop advanced integrations and analytics. Other complex clinical workflows require separate regulatory, safety and domain validation.

## External boundaries and assumptions

- Frontend (React) communicates with backend API through internal Nginx.
- Single PostgreSQL instance holds synthetic demo records (schema is design-stage only).
- National systems (MHRS/e-Nabız/MEDULA etc.) are **external providers**, not reimplemented here, and live connections are deferred.
- The system **does not** decide medical treatments, make autonomous diagnoses, operate physical equipment or directly replace a production HBYS during the course.
- Users, roles, data retention and hospital policies require stakeholder validation.

## Context diagram

See [C4 context](../architecture/c4-context.mmd) and [containers](../architecture/c4-container.mmd).
