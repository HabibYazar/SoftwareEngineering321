export interface DirectoryFlags {
  DEV?: boolean
  VITE_STAFF_MOCK?: string
  VITE_STAFF_HTTP_PREVIEW?: string
  VITE_DEPARTMENTS_HTTP_PREVIEW?: string
  VITE_DEPARTMENTS_PR251_COMPAT?: string
}

// Üretim derlemesi her zaman birleşmiş gerçek uç noktaları kullanır; demo ve önizleme bayrakları yalnızca DEV'dedir.
// DEV'de demo ve HTTP ayrı seçimlerdir. Açık demo seçimi, sentetik doktorları gerçek departmanlarla karıştırmaz.
export function directoryModes(flags: DirectoryFlags) {
  if (!flags.DEV) return { staff: 'http', departments: 'canonical', preview: false } as const
  const staff = flags.VITE_STAFF_MOCK === 'true' ? 'mock'
    : flags.VITE_STAFF_HTTP_PREVIEW === 'true' ? 'http' : 'unavailable'
  const departments = staff !== 'mock' && flags.VITE_DEPARTMENTS_HTTP_PREVIEW === 'true'
    ? flags.VITE_DEPARTMENTS_PR251_COMPAT === 'true' ? 'pr251' : 'canonical'
    : null
  return { staff, departments, preview: staff === 'http' || departments !== null } as const
}
