# HMS Community backend

Spring Boot 3.5 / Java 21 modular monolith. The schema is owned by the Data workstream
(`src/main/resources/db/migration`, see `docs/database/README.md`); the backend only applies it with Flyway.

## Database configuration

| Variable | Purpose |
|---|---|
| `HMS_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://db:5432/hms_community`. When unset, the backend starts without a database and every login/patient operation answers `503 INTEGRATION_PENDING` (fail-closed). |
| `HMS_DB_USER` / `HMS_DB_PASSWORD` | Runtime login. In production this should be a member of the least-privilege role `hms_app`. |
| `HMS_DB_MIGRATION_USER` / `HMS_DB_MIGRATION_PASSWORD` | Optional separate owner for Flyway migrations (defaults to the runtime login, as in `compose.yaml`). |

Flyway runs on startup. A database whose schema was applied manually with `psql` has no
`flyway_schema_history` table and must be baselined (or recreated, for the local demo) before the backend can start.

No credentials are created by the migrations; the first administrator is created with
`security_bootstrap_admin(...)` as described in `docs/database/README.md` §1.

## Tests

```bash
mvn -B clean verify
```

Without further configuration the PostgreSQL integration tests (`Postgres*Test`, tag `postgres`) are **skipped**
and only the database-less suite runs. To run them, point these variables at a **disposable** PostgreSQL 17 server
with ICU support (never a shared or production server); each run creates a new database `hms_it_<timestamp>_<random>`,
migrates it, connects the application through a separate `hms_app` login and seeds synthetic accounts:

```bash
export HMS_TEST_DB_URL=jdbc:postgresql://localhost:55432/postgres   # server URL, no query parameters
export HMS_TEST_DB_USER=<superuser of the disposable server>
export HMS_TEST_DB_PASSWORD=<its password>
mvn -B clean verify
```

The Data workstream's SQL acceptance tests run separately against a freshly migrated database:
`psql -X -v ON_ERROR_STOP=1 -f db-tests/schema_tests.sql`.
