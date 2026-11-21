import { createMockPatientStore, type MockPatientStore } from './mockPatientStore.ts'
import { RegistrationError, type PatientRegistrationService } from './types.ts'

// Demo kaydı, arama demosuyla paylaşılabilen ve kalıcı depolama kullanmayan sentetik depoya yazar.
export function createMockRegistrationService(delay = 250, store: MockPatientStore = createMockPatientStore()): PatientRegistrationService {
  return {
    async register(patient, accessToken, signal) {
      if (!accessToken) throw new RegistrationError('unauthorized')
      signal?.throwIfAborted()
      if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay))
      signal?.throwIfAborted()

      // Depoda kontrol ve kayıt aynı adımda yapılır; eşzamanlı ikinci kayıt da çakışır.
      const registered = store.register(patient)
      if (!registered) throw new RegistrationError('duplicate')
      return registered
    },
  }
}
