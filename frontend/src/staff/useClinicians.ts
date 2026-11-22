import { useCallback } from 'react'
import { useResource } from '../components/useResource'
import type { StaffService } from './types'

// Departman, filtrelenen GET isteğinin kimliğine katılır. Eski departmandan veri devralınmaz.
export function useClinicians(service: StaffService, department: string | null, refreshVersion = 0) {
  const load = useCallback((signal: AbortSignal) => service.listClinicians(department, signal), [service, department])
  return useResource(load, refreshVersion)
}
