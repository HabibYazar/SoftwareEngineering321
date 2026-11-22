import type { RegistrationErrors } from './validation.ts'

export const conditionLevels = ['MILD', 'MODERATE', 'ADVANCED'] as const
export type ConditionLevel = typeof conditionLevels[number]

export interface RelativeInformation {
  fullName: string
  relationship: string
  phone: string
}

// OpenAPI istek ve yanıt tipleri. Kimlik numarası, başındaki sıfırları korumak için metindir.
export interface NewPatient {
  firstName: string
  lastName: string
  nationalId: string
  dateOfBirth: string
  gender: string
  phone: string
  address: string
  conditionLevel: ConditionLevel
  relativeInformation?: RelativeInformation
}

export interface Patient extends NewPatient {
  id: string
}

export interface PatientRegistrationService {
  register(patient: NewPatient, accessToken: string | null, signal?: AbortSignal): Promise<Patient>
}

const messages = {
  duplicate: 'A patient with this national ID is already registered.',
  invalid: 'The server rejected the registration. Check the details and try again.',
  unauthorized: 'Your session is missing or expired. Sign in again before submitting.',
  forbidden: 'You do not have permission to register a patient.',
  pending: 'Patient registration is unavailable because the backend database is not configured.',
  server: 'Registration could not be confirmed. Check with your administrator before submitting again.',
  network: 'Registration could not be confirmed. Check your connection before submitting again.',
  response: 'The server returned an unexpected response. Registration could not be confirmed.',
}

export class RegistrationError extends Error {
  readonly code: keyof typeof messages
  readonly fieldErrors: RegistrationErrors

  // Hassas bilgi içerebilecek ham API hata metnini göstermeyiz.
  constructor(code: keyof typeof messages, fieldErrors: RegistrationErrors = {}) {
    super(messages[code])
    this.name = 'RegistrationError'
    this.code = code
    this.fieldErrors = fieldErrors
  }
}
