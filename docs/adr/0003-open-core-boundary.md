# ADR-0003 — Open-Core repository separation

**Status:** Proposed; rights review required before publication

- `hms-community` contains only Community features: patient, appointments, staff, outpatient, records, laboratory, inpatient, security/audit.
- Community intended license: Apache-2.0 (subject to contributor and university rights checks).
- Enterprise may be developed in a separately owned, private repository with separately developed features, assets and integration code. **None belongs here.**
- Apache-2.0 permits reuse, commercial use and redistribution of Community code; open-source grants cannot later be revoked for already distributed versions.
- Team member contributions are not automatically exclusively owned by the lead. Written contributor arrangements, rights to commercial exploitation, and university rules need appropriate review before any publication or proprietary reuse.
- Base security, safe access control, auditability and medical safety must never depend on purchase of an Enterprise license.
