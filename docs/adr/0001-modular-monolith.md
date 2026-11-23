# ADR-0001 — Modular monolith

**Status:** Accepted and implemented as the Sprint 2 architecture baseline (23 November 2026).

## Context
Six students, a ten-week academic schedule, stringent test/review evidence and a long-term multi-module vision. Premature microservices make deployment, cross-module consistency, CI and monitoring harder.

## Decision
One Java Spring Boot deployable containing bounded domain packages (`patients`, `appointments`, `staff`, `outpatient`, `records`, `laboratory`, `inpatient`, `security`), one REST API and one PostgreSQL instance. Integrations via abstract adapters. Internal boundaries enforced by design, reviews and future architecture tests.

## Consequences
+ One-command deployment and debugging, straightforward transactions and CI.
- Requires discipline to prevent cross-module shortcuts.
- Future modules can be split into independent services only when actual scaling/team needs justify it.

## Alternatives and trade-offs

**Microservices were considered but deferred.** For a six-person academic team, separate deployables would introduce additional service communication, distributed transactions, version coordination and operational complexity.

**Modular monolith benefits:** simpler local deployment, one database transaction boundary, integrated security and audit controls, and fewer moving parts for CI.

**Trade-off:** modules share a deployment and database. Code reviews, package boundaries and tests are necessary to reduce coupling.

**Future evolution:** individual modules can be separated only when scaling, operational requirements or team ownership justify the additional complexity.

## Implementation evidence

- [Sprint 2 C4 and UML architecture](../sprint-2/architecture.md)
- [Docker deployment architecture](../architecture/containers-architecture.md)
- [Sprint 2 integration verification](../sprint-2/README.md)
