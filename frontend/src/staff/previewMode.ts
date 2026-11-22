export interface DirectoryFlags {
  DEV?: boolean
  VITE_STAFF_MOCK?: string
  VITE_STAFF_HTTP_PREVIEW?: string
  VITE_DEPARTMENTS_HTTP_PREVIEW?: string
  VITE_DEPARTMENTS_PR251_COMPAT?: string
}

// Demo ve HTTP ayrı seçimlerdir. Açık demo seçimi, sentetik doktorları gerçek departmanlarla karıştırmaz.
export function directoryModes(flags: DirectoryFlags) {
  const staff = !flags.DEV ? 'unavailable' : flags.VITE_STAFF_MOCK === 'true' ? 'mock'
    : flags.VITE_STAFF_HTTP_PREVIEW === 'true' ? 'http' : 'unavailable'
  const departments = flags.DEV && staff !== 'mock' && flags.VITE_DEPARTMENTS_HTTP_PREVIEW === 'true'
    ? flags.VITE_DEPARTMENTS_PR251_COMPAT === 'true' ? 'pr251' : 'canonical'
    : null
  return { staff, departments } as const
}
