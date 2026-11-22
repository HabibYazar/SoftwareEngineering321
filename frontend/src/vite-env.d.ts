/// <reference types="vite/client" />

// Her sentetik servis yalnızca Vite geliştirme modunda kendi açık ayarıyla seçilir.
interface ImportMetaEnv {
  readonly VITE_AUTH_MOCK?: string
  readonly VITE_ADMIN_AUDIT_MOCK?: string
  readonly VITE_ADMIN_STAFF_MOCK?: string
  readonly VITE_STAFF_HTTP_PREVIEW?: string
  readonly VITE_DEPARTMENTS_HTTP_PREVIEW?: string
  readonly VITE_DEPARTMENTS_PR251_COMPAT?: string
  readonly VITE_STAFF_MOCK?: string
  readonly VITE_PATIENT_REGISTRATION_MOCK?: string
  readonly VITE_PATIENT_SEARCH_MOCK?: string
  readonly VITE_PATIENT_PROFILE_MOCK?: string
}
