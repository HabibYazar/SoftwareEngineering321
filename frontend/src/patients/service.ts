import { createHttpRegistrationService } from './httpRegistrationService'
import type { PatientRegistrationService } from './types'

export const registrationMockEnabled = import.meta.env.DEV && import.meta.env.VITE_PATIENT_REGISTRATION_MOCK === 'true'

// Demo seçimi başlangıçta ve yalnızca açık geliştirme ayarıyla yapılır. HTTP hatası modu değiştiremez.
export async function createRegistrationService(request: typeof fetch): Promise<PatientRegistrationService> {
  if (import.meta.env.DEV && import.meta.env.VITE_PATIENT_REGISTRATION_MOCK === 'true') {
    const [{ createMockRegistrationService }, { sharedMockPatientStore }] = await Promise.all([
      import('./mockRegistrationService'), import('./mockPatientStore'),
    ])
    // İki demo açıksa arama, bu oturumda eklenen sentetik hastayı da görebilir.
    return createMockRegistrationService(undefined, sharedMockPatientStore)
  }
  return createHttpRegistrationService(request)
}
