# Sprint 2 — Microsoft Teams Submission

**Course:** BM321 / SENG321 Software Engineering
**Team:** 15
**Project:** HMS Community — Hospital Management System
**Deadline:** 24 November 2026, 23:59 (Türkiye)

## Repository
https://github.com/HabibYazar/SoftwareEngineering321

## Required deliverables

| Deliverable | Link |
|---|---|
| Detailed SRS | [Software Requirements Specification](../SRS.md) |
| Refined Product Backlog | [Sprint 2 Backlog](refined-backlog.md) |
| NFR & Quality Measures | [Quality Specifications](quality-measures.md) |
| Figma UI/UX prototypes | [Figma Design](https://www.figma.com/design/zgMOSpFkfdUensVpfB7jgR) |
| User-flow diagrams | [Figma User Flows](https://www.figma.com/design/zgMOSpFkfdUensVpfB7jgR), [Mermaid Flow](../architecture/sprint2-user-flows.mmd) |
| C4 models | [C4 Architecture](architecture.md) |
| UML diagrams | [Architecture Diagrams](../architecture/README.md) |
| Verification | [Sprint 2 Integration Report](README.md) |

## Team Members and Responsibilities

| Member | GitHub | Role | Responsibility |
|---|---|---|---|
| Habib Yazar | @HabibYazar | Technical Lead | Architecture, backend, integration |
| Hüseyin Tutak | @huseyin-tutak | Product Owner | SRS, backlog, acceptance criteria |
| Ali Ardıl Erkol | @aliardilerkol8-eng | Scrum Master | Sprint coordination and issue tracking |
| Yusuf Arda Şanlı | @Ekejje | QA Engineer | Testing and quality verification |
| Yılmaz Şanlı | @Zmly007 | DevOps Engineer | CI/CD, Docker, infrastructure |
| Arda Servet Usta | @ardaservetusta-lang | Implementation Engineer | Frontend and UI workflows |

Detailed responsibilities: [RACI V5](../sprint-1/raci.md).

## Issues and Pull Requests

**User Stories:** [US-01 #107](https://github.com/HabibYazar/SoftwareEngineering321/issues/107), [US-02 #130](https://github.com/HabibYazar/SoftwareEngineering321/issues/130), [US-03 #140](https://github.com/HabibYazar/SoftwareEngineering321/issues/140), [US-06 #175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175), [US-07 #186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186), [US-08 #193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193), [US-09 #201](https://github.com/HabibYazar/SoftwareEngineering321/issues/201).

**Relevant PRs:**

- [#257 — Sprint 2 integration](https://github.com/HabibYazar/SoftwareEngineering321/pull/257)
- [#258 — Database and doctor seed data](https://github.com/HabibYazar/SoftwareEngineering321/pull/258)
- [#259 — Demo accounts and validation](https://github.com/HabibYazar/SoftwareEngineering321/pull/259)
- [#261 — Integration conflict resolution](https://github.com/HabibYazar/SoftwareEngineering321/pull/261)
- [#262 — Verification report](https://github.com/HabibYazar/SoftwareEngineering321/pull/262)
- [#264 — SRS, Backlog and NFR](https://github.com/HabibYazar/SoftwareEngineering321/pull/264)
- [#265 — C4 and UML architecture](https://github.com/HabibYazar/SoftwareEngineering321/pull/265)

## Release

Target tag: `sprint-2`.

The release tag must be created only after all approved Sprint 2 documentation has been merged into `main`.

## Verification and limitations

Automated backend, frontend and OpenAPI checks passed on the previously integrated main commit. Local Docker smoke tests passed for database migrations, staff authentication, clinician directory and negative patient validation.

Full end-to-end QA coverage, performance measurements and formal Product Owner acceptance are tracked separately.

Only synthetic demonstration data is used. This is an educational prototype, not a production hospital system.
