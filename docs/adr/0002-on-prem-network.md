# ADR-0002 — On-premises intranet hosting

**Status:** Proposed

Single hospital, staff browser app reachable through an internal reverse proxy. Database and API have no direct public ingress. External national-health integrations are excluded until legal, operational and technical approvals are verified. Separate public patient-facing edge (if ever needed) must be designed and security-reviewed independently.

**Important:** Network isolation is not a replacement for TLS, RBAC, audited access, backup and recovery, segmentation, and incident response. Compose demo currently binds to localhost only.
