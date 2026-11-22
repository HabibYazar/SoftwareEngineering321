import type { NewPatient, Patient, RelativeInformation } from '../types.ts'
import { validatePatientDetails, type PatientDetailsDraft, type PatientDetailsErrors } from '../validation.ts'

// Arayüz içi güncelleme modeli, değişmez hasta kimliklerini alan listesine almaz.
export type PatientProfileChanges = Omit<NewPatient, 'nationalId' | 'relativeInformation'> & {
  relativeInformation: RelativeInformation | null
}

export interface PatientProfileService {
  readonly canUpdate: boolean
  load(id: string, accessToken: string | null, signal?: AbortSignal): Promise<Patient>
  update(id: string, changes: PatientProfileChanges, accessToken: string | null, signal?: AbortSignal): Promise<Patient>
}

const messages = {
  missing: 'Patient not found. Return to search and select a patient.',
  invalid: 'The patient changes are invalid. Check the details and try again.',
  unauthorized: 'Your session is missing or expired. Sign in again.',
  forbidden: 'You do not have permission to access this patient profile.',
  network: 'Could not reach the server. Check your connection and try again.',
  server: 'The patient profile is temporarily unavailable. Try again later.',
  response: 'The service returned an unexpected patient profile response.',
  pending: 'Patient profiles are unavailable because the backend database is not configured.',
  identity: 'The national ID cannot be changed.',
  unavailable: 'Saving profile changes is currently unavailable.',
}

export class PatientProfileError extends Error {
  // Ham HTTP hatası hasta bilgisi içerebilir, bu yüzden yalnızca yerel mesajı kullanırız.
  constructor(code: keyof typeof messages, fieldErrors: PatientDetailsErrors = {}) {
    super(messages[code])
    this.name = 'PatientProfileError'
    this.code = code
    this.fieldErrors = fieldErrors
  }

  readonly code: keyof typeof messages
  readonly fieldErrors: PatientDetailsErrors
}

// Form, sabit kimlikler yerine yalnızca düzenlenebilir alanların kopyasını alır.
export function draftFromPatient(patient: Patient): PatientDetailsDraft {
  const { firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel, relativeInformation } = patient
  return {
    firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel,
    relativeFullName: relativeInformation?.fullName ?? '',
    relativeRelationship: relativeInformation?.relationship ?? '',
    relativePhone: relativeInformation?.phone ?? '',
  }
}

// Değişiklik yoksa sayfadan ayrılırken uyarı göstermeye gerek kalmaz.
export function isProfileDirty(patient: Patient, draft: PatientDetailsDraft, includeRelative: boolean): boolean {
  return includeRelative !== Boolean(patient.relativeInformation)
    || Object.entries(draftFromPatient(patient)).some(([field, value]) => draft[field as keyof PatientDetailsDraft] !== value)
}

// null, isteğe bağlı yakının kayıt sırasında açıkça kaldırılmasını ifade eder.
export function validateProfile(draft: PatientDetailsDraft, includeRelative: boolean):
  { payload: PatientProfileChanges } | { errors: PatientDetailsErrors } {
  const result = validatePatientDetails(draft, includeRelative)
  if ('errors' in result) return result
  return { payload: { ...result.payload, relativeInformation: result.payload.relativeInformation ?? null } }
}