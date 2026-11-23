# US-02 role-based access control

The sidebar, workspace cards, direct-route resolver and render guard share the same permission table. Signed-out visitors to protected addresses go to sign-in. Signed-in users without the specific grant return to their role home. Architecture remains public. [Frontend issue #135](https://github.com/HabibYazar/SoftwareEngineering321/issues/135)

| Role | Register, search and edit patients | Doctor directory | Audit and staff demos |
| --- | --- | --- | --- |
| Administrator | Yes | — | Yes |
| Receptionist | Yes | Yes | — |
| Physician, Nurse, Patient Care Assistant, Lab Technician, Cleaning Staff | — | — | — |

Administrator and Receptionist patient access follows the inspected PostgreSQL grants. Doctor listing remains Receptionist-only, including in preview. The Administrator demo permissions gate the frontend entry points. Demo matrix mutations neither add real application grants nor change the server. Full product acceptance still depends on the approved role matrix and actual server authorization.

Hash links, browser and app Back/Forward, Home, Refresh, and unsaved patient form protections use the shared navigation. Tokens are in memory. A current protected `401` clears its session, while `403` reports denial and retains it. A late response cannot clear a newer session. Node tests cover all roles including direct Administrator demo links, history, route denial and the protected transport. [Permission tests](../tests/permissions.test.mjs) · [Integration matrix](backend-integration/README.md)
