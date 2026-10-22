# Architecture index

## Architecture decision

**Style:** modular monolith. One Java deployment, module boundaries within one codebase, one PostgreSQL instance (data isolated by schema conventions to be designed). Microservices and message broker are intentionally deferred; they add operational cost without helping the first sprint.

## C4 and domain diagrams (Mermaid)

- [C4 L1 context](c4-context.mmd)
- [C4 L2 containers](c4-container.mmd)
- [C4 L3 module responsibilities](c4-components.mmd)
- [Domain relationships / ER](domain-model.mmd)
- [MVP sequence](mvp-sequence.mmd)

Mermaid diagrams can be previewed in GitHub Markdown code fences or in Mermaid Live Editor. Treat all relationships as design hypotheses until requirements are validated.

## Module boundary rules

- No feature module owns security for other modules; `security` exposes role/permission capabilities and audit facade.
- Own entities and repositories inside the owning domain module. Cross-module reads through application services and stable interfaces, not another module's tables or repository types.
- Every mutating operation must have authorized actor, transaction boundaries, validation, audit intent, failure semantics, and tests.
- Use API contracts and ID references rather than exposing ORM entities across module boundaries.
- Healthcare-specific integrations (MEDULA, e-Nabız, MHRS, e-prescription, LIS devices, PACS) are **not implemented or authorized** in this scaffold. Adapter interfaces may be designed after confirming actual provider requirements.

## Deferred production requirements

Risk analysis, operational redundancy and offline workflows, encrypted internal transport, IAM integration, immutable audit event storage, backup and disaster recovery, KVKK and clinical compliance, detailed device/medication safety checks, real hospital stakeholder validation, and load/performance testing. LAN hosting alone does **not** make an application secure.
