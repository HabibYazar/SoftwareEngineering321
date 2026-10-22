# Security policy

**Not suitable for clinical production.** Do not connect to a real hospital or input identifiable patient data. The starter only exposes a local health endpoint and denies other HTTP requests by default. This is not a complete authentication or authorization implementation.

Report security findings privately to the project maintainers (contact to be configured before public launch); do not create public issues containing exploit details or sensitive data. Fixes require review and regression tests.

Minimum pre-production gates: threat model; RBAC and patient-level access authorization; encrypted transit and secrets; audit immutability and access logging; backup-and-restore drills; data retention/consent and KVKK legal review; least-privilege accounts; medication/lab clinical workflow validation; integration certifications where required.
