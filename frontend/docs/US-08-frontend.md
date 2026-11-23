# US-08 patient profile editing

Administrator and Receptionist can open a profile from search or from the post-registration success action. The protected route is `#/patients/edit/<uuid>`. It does not put the national ID in the URL. The form displays the national ID as read-only. Legacy 11-digit IDs remain valid for loading and editing profiles regardless of the new-registration checksum. [Story #193](https://github.com/HabibYazar/SoftwareEngineering321/issues/193) · [Frontend #200](https://github.com/HabibYazar/SoftwareEngineering321/issues/200)

`GET /api/v1/patients/{patientId}` loads the matching record. `PUT /api/v1/patients/{patientId}` sends a full replacement of editable fields, without `id` or `nationalId`. Mild or Moderate optional contacts are retained unless explicitly removed. When explicitly removed, `relativeInformation` is omitted. Advanced requires contact information. Both requests use the current Bearer session. The returned UUID and immutable identity are checked before displaying success.

Profile and registration use the same on-blur, touched-field and submit validation. Existing unsupported gender values display their original value. A supported option must be chosen before saving. Stored Turkish mobile numbers display as `05XX XXX XX XX`. Other legacy values remain visible and require correction before submission. PUT uses `+905XXXXXXXXX` for valid mobile numbers. The backend still accepts free-form gender strings and its documented broader phone format.

A pending save prevents duplicate writes. Failed saves preserve the draft. Cancel or navigation away uses the existing unsaved-changes guard. Successful saves update matching recent-session summaries and refresh the last submitted query when returning to search.

`VITE_PATIENT_PROFILE_MOCK=true` writes to the shared synthetic store in development. Real HTTP is wired to the merged contract and requires a compatible backend. Live writes and the corresponding US-03 PostgreSQL audit record have not been verified in this increment. [Profile tests](../tests/patient-profile.test.mjs) · [Integration matrix](backend-integration/README.md)
