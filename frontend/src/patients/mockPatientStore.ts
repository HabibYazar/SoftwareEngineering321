import { PatientProfileError, type PatientProfileChanges } from './profile/model.ts'
import type { NewPatient, Patient } from './types.ts'
import { isPatient } from './validation.ts'

const syntheticDetails = {
  dateOfBirth: '2000-01-01', gender: 'Male', phone: '+905000000000',
  address: 'Synthetic example address', conditionLevel: 'MILD',
} as const

const fixtures: Patient[] = [
  { ...syntheticDetails, id: '00000000-0000-4000-8000-000000000001', nationalId: '00000000001', firstName: 'Avery', lastName: 'Demo' },
  { ...syntheticDetails, id: '00000000-0000-4000-8000-000000000002', nationalId: '00000000002', firstName: 'Avery', lastName: 'Sample' },
  { ...syntheticDetails, id: '00000000-0000-4000-8000-000000000003', nationalId: '00000000003', firstName: 'Morgan', lastName: 'Example' },
]

// Yalnızca sentetik demo kayıtları kullanılır. Depo tarayıcı belleğinde yaşar ve yükleme ile sıfırlanır.
export function createMockPatientStore() {
  const records = new Map(fixtures.map(patient => [patient.nationalId, structuredClone(patient)]))

  // UUID, düzenleme için kullanılır. Kimlik numarası kayıt sırasında benzersizlik anahtarı olarak kalır.
  function findById(id: string) {
    return Array.from(records.values()).find(patient => patient.id.toLowerCase() === id.toLowerCase())
  }

  return {
    register(input: NewPatient): Patient | null {
      if (records.has(input.nationalId)) return null
      const patient = { ...structuredClone(input), id: crypto.randomUUID() }
      records.set(patient.nationalId, patient)
      return structuredClone(patient)
    },
    find(id: string): Patient | null {
      const record = findById(id)
      return record ? structuredClone(record) : null
    },
    update(id: string, changes: PatientProfileChanges): Patient | null {
      const current = findById(id)
      if (!current) return null
      const { firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel, relativeInformation } = changes
      // Alanlar tek tek alınır. Dışarıdan eklenen kimlik alanları kayda ulaşmaz.
      const updated: Patient = {
        id: current.id, nationalId: current.nationalId,
        firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel,
      }
      // Sadece açık null mevcut yakını kaldırır. Eksik bir alan, mevcut yakını korur.
      const relative = relativeInformation === null ? undefined : relativeInformation ?? current.relativeInformation
      if (relative !== undefined) {
        updated.relativeInformation = {
          fullName: relative.fullName, relationship: relative.relationship, phone: relative.phone,
        }
      }
      if (!isPatient(updated)) throw new PatientProfileError('invalid')
      records.set(current.nationalId, updated)
      return structuredClone(updated)
    },
    list(): Patient[] {
      return Array.from(records.values(), patient => structuredClone(patient))
    },
  }
}

export type MockPatientStore = ReturnType<typeof createMockPatientStore>

// Etkin demo servisleri kayıt, arama ve düzenleme için aynı sentetik listeyi görür.
export const sharedMockPatientStore = createMockPatientStore()
