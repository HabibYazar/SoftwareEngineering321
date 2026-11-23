# Sprint 2 — C4 and UML Architecture

**Project:** HMS Community — Team 15

**Date:** 23 November 2026

**Architect:** Habib Yazar

**Status:** Sprint 2 architecture documentation baseline.

The diagrams distinguish implemented Sprint 2 components from planned Sprint 3/final functionality. All local demonstration data is synthetic.

## 1. C4 Level 1 — System Context

```mermaid
flowchart LR
    Receptionist["Receptionist (Person)"]
    Administrator["Administrator (Person)"]
    Staff["Physicians and other staff (Persons)"]
    HMS["HMS Community (Software System)"]
    External["National health and insurance services (External Systems)"]
    Devices["Laboratory and imaging devices (External Systems)"]

    Receptionist -->|"Login, register/search/update patients, browse doctors"| HMS
    Administrator -->|"Login and permitted administrative access"| HMS
    Staff -->|"Authenticate and access permitted functions"| HMS

    HMS -.->|"Future approved adapter; not implemented"| External
    HMS -.->|"Future device adapter; not implemented"| Devices
```

## 2. C4 Level 2 — Containers

```mermaid
flowchart TB
    Users["Authorized staff (Browser)"]

    subgraph LocalHost["Local Docker demonstration host"]
        direction TB
        Frontend["Frontend Container: React and TypeScript SPA served by Nginx 1.29"]
        Backend["Backend Container: Spring Boot 3.5 / Java 21, modular monolith"]
        Database[("Database Container: PostgreSQL 17")]
        Volume[("Docker named volume: hms_postgres")]
    end

    Users -->|"HTTP localhost:80; configurable loopback port"| Frontend
    Frontend -->|"Reverse proxy REST /api/v1"| Backend
    Backend -->|"JDBC TCP 5432, internal network"| Database
    Database <-->|"Persistent database files"| Volume

    Backend -.->|"Flyway on startup: V1-V5 plus optional local demo seeds"| Database

    Note["Backend host port: 127.0.0.1:8080 by default. Database: no published host port. TLS required for real deployments."]
    Note -.- Backend
```

## 3. C4 Level 3 — Backend Components

```mermaid
flowchart LR
    Web["React frontend through Nginx"]
    DB[("PostgreSQL 17")]

    subgraph Backend["Spring Boot modular monolith"]
        Security["Security filter chain / Bearer token verification"]
        Auth["AuthController / AuthenticationService"]
        Patients["PatientController / PatientService / PatientValidator"]
        Staff["StaffController / StaffService"]
        Departments["DepartmentsController / DepartmentsService"]
        Sessions["Account directory / AccessTokenService / Session store"]
        PatientRepo["JdbcPatientRepository"]
        StaffRepo["StaffRepository"]
        DepartmentRepo["DepartmentsRepository"]
        Audit["AuditService / DB audit functions and triggers"]
    end

    Web -->|"REST /api/v1"| Security

    Security --> Auth
    Security --> Patients
    Security --> Staff
    Security --> Departments

    Auth --> Sessions
    Patients --> PatientRepo
    Patients --> Audit
    Staff --> StaffRepo
    Departments --> DepartmentRepo

    Sessions -->|"Accounts and sessions"| DB
    PatientRepo -->|"Patients and relatives"| DB
    StaffRepo -->|"Doctors read view"| DB
    DepartmentRepo -->|"Departments table"| DB
    Audit -->|"Audit records"| DB

    Planned["Appointments, encounters, records, laboratory and inpatient modules: planned/deferred"]
    Planned -.- Backend
```

## 4. UML Implementation Class Diagram

```mermaid
classDiagram
    class AuthController {
        +login(LoginRequest) LoginResponse
        +logout(StaffAuthenticationToken) void
    }

    class AuthenticationService {
        +login(String username, String password) IssuedAccessToken
        +logout(StaffAuthenticationToken) void
    }

    class PatientController {
        +register(PatientRequest) ResponseEntity
        +search(String name, String nationalId) List~Patient~
        +get(UUID patientId) Patient
        +update(UUID patientId, PatientRequest) Patient
    }

    class PatientService {
        +register(String nationalId, PatientDetails details, AuthenticatedStaff actor) Patient
        +search(String name, String nationalId, AuthenticatedStaff actor) List~Patient~
        +get(UUID patientId, AuthenticatedStaff actor) Patient
        +update(UUID patientId, String nationalId, PatientDetails details, AuthenticatedStaff actor) Patient
    }

    class PatientValidator {
        +validateNationalId(String nationalId) List~FieldViolation~
        +validateDetails(PatientDetails details) List~FieldViolation~
    }

    class PatientRepository {
        <<interface>>
        +insert(Patient patient, AuthenticatedStaff actor) Patient
        +findById(UUID id) Optional~Patient~
        +findByNationalId(String id) Optional~Patient~
        +searchByName(String name) List~Patient~
        +update(Patient patient, AuthenticatedStaff actor) Patient
    }

    class JdbcPatientRepository {
        -JdbcClient jdbc
        +insert(Patient patient, AuthenticatedStaff actor) Patient
        +update(Patient patient, AuthenticatedStaff actor) Patient
    }

    class StaffController {
        +listClinicians(String department) List~Clinician~
    }

    class StaffService {
        +getClinicians(String department) List~Clinician~
    }

    class StaffRepository {
        -JdbcClient jdbcClient
        +findClinicians(String department) List~Clinician~
    }

    class DepartmentsController {
        +listDepartments() List~Department~
    }

    class DepartmentsService {
        +getDepartments() List~Department~
    }

    class DepartmentsRepository {
        -JdbcClient jdbcClient
        +findDepartments() List~Department~
    }

    class Clinician {
        <<record>>
        +UUID id
        +String displayName
        +String department
    }

    class Department {
        <<record>>
        +UUID id
        +String name
    }

    AuthController --> AuthenticationService
    PatientController --> PatientService
    PatientService --> PatientValidator
    PatientService --> PatientRepository
    JdbcPatientRepository ..|> PatientRepository

    StaffController --> StaffService
    StaffService --> StaffRepository
    StaffRepository ..> Clinician

    DepartmentsController --> DepartmentsService
    DepartmentsService --> DepartmentsRepository
    DepartmentsRepository ..> Department
```

## 5. UML Authentication Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Staff Member
    participant UI as React Frontend
    participant Auth as AuthController
    participant Service as AuthenticationService
    participant Accounts as StaffAccountDirectory
    participant Tokens as AccessTokenService
    participant DB as PostgreSQL

    Staff->>UI: Enter username and password
    UI->>Auth: POST /api/v1/auth/login
    Auth->>Service: login(username, password)
    Service->>Accounts: findByUsername(username)
    Accounts->>DB: Read account and role
    DB-->>Accounts: Account or no match
    Accounts-->>Service: Optional account

    alt Valid and active account
        Service->>Service: Verify BCrypt password
        Service->>Tokens: issue(account)
        Tokens->>DB: Record authenticated session
        DB-->>Tokens: Session created
        Tokens-->>Service: Access token and staff role
        Service-->>Auth: IssuedAccessToken
        Auth-->>UI: HTTP 200 LoginResponse
        UI-->>Staff: Authorized workspace
    else Invalid credentials
        Service->>DB: Record failed login
        Service-->>Auth: InvalidCredentialsException
        Auth-->>UI: HTTP 401
        UI-->>Staff: Login error, no session
    end

    opt Staff logs out
        Staff->>UI: Logout
        UI->>Auth: POST /api/v1/auth/logout with Bearer token
        Auth->>Service: logout(authentication)
        Service->>Tokens: revoke(sessionId)
        Tokens->>DB: Revoke session
        Auth-->>UI: HTTP 204 No Content
        UI-->>Staff: Return to login
    end
```

## 6. UML Patient Management Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    participant UI as React Frontend
    participant Security as Spring Security
    participant Controller as PatientController
    participant Service as PatientService
    participant Validator as PatientValidator
    participant Repo as JdbcPatientRepository
    participant DB as PostgreSQL
    participant Audit as AuditService / DB Triggers

    Receptionist->>UI: Complete patient registration
    UI->>Security: POST /api/v1/patients (Bearer token)
    Security->>Security: Verify PATIENT_CREATE permission

    alt Unauthorized
        Security-->>UI: HTTP 401 or 403
    else Authorized
        Security->>Controller: Forward registration request
        Controller->>Service: register(nationalId, details, actor)
        Service->>Validator: Validate ID, fields and condition
        alt Invalid fields
            Service-->>UI: HTTP 400 field errors
        else Valid fields
            Service->>Repo: insert(patient, actor)
            Repo->>DB: INSERT patient and optional relative
            DB->>Audit: Audit trigger, same transaction
            DB-->>Repo: Persisted patient
            Repo-->>Service: Patient
            Service-->>Controller: Patient
            Controller-->>UI: HTTP 201 Created
        end
    end

    Receptionist->>UI: Search by name or national ID
    UI->>Controller: GET /api/v1/patients?name=... (authorized)
    Controller->>Service: search(name, nationalId, actor)
    Service->>Repo: searchByName / findByNationalId
    Repo->>DB: SELECT matching patients
    DB-->>Repo: Patient records
    Service->>Audit: Record viewed patient results
    Controller-->>UI: HTTP 200 patient list

    opt Update existing patient
        Receptionist->>UI: Edit permitted patient fields
        UI->>Controller: PUT /api/v1/patients/{patientId}
        Controller->>Service: update(patientId, details, actor)
        Service->>Validator: Apply US-06 validation rules
        Service->>Repo: update(patient, actor)
        Repo->>DB: UPDATE patient and relative
        DB->>Audit: Before/after audit trigger
        Controller-->>UI: HTTP 200 updated patient
    end
```

## 7. UML Doctor Directory Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    participant UI as React DoctorDirectory
    participant Security as Spring Security
    participant Departments as DepartmentsController
    participant DeptService as DepartmentsService
    participant DeptRepo as DepartmentsRepository
    participant Staff as StaffController
    participant StaffService as StaffService
    participant StaffRepo as StaffRepository
    participant DB as PostgreSQL

    Receptionist->>UI: Open doctor directory
    UI->>Security: GET /api/v1/departments
    Security->>Security: Verify DOCTOR_DIRECTORY_VIEW

    alt Authorized
        Security->>Departments: Route request
        Departments->>DeptService: getDepartments()
        DeptService->>DeptRepo: findDepartments()
        DeptRepo->>DB: SELECT id, name FROM departments WHERE is_active
        DB-->>DeptRepo: Active departments
        Departments-->>UI: HTTP 200 Department[]
    else Unauthorized
        Security-->>UI: HTTP 401 or 403
    end

    Receptionist->>UI: Select Kardiyoloji
    UI->>Security: GET /api/v1/staff?department=Kardiyoloji
    Security->>Staff: Authorized request
    Staff->>StaffService: getClinicians("Kardiyoloji")
    StaffService->>StaffRepo: findClinicians("Kardiyoloji")
    StaffRepo->>DB: SELECT from doctors view WHERE department = ?
    DB-->>StaffRepo: Clinician records
    StaffRepo-->>StaffService: List of clinicians
    StaffService-->>Staff: List of clinicians
    Staff-->>UI: HTTP 200 Clinician[]

    alt Results found
        UI-->>Receptionist: Display matching doctors
    else Empty results
        UI-->>Receptionist: No doctors found
    end
```

## 8. Sprint 2 User Flows

```mermaid
flowchart TD
    Start["Staff opens HMS Community"] --> Login["Login form"]
    Login --> Credentials{"Valid credentials?"}

    Credentials -->|"No"| LoginError["Show error; no token"]
    LoginError --> Login

    Credentials -->|"Yes"| Workspace["Role-aware workspace"]
    Workspace --> Permission{"Permission for selected feature?"}
    Permission -->|"No"| Denied["Access denied / hidden feature"]
    Permission -->|"Yes"| Feature{"Select feature"}

    Feature -->|"Register patient"| Registration["Patient registration form"]
    Registration --> CheckForm{"Valid fields?"}
    CheckForm -->|"No"| FieldErrors["Display field errors"]
    FieldErrors --> Registration
    CheckForm -->|"Yes"| SavePatient["POST /api/v1/patients"]
    SavePatient --> RegistrationResult["Success 201 or error feedback"]

    Feature -->|"Search patients"| Search["Search by exact national ID or partial name"]
    Search --> Results{"Matching patient?"}
    Results -->|"No"| Empty["No patient found"]
    Results -->|"Yes"| Profile["Open patient profile"]
    Profile --> Edit["Edit permitted fields"]
    Edit --> Update["PUT /api/v1/patients/{patientId}"]
    Update --> UpdateResult["Show validation or save result"]

    Feature -->|"Doctor directory"| Departments["Load active departments"]
    Departments --> Choose["Choose department"]
    Choose --> Doctors["GET /api/v1/staff?department=..."]
    Doctors --> Display["Show matching doctors or empty state"]

    Workspace --> Logout["Logout"]
    RegistrationResult --> Workspace
    Empty --> Workspace
    UpdateResult --> Workspace
    Display --> Workspace
    Logout --> Login
```

## 9. Planned Sprint 3 MVP Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    participant UI as React Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    actor Physician

    Note over Receptionist,DB: Sprint 2 implemented: login, patient registration/search/update, clinician directory

    Receptionist->>UI: Log in
    UI->>API: POST /api/v1/auth/login
    API->>DB: Authenticate and create session
    API-->>UI: Bearer token and role

    Receptionist->>UI: Register synthetic patient
    UI->>API: POST /api/v1/patients
    API->>DB: Validate and persist patient
    API-->>UI: 201 Created

    Receptionist->>UI: Browse doctors by department
    UI->>API: GET /api/v1/departments
    UI->>API: GET /api/v1/staff?department=...
    API-->>UI: Department and clinician records

    Note over Receptionist,DB: Sprint 3 below is PLANNED and is not implemented in Sprint 2

    opt Planned Sprint 3 appointment workflow
        Receptionist->>UI: Select clinician and available slot
        UI->>API: POST /api/v1/appointments
        API->>DB: Planned atomic slot reservation
        alt Slot unavailable
            API-->>UI: Planned 409 Conflict
        else Slot available
            API-->>UI: Planned 201 Created
        end

        Physician->>UI: View schedule and begin examination
        UI->>API: GET /api/v1/appointments
        UI->>API: POST /api/v1/encounters
        Physician->>UI: Record clinical note and diagnosis
        UI->>API: PUT /api/v1/encounters/{id}/clinical-note
        Physician->>UI: Complete examination
    end

    Note over UI,DB: Laboratory result workflows belong to the final iteration
```

## 10. Database Schema

The detailed PostgreSQL ER model is maintained in [database-er.mmd](../architecture/database-er.mmd). Migrations V1-V5 are installed through Flyway. The patient gender restriction is updated by V5.

## 11. Design Decisions and Traceability

- [Architecture Decision Records](../adr/0001-modular-monolith.md)
- [US-09 Backend Design](../architecture/US-09-backend-architecture.md)
- [Docker Deployment Design](../architecture/containers-architecture.md)
- [Software Requirements Specification](../SRS.md)
- [Sprint 2 Verification Report](README.md)

**Figma status:** These Mermaid user flows are not a substitute for the required Figma UI/UX prototype. Add the team's Figma design and prototype links to the final Teams submission after verification.
