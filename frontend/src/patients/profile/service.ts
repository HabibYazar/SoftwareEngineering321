import { createHttpPatientProfileService } from './httpProfileService'
import type { PatientProfileService } from './model'

export const patientProfileMockEnabled = import.meta.env.DEV && import.meta.env.VITE_PATIENT_PROFILE_MOCK === 'true'

// Profil demosu yalnızca açık geliştirme ayarıyla seçilir. HTTP hatası seçimi değiştirmez.
export async function createPatientProfileService(request: typeof fetch): Promise<PatientProfileService> {
  if (import.meta.env.DEV && import.meta.env.VITE_PATIENT_PROFILE_MOCK === 'true') {
    const [{ createMockPatientProfileService }, { sharedMockPatientStore }] = await Promise.all([
      import('./mockProfileService'), import('../mockPatientStore'),
    ])
    return createMockPatientProfileService(sharedMockPatientStore)
  }
  return createHttpPatientProfileService(request)
}