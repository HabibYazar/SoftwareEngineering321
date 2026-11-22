import { summarizePatient } from '../recentPatients.ts'
import { sharedMockPatientStore, type MockPatientStore } from '../mockPatientStore.ts'
import { PatientSearchError, type PatientSearchService } from './model.ts'

// Demo kimliği tam eşleştirir. Ad aramasında birleştirilmiş ad ve soyadın içinde arama yapar.
export function createMockPatientSearchService(store: MockPatientStore = sharedMockPatientStore, delay = 450): PatientSearchService {
  return {
    listDemoPatients: () => store.list().map(summarizePatient),
    async search(query, accessToken, signal) {
      if (!accessToken) throw new PatientSearchError('unauthorized')
      signal?.throwIfAborted()
      if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay))
      signal?.throwIfAborted()

      const name = query.value.toLowerCase()
      return store.list().filter(patient => query.mode === 'nationalId'
        ? patient.nationalId === query.value
        : (patient.firstName + ' ' + patient.lastName).toLowerCase().includes(name))
    },
  }
}
