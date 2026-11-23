# Containerization and Runtime Deployment Architecture

## 1. Architecture decision

The HMS Community educational prototype uses three Docker Compose services behind an isolated internal network:

- `frontend`: Nginx 1.29 serving a compiled React/TypeScript application and proxying `/api/*`.
- `backend`: Java 21 / Spring Boot modular monolith, built with Maven.
- `db`: PostgreSQL 17 with a persistent named Docker volume.

The backend runs as non-root user `appuser` (UID 10001). The frontend currently uses the upstream Nginx runtime image without an explicit non-root user; this is a pending hardening item, not a verified compliance claim.

## 2. Network boundaries

The frontend publishes the configurable host port `HMS_HTTP_PORT` (default 80) on `127.0.0.1`. The backend publishes `HMS_BACKEND_PORT` (default 8080), also on `127.0.0.1`.

PostgreSQL exposes port 5432 only within the Docker internal bridge network. It does not publish a host port.

This is a development/demo layout, not a clinical production deployment. Real deployment requires transport encryption, identity/governance review, operational controls and formal security assessment.

## 3. Database installation and seeds

The backend applies versioned Flyway migrations V1–V5 on startup. The local Compose environment enables `classpath:db/migration,classpath:db/seed`, adding repeatable synthetic demo migrations.

The schema is not created by `db/init` bind mounts. No `docker-entrypoint-initdb.d` initialization scripts or seed CSVs are mounted by the current `compose.yaml`.

## 4. Runtime sequence

1. `docker compose up --build -d` builds the backend and frontend images and starts PostgreSQL.
2. PostgreSQL becomes healthy using `pg_isready`.
3. Backend connects via JDBC, applies migrations and starts its REST API.
4. Backend health check waits for `GET /api/v1/health`.
5. Frontend starts after the backend is healthy and serves the compiled application.

`docker compose down` preserves the `hms_postgres` volume. Do not use `down -v` unless intentional data deletion is required.

## 5. Verified evidence

The local isolated Docker smoke test verified healthy PostgreSQL/backend services, frontend HTTP 200, Flyway V1–V5, six role-based demo accounts, administrator login, 10 departments and 40 doctors.

It did not independently verify restart persistence, non-root Nginx execution or the NFR-02 pre-cached 60-second startup target.

See [Sprint 2 verification](../sprint-2/README.md) and [quality measures](../sprint-2/quality-measures.md).
