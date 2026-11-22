import { type MockPatientStore } from '../mockPatientStore.ts'
import { PatientProfileError, type PatientProfileService } from './model.ts'

// Kayıt, arama ve düzenleme aynı geçici sentetik veri deposunu kullanır.
export function createMockPatientProfileService(store: MockPatientStore, delay = 250): PatientProfileService {
  async function waitForDemo(accessToken: string | null, signal?: AbortSignal) {
    if (!accessToken) throw new PatientProfileError('unauthorized')
    signal?.throwIfAborted()
    if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay))
    signal?.throwIfAborted()
  }

  return {
    canUpdate: true,
    async load(id, accessToken, signal) {
      await waitForDemo(accessToken, signal)
      const patient = store.find(id)
      if (!patient) throw new PatientProfileError('missing')
      return patient
    },
    async update(id, changes, accessToken, signal) {
      await waitForDemo(accessToken, signal)
      const patient = store.update(id, changes)
      if (!patient) throw new PatientProfileError('missing')
      return patient
    },
  }
}