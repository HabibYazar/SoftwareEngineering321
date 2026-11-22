# US-09 / Issue #204: Backend Clinician Directory & Filter Architecture

## 1. Scope & Objective
This documentation covers the backend implementation for **US-09** (*List doctors by department*) and **Issue #204** (*[Backend] Expose department and filtered doctor lists*).

It satisfies the requirement for hospital staff to retrieve clinicians, either as a complete list or filtered by department name, directly against the seeded relational database.

---

## 2. API Contract
Uses the existing endpoint defined in `api/openapi.yaml`:
- **Path:** `GET /api/v1/staff`
- **Query Parameter:** `department` (optional string, e.g. `Kardiyoloji`)
- **Success Response:** `200 OK` returning `Clinician[]`:
  ```json
  [
    {
      "id": "21e2a9c9-04a7-51cd-bb50-8652e2278c37",
      "displayName": "Doç. Dr. Ayşe ÖZDEMİR",
      "department": "Kardiyoloji"
    }
  ]
  ```
- **Empty / Non-Existent Filter Handling:** Returns HTTP `200 OK` with an empty array `[]` when no clinicians match the specified filter.

---

## 3. Class Design (UML Class Diagram)

```mermaid
classDiagram
    class StaffController {
        -StaffService staffService
        +listClinicians(String department) List~Clinician~
    }

    class StaffService {
        -StaffRepository staffRepository
        +getClinicians(String department) List~Clinician~
    }

    class StaffRepository {
        -JdbcClient jdbcClient
        +findClinicians(String department) List~Clinician~
    }

    class Clinician {
        <<record>>
        +UUID id
        +String displayName
        +String department
    }

    StaffController --> StaffService : calls
    StaffService --> StaffRepository : calls
    StaffRepository ..> Clinician : maps SQL rows to
```

---

## 4. Interaction Flow (UML Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist as Receptionist / Frontend
    participant Ctrl as StaffController
    participant Svc as StaffService
    participant Repo as StaffRepository
    participant DB as PostgreSQL (clinicians table)

    Receptionist->>Ctrl: GET /api/v1/staff?department=Kardiyoloji
    Ctrl->>Svc: getClinicians("Kardiyoloji")
    Svc->>Repo: findClinicians("Kardiyoloji")
    alt department parameter provided
        Repo->>DB: SELECT id, display_name, department FROM clinicians WHERE department = ? ORDER BY row_num ASC
    else no department parameter
        Repo->>DB: SELECT id, display_name, department FROM clinicians ORDER BY row_num ASC
    end
    DB-->>Repo: Clinician records
    Repo-->>Svc: List<Clinician>
    Svc-->>Ctrl: List<Clinician>
    Ctrl-->>Receptionist: 200 OK [Clinician JSON array]
```
