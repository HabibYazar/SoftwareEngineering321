# ADR-0001 — Modular monolith

**Status:** Proposed for Sprint 1

## Context
Six students, a ten-week academic schedule, stringent test/review evidence and a long-term multi-module vision. Premature microservices make deployment, cross-module consistency, CI and monitoring harder.

## Decision
One Java Spring Boot deployable containing bounded domain packages (`patients`, `appointments`, `staff`, `outpatient`, `records`, `laboratory`, `inpatient`, `security`), one REST API and one PostgreSQL instance. Integrations via abstract adapters. Internal boundaries enforced by design, reviews and future architecture tests.

## Consequences
+ One-command deployment and debugging, straightforward transactions and CI.
- Requires discipline to prevent cross-module shortcuts.
- Future modules can be split into independent services only when actual scaling/team needs justify it.
