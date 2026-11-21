import { createHttpPatientSearchService } from './httpSearchService'
import type { PatientSearchService } from './model'

export const patientSearchMockEnabled = import.meta.env.DEV && import.meta.env.VITE_PATIENT_SEARCH_MOCK === 'true'

// Servis başlangıçta seçilir. HTTP hatası geliştirme demosuna geçiş yapmaz.
export async function createPatientSearchService(request: typeof fetch): Promise<PatientSearchService> {
  if (import.meta.env.DEV && import.meta.env.VITE_PATIENT_SEARCH_MOCK === 'true') {
    const { createMockPatientSearchService } = await import('./mockSearchService')
    return createMockPatientSearchService()
  }
  return createHttpPatientSearchService(request)
}
