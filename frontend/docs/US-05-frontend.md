# US-05 staff account management

Administrator can open `#/admin/staff` from home or the sidebar. Central route permissions protect direct addresses and navigation. This screen manages synthetic account records in a separate domain from the Receptionist doctor directory.

Set `VITE_ADMIN_STAFF_MOCK=true` in development. The demo supports search, role filtering, adding and editing accounts, changing roles, activation and deactivation, simulated password-reset requests, a preview role-permission matrix, and demo doctor assignments. Deactivation, role changes, password-reset requests and removals prompt for confirmation. The signed-in demo Administrator and the last active Administrator cannot be deactivated or demoted.

The mock service keeps changes in memory for the current document. It generates no passwords or notifications and never calls the backend. The preview matrix does not alter sign-in, route access or server permissions. Production and disabled demos show an unavailable state.

`StaffManagementService` defines the future integration seam. Approved endpoints, pagination, concurrency, server-side authorization, audit and reset semantics are TBD. Node tests cover create, duplicate username, edit, activation, Administrator protections, reset safeguards, permission isolation and department assignment. [Staff tests](../tests/admin-demo.test.mjs) · [Integration matrix](backend-integration/README.md)
