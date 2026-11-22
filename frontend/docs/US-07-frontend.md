# US-07 patient search

Administrator and Receptionist can open `#/patients/search`. A search uses exactly one criterion: an 11-digit national ID or a nonblank partial name. It calls `GET /api/v1/patients?nationalId=…` or `GET /api/v1/patients?name=…` with Bearer authorization. The HTTP client validates the `Patient[]` response. Exact ID search preserves leading zeros and accepts existing 11-digit IDs that do not satisfy the new-registration checksum. [Story #186](https://github.com/HabibYazar/SoftwareEngineering321/issues/186) · [Frontend #189](https://github.com/HabibYazar/SoftwareEngineering321/issues/189)

Loading, empty, validation, access and retryable error states are distinct. Clear and edited criteria cancel earlier work. Refresh repeats the last submitted criterion while it remains unchanged. Search cards link to editable profiles by UUID. The last query stays in memory while the profile is open, and a successful save refreshes it on return.

**Recently registered in this session** shows up to 12 minimal summaries received after confirmation of a registration. It omits national IDs and contact details and clears when the authentication session ends. The separate **Demo patient list** comes only from the synthetic search service and shares the demo store with registration and profiles. In HTTP mode the page displays **Full patient listing requires backend support**. The server returns `400` when a search criterion is missing, so the client never requests a general listing.

Tests cover empty and malformed results, cancellations, exact and partial queries, immediate demo visibility, and the absence of a full-list request in HTTP mode. [Search tests](../tests/patient-search.test.mjs) · [Patient polish tests](../tests/patient-polish.test.mjs)
