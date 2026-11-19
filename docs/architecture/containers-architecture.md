## Containerization & Runtime Deployment Architecture
### Windows Setup & Usage
Project members need to download and install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) to run the full application stack with a single command. Using the WSL 2 backend is strongly recommended for faster file I/O and native Linux container compatibility—see the [Microsoft WSL Installation Guide](https://learn.microsoft.com/en-us/windows/wsl/install) to set it up before launching Docker Desktop.

After installing, the project can be run with a single command by launching cmd, cd into the project directory, and running `docker compose up --build`
frontend will bind to port 80 (browsers can connect to http://localhost:80) and backend will bind to port 8080 (ensure localhost:8080/api/v1/health returns success). db will not bind to any port, it will seed data from db/init/ . 


### Architectural Rationale
environment parity across all developer workstations and elimination of host-level dependency conflicts (`Java`, `Maven`, `Node.js`, `PostgreSQL`).
All decisions align with industry standard practices.
The system is decomposed into three isolated containers orchestrated to run with a single command via Docker Compose (`compose.yaml`):

1. **Process Isolation & Lifecycle Decoupling:** Separating the presentation tier (`frontend`), application tier (`backend`), and persistence tier (`db`) allows independent rebuilds and restarts without data loss.
2. **Multi-Stage Builds:** Both `backend` and `frontend` compile source code inside heavy SDK builder images (`maven:3.9-eclipse-temurin-21` and `node:22-alpine`) and copy only compiled artifacts (`.jar` and static `/dist` assets) into minimal runtime images (`eclipse-temurin:21-jre` and `nginx:1.29-alpine`).
3. **Least-Privilege Execution:** The Spring Boot container runs under an unprivileged system user (`appuser`, UID `10001`) rather than `root`.
4. **Network Segmentation:** PostgreSQL is isolated on the private `internal` Docker bridge network with no host port exposure; only `frontend` (`:80`) and `backend` (`:8080`) bind to host ports.

### Container & Deployment Diagram
```mermaid
flowchart TB
    %% C4 Styling
    classDef person fill:#08427B,stroke:#052E56,color:#FFFFFF,rx:12,ry:12
    classDef container fill:#438DD5,stroke:#2E6295,color:#FFFFFF,rx:6,ry:6
    classDef storage fill:#999999,stroke:#6B6B6B,color:#FFFFFF,rx:6,ry:6

    user(["<b>End User</b><br/><i>[Person]</i><br/>Clinician, staff, or developer"]):::person

    subgraph host ["Docker Host Machine — Bridge Network (internal)"]
        direction TB

        frontend["<b>Frontend Web Container</b><br/><i>[Container: Nginx 1.29 / Node 22]</i><br/>Serves compiled SPA static assets (:80)<br/>and reverse-proxies /api/* requests"]:::container

        backend["<b>Backend API Container</b><br/><i>[Container: Spring Boot / Temurin 21 JRE]</i><br/>Executes REST API & JPA domain logic<br/>as non-root <b>appuser</b> (:8080)"]:::container

        db[("<b>Database Container</b><br/><i>[Container: PostgreSQL 17 Alpine]</i><br/>Stores relational clinical data (:5432)<br/>with pg_isready healthcheck")]:::container

        subgraph mounts ["Docker Volumes & Host Mounts"]
            direction LR
            vol_data["<b>hms_postgres</b><br/><i>[Docker Named Volume]</i><br/>Persists /var/lib/postgresql/data"]:::storage
            vol_seed["<b>./db/init</b><br/><i>[Read-Only Bind Mount]</i><br/>01_init_and_seed.sql & CSV fixtures"]:::storage
        end
    end

    user -->|"HTTP :80"| frontend
    frontend -->|"Proxies /api/* (HTTP :8080)"| backend
    backend -->|"JDBC (TCP :5432)"| db
    db <-->|"Reads / Writes state"| vol_data
    vol_seed -->|"Seeds schema & CSVs on 1st boot"| db
```

# Compose Usage and Health Check Sequence Diagram: 

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant Compose as Docker Compose
    participant DB as db (postgres:17-alpine)
    participant BE as backend (Spring Boot)
    participant FE as frontend (nginx:1.29-alpine)

    Dev->>Compose: docker compose up --build
    Compose->>DB: Start container & mount ./db/init
    alt Volume hms_postgres is empty
        DB->>DB: Execute /docker-entrypoint-initdb.d/01_init_and_seed.sql
        DB->>DB: COPY departments.csv (8 rows) & clinicians.csv (25 rows)
    end
    loop Every 5s (max 15 retries)
        Compose->>DB: pg_isready -U hms -d hms_community
    end
    DB-->>Compose: Status: Healthy
    Compose->>BE: Start container (depends_on: service_healthy)
    BE->>DB: Connect via JDBC (jdbc:postgresql://db:5432/hms_community)
    Compose->>FE: Start container (depends_on: backend)
    FE-->>Dev: Ready at http://localhost:80
```
```

