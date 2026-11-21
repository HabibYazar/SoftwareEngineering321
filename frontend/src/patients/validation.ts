import { conditionLevels, type ConditionLevel, type NewPatient, type Patient } from './types.ts'

// Uzunluk sınırları OpenAPI'den gelir ve hem form hem yanıt doğrulamasında kullanılır.
export const fieldLimits = {
  firstName: 100,
  lastName: 100,
  gender: 40,
  phone: 32,
  address: 500,
  relativeFullName: 150,
  relativeRelationship: 80,
  relativePhone: 32,
} as const

export type PatientDetailsDraft = Omit<NewPatient, 'nationalId' | 'conditionLevel' | 'relativeInformation'> & {
  conditionLevel: ConditionLevel | ''
  relativeFullName: string
  relativeRelationship: string
  relativePhone: string
}
export type PatientDetailsField = keyof PatientDetailsDraft
export type PatientDetailsErrors = Partial<Record<PatientDetailsField, string>>
export type RegistrationDraft = PatientDetailsDraft & Pick<NewPatient, 'nationalId'>
export type RegistrationField = keyof RegistrationDraft
export type RegistrationErrors = Partial<Record<RegistrationField, string>>

export const emptyRegistration: RegistrationDraft = {
  firstName: '', lastName: '', nationalId: '', dateOfBirth: '', gender: '', phone: '', address: '',
  conditionLevel: '', relativeFullName: '', relativeRelationship: '', relativePhone: '',
}

const patientTextFields = ['firstName', 'lastName', 'gender', 'phone', 'address'] as const
const relativeFields = ['relativeFullName', 'relativeRelationship', 'relativePhone'] as const
// Kayıt ve tam kimlik araması aynı 11 haneli biçimi kullanır.
export const nationalIdPattern = /^[0-9]{11}$/
// Java PatientDetails ve RelativeInformation aynı telefon biçimini denetler.
const patientPhonePattern = /^\+?[0-9][0-9 ()-]{5,29}[0-9]$/
// Kayıt ve düzenleme için PostgreSQL hasta servisinin kabul ettiği en erken tarih.
export const earliestPatientBirthDate = '1900-01-01'
const calendarDatePattern = /^\d{4}-\d{2}-\d{2}$/
const uuidPattern = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i

function isConditionLevel(value: unknown): value is ConditionLevel {
  return conditionLevels.some(level => level === value)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

// Tarihi UTC olarak karşılaştırmak, geçersiz takvim günlerinin otomatik düzeltilmesini yakalar.
function isCalendarDate(value: unknown): value is string {
  if (typeof value !== 'string' || !calendarDatePattern.test(value)) return false
  const date = new Date(value + 'T00:00:00Z')
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}

function isValidText(value: unknown, limit: number) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= limit
}

function validateText(draft: PatientDetailsDraft, errors: PatientDetailsErrors, field: keyof typeof fieldLimits) {
  const value = draft[field].trim()
  if (!value) errors[field] = 'This field is required.'
  else if (value.length > fieldLimits[field]) errors[field] = 'Use ' + fieldLimits[field] + ' characters or fewer.'
  else if ((field === 'phone' || field === 'relativePhone') && !patientPhonePattern.test(value)) {
    errors[field] = 'Enter a valid phone number with 7 to 31 characters, optionally prefixed by +.'
  }
}

// Kayıt ve düzenleme aynı kişi ve yakın alanlarını doğrular. Kimlik numarası bu aşamaya dahil değildir.
export function validatePatientDetails(draft: PatientDetailsDraft, includeRelative: boolean):
  { payload: Omit<NewPatient, 'nationalId'> } | { errors: PatientDetailsErrors } {
  const errors: PatientDetailsErrors = {}
  const level = isConditionLevel(draft.conditionLevel) ? draft.conditionLevel : null
  const withRelative = level === 'ADVANCED' || includeRelative

  patientTextFields.forEach(field => validateText(draft, errors, field))
  if (!isCalendarDate(draft.dateOfBirth)) errors.dateOfBirth = 'Enter a valid date of birth.'
  else if (draft.dateOfBirth < earliestPatientBirthDate) errors.dateOfBirth = 'Date of birth must be on or after ' + earliestPatientBirthDate + '.'
  else {
    // Sunucudaki PastOrPresent kuralını yerel takvim günüyle eşleştiririz.
    const today = new Date()
    const localDate = [today.getFullYear(), today.getMonth() + 1, today.getDate()]
      .map(part => String(part).padStart(2, '0')).join('-')
    if (draft.dateOfBirth > localDate) errors.dateOfBirth = 'Date of birth cannot be in the future.'
  }
  if (!level) errors.conditionLevel = 'Choose a condition level.'
  if (withRelative) relativeFields.forEach(field => validateText(draft, errors, field))
  if (!level || Object.keys(errors).length) return { errors }

  const payload: Omit<NewPatient, 'nationalId'> = {
    firstName: draft.firstName.trim(),
    lastName: draft.lastName.trim(),
    dateOfBirth: draft.dateOfBirth,
    gender: draft.gender.trim(),
    phone: draft.phone.trim(),
    address: draft.address.trim(),
    conditionLevel: level,
  }
  if (withRelative) {
    payload.relativeInformation = {
      fullName: draft.relativeFullName.trim(),
      relationship: draft.relativeRelationship.trim(),
      phone: draft.relativePhone.trim(),
    }
  }
  return { payload }
}

// Yalnızca yeni hasta kaydında kimlik numarası ayrı bir giriş olarak doğrulanır.
export function validateRegistration(draft: RegistrationDraft, includeRelative: boolean):
  { payload: NewPatient } | { errors: RegistrationErrors } {
  const details = validatePatientDetails(draft, includeRelative)
  const nationalId = draft.nationalId.trim()
  if (!nationalIdPattern.test(nationalId)) {
    return { errors: { ...('errors' in details ? details.errors : {}), nationalId: 'Enter a synthetic national ID with exactly 11 digits.' } }
  }
  if ('errors' in details) return details
  return { payload: { ...details.payload, nationalId } }
}

// Profil bağlantıları ve hasta yanıtları aynı UUID biçimini kullanır.
export function isPatientId(value: unknown): value is string {
  return typeof value === 'string' && uuidPattern.test(value)
}

// Hasta yanıtında kimliği, zorunlu alanları ve koşula bağlı yakın bilgisini birlikte doğrular.
export function isPatient(value: unknown): value is Patient {
  if (!isRecord(value)
    || !isPatientId(value.id)
    || typeof value.nationalId !== 'string' || !nationalIdPattern.test(value.nationalId)
    || !isCalendarDate(value.dateOfBirth) || value.dateOfBirth < earliestPatientBirthDate || !isConditionLevel(value.conditionLevel)
    || !patientTextFields.every(field => isValidText(value[field], fieldLimits[field]))) return false

  const relative = value.relativeInformation
  if (relative === undefined) return value.conditionLevel !== 'ADVANCED'
  return isRecord(relative)
    && isValidText(relative.fullName, fieldLimits.relativeFullName)
    && isValidText(relative.relationship, fieldLimits.relativeRelationship)
    && isValidText(relative.phone, fieldLimits.relativePhone)
}
