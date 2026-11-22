# HMS Community backend

Spring Boot 3.5 / Java 21 modular monolith. The schema is owned by the Data workstream
(`src/main/resources/db/migration`, see `docs/database/README.md`); the backend only applies it with Flyway.

## Database configuration

| Variable | Purpose |
|---|---|
| `HMS_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://db:5432/hms_community`. When unset, the backend starts without a database and every login/patient operation answers `503 INTEGRATION_PENDING` (fail-closed). |
| `HMS_DB_USER` / `HMS_DB_PASSWORD` | Runtime login. In production this should be a member of the least-privilege role `hms_app`. |
| `HMS_DB_MIGRATION_USER` / `HMS_DB_MIGRATION_PASSWORD` | Optional separate owner for Flyway migrations (defaults to the runtime login, as in `compose.yaml`). |
| `HMS_FLYWAY_LOCATIONS` | Defaults to `classpath:db/migration` (schema only). `compose.yaml` adds `classpath:db/seed`, an idempotent repeatable migration with synthetic US-09 departments and doctors for the local demo. |
| `HMS_BOOTSTRAP_ADMIN_USERNAME` / `HMS_BOOTSTRAP_ADMIN_PASSWORD` | Optional. When both are set and no administrator exists, the first administrator is created on startup through `security_bootstrap_admin(...)`; otherwise nothing changes. The password (12 characters to 72 bytes) is stored only as a BCrypt hash and never logged. |

Flyway runs on startup. A database whose schema was applied manually with `psql` has no
`flyway_schema_history` table and must be baselined (or recreated, for the local demo) before the backend can start.

No credentials are created by the migrations. The first administrator is created either by the bootstrap
variables above or manually with `security_bootstrap_admin(...)` as described in `docs/database/README.md` §1
(the function is revoked from `PUBLIC`, so a least-privilege `hms_app` runtime login must use the migration user).

## Local Docker stack

```bash
cp .env.example .env    # set HMS_BOOTSTRAP_ADMIN_USERNAME / HMS_BOOTSTRAP_ADMIN_PASSWORD for the first start
docker compose up --build -d
```

The UI is served on `http://localhost:${HMS_HTTP_PORT:-80}`; the backend is published only on
`127.0.0.1:${HMS_BACKEND_PORT:-8080}` (for the Vite dev proxy) and the database is not published. Nginx resolves the
backend at request time and the frontend starts only after the backend health check passes. Data stays in the
`hms_postgres` volume; `docker compose down` keeps it (never add `-v` unless you intend to delete the local demo data).
If a port is busy (for example an older stack still publishing 8080), set `HMS_HTTP_PORT` / `HMS_BACKEND_PORT` in `.env`.

## Tests

```bash
mvn -B clean verify
```

Without further configuration the PostgreSQL integration tests (`Postgres*Test`, tag `postgres`) are **skipped**
and only the database-less suite runs. To run them, point these variables at a **disposable** PostgreSQL 17 server
with ICU support (never a shared or production server); each run creates a new database `hms_it_<timestamp>_<random>`,
migrates it (schema and the synthetic `db/seed` directory), connects the application through a separate `hms_app`
login and seeds synthetic accounts:

```bash
export HMS_TEST_DB_URL=jdbc:postgresql://localhost:55432/postgres   # server URL, no query parameters
export HMS_TEST_DB_USER=<superuser of the disposable server>
export HMS_TEST_DB_PASSWORD=<its password>
mvn -B clean verify
```

The Data workstream's SQL acceptance tests run separately against a freshly migrated database:
`psql -X -v ON_ERROR_STOP=1 -f db-tests/schema_tests.sql`.
