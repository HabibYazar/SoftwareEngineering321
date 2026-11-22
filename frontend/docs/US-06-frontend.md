# US-06 patient registration

Administrator and Receptionist can open `#/patients/register` from home or the sidebar. Use synthetic data. [Story #175](https://github.com/HabibYazar/SoftwareEngineering321/issues/175) · [Frontend #182](https://github.com/HabibYazar/SoftwareEngineering321/issues/182)

| Field | Frontend validation |
| --- | --- |
| First and last name | Required, up to 100 characters each |
| Synthetic national ID | Exactly 11 ASCII digits, no leading zero, and valid 10th and 11th check digits |
| Date of birth | Calendar date on or after 1900-01-01 and on or before the current local day |
| Gender | Male, Female, Other or Prefer not to disclose. The last label sends `Not disclosed`. The server accepts a string and Product Owner approval of this list is pending. |
| Phone | Turkish mobile input `05XX XXX XX XX`, with automatic spacing and an 11-digit limit. Oversized pastes are rejected. Requests use `+905XXXXXXXXX`. |
| Address | Required, up to 500 characters |
| Condition level and optional relative | Advanced requires a complete contact. Mild and Moderate accept an omitted contact. If included, full name, relationship and Turkish mobile number are required. |

Fields validate on blur and revalidate while correcting. Submit validates all fields, focuses the first invalid control, and prevents duplicate requests. Server field errors remain mapped to their controls. HTTP `409` reports duplicate national ID and failed requests preserve the draft. Blur never saves.

`POST /api/v1/patients` sends the documented `NewPatient` JSON and the in-memory Bearer token. On a confirmed `201 Patient`, the form resets and the success card shows name, condition level and **Edit Profile**, **Register Another Patient** and **Search Patients**. Edit opens the returned UUID directly. The application retains a minimal recent-patient summary in the active session.

## National ID validation boundary

The new-registration form requires exactly 11 ASCII digits, a nonzero first digit and both check digits. The 10th digit uses the weighted odd/even position sums from the first nine digits. The 11th uses the sum of the first ten digits. The form reports invalid input before POST and keeps 409 duplicate handling. The checksum verifies mathematical consistency only. The application performs no official identity lookup.

The Java validator, OpenAPI and database still enforce only the 11-digit format. Exact search and the profile reader keep accepting legacy 11-digit IDs, including existing synthetic IDs starting with zero. IDs remain read-only when editing a profile. Server-side enforcement and formal Product Owner approval are separate pending decisions. Tests generate deterministic, checksum-valid synthetic input for new registrations and retain the legacy fixtures for search and editing. [OpenAPI contract](../../api/openapi.yaml) · [Java validator](../../backend/src/main/java/org/hmscommunity/patients/application/PatientValidator.java)

## Demo and verification

`VITE_PATIENT_REGISTRATION_MOCK=true` uses the temporary synthetic store in development. The prior navigation guard still covers dirty forms and pending submission. Tests exercise client and server errors, field feedback, normalization, UUID links and mock registration. [Form tests](../tests/patient-polish.test.mjs) · [UI tests](../tests/polish-ui.test.mjs) · [Contracts](backend-integration/README.md)
