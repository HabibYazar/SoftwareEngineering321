# Sprint 1 — System Vision & Boundary (draft)

## Vision
For authorized hospital staff who need an auditable patient journey, HMS Community provides a unified intranet-based workflow for registration, appointments and outpatient examinations. The **Week 8 MVP** ends with examination documentation (notes, diagnosis and medical history); the **Week 10 final** extends it with synthetic laboratory requests and results. This semester's result is a demonstrable teaching prototype, not a hospital-certified clinical product.

## System responsibilities by release

### Week 8 MVP — Sprint 3

1. Authenticate staff and enforce role-based access; log essential security and data actions.
2. Register and search synthetic patient profiles; list doctors by department.
3. Manage appointments and reject overlapping slots; show the physician daily schedule.
4. Start and complete outpatient examinations; record examination notes, diagnosis, medical history and allergies.
5. Expose versioned REST contracts and automated tests for the main workflow.

### Week 10 final

6. Add synthetic laboratory test requests, result entry and physician result review.
7. Include optional backlog features only when capacity allows (e.g., an audit-log viewer or bed occupancy display).

## Outside the current term / deferred

The Community roadmap still includes broader inpatient and bed management, but full patient admission/discharge is **not planned this term**. Advanced clinical workflows, regulated external integrations and future separately developed Enterprise functionality remain outside this academic release and require their own safety, domain and legal validation.

## External boundaries and assumptions

- Frontend (React) communicates with backend API through internal Nginx.
- Single PostgreSQL instance holds synthetic demo records (schema is design-stage only).
- National systems (MHRS/e-Nabız/MEDULA etc.) are **external providers**, not reimplemented here, and live connections are deferred.
- The system **does not** decide medical treatments, make autonomous diagnoses, operate physical equipment or directly replace a production HBYS during the course.
- Users, roles, data retention and hospital policies require stakeholder validation.

## Context diagram

See [C4 context](../architecture/c4-context.mmd) and [containers](../architecture/c4-container.mmd).
