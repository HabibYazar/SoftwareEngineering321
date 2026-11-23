# US-04 audit log viewer

Administrator can open `#/admin/audit-logs` from the sidebar or home. US-02 applies the same Administrator permission to navigation, direct links and the render guard. The backend owns any future server-side audit authorization.

Set `VITE_ADMIN_AUDIT_MOCK=true` in development and sign in with the synthetic Administrator account. The **DEMO MODE — SYNTHETIC AUDIT EVENTS** banner identifies the data. Search by actor, action and inclusive UTC date range, then clear filters or switch timestamp order. The table scrolls independently at narrow widths and displays loading, empty and retryable error states. Events are read-only.

`AuditService.list(signal?)` is the typed seam for a future approved listing and filter contract. Existing US-03 server writes do not provide a listing endpoint. This demo makes no HTTP calls and has no edit/delete actions or browser persistence. Production and disabled demos show an unavailable state.

Node tests cover Administrator-only access, direct links, combined filters, sort order and empty results. [Audit tests](../tests/admin-demo.test.mjs) · [Integration matrix](backend-integration/README.md)
