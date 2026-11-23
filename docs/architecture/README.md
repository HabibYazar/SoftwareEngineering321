# HMS Community — Software Architecture Documentation

**Baseline:** Sprint 2, 23 November 2026
**Owner:** Habib Yazar — Lead Software Architect and Technical Lead
**Architectural style:** Modular monolith

## Sprint 2 submission

[View the rendered C4, UML and user-flow diagrams](../sprint-2/architecture.md).

### C4 models

- [C4 Level 1 — System Context](c4-context.mmd)
- [C4 Level 2 — Container Diagram](c4-container.mmd)
- [C4 Level 3 — Implemented Backend Components](c4-components.mmd)

### UML diagrams

- [Implementation Class Diagram](uml-implementation-class.mmd)
- [Authentication Sequence Diagram](uml-auth-sequence.mmd)
- [Patient Management Sequence Diagram](uml-patient-sequence.mmd)
- [Doctor Directory Sequence Diagram](uml-directory-sequence.mmd)
- [Sprint 2 User Flows](sprint2-user-flows.mmd)

### Data models and supporting documents

- [Physical PostgreSQL ER Diagram](database-er.mmd)
- [Conceptual Future Domain Model](domain-model.mmd)
- [Sprint 3 Planned MVP Sequence](mvp-sequence.mmd)
- [Docker Deployment Architecture](containers-architecture.md)
- [US-09 Backend Architecture](US-09-backend-architecture.md)

## Architecture rationale

The backend is a single Java 21 / Spring Boot deployment with internal functional modules, using Spring Security, JdbcClient and Flyway-managed PostgreSQL. The frontend is React/TypeScript delivered through Nginx. Modules share one deployment and database but keep service/repository responsibilities separated.

A modular monolith limits distributed-system complexity for the course timeline and supports rapid integration. Microservices, message brokers and external clinical adapters are deferred.

See [ADR-001](../adr/0001-modular-monolith.md), [ADR-002](../adr/0002-on-prem-network.md) and [ADR-004](../adr/0004-api-first.md).

## Current implementation boundary

Sprint 2 implements authentication, role-based authorization, audit infrastructure, patient registration/search/update and doctor/department lookup.

Appointments, encounters, clinical records, laboratory functions and inpatient workflows are planned or deferred. Their conceptual domain relationships must not be confused with already deployed backend functionality.

The UI/UX user-flow diagram here is supplementary engineering documentation; the separately required **Figma prototype and its shareable link** must still be provided for Teams submission.
