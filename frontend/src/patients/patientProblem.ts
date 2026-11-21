import { readApiProblem } from '../apiProblem.ts'
import { earliestPatientBirthDate, fieldLimits, type RegistrationErrors, type RegistrationField } from './validation.ts'

const fields: Record<string, RegistrationField> = {
  firstName: 'firstName', lastName: 'lastName', nationalId: 'nationalId',
  dateOfBirth: 'dateOfBirth', gender: 'gender', phone: 'phone', address: 'address',
  conditionLevel: 'conditionLevel', 'relativeInformation.fullName': 'relativeFullName',
  'relativeInformation.relationship': 'relativeRelationship', 'relativeInformation.phone': 'relativePhone',
}

// Java DTO'sundaki iç içe alan yolları, formun düz alan adlarına dönüştürülür.
export async function readPatientFieldErrors(response: Response, signal?: AbortSignal): Promise<RegistrationErrors> {
  const problem = await readApiProblem(response, signal)
  const errors: RegistrationErrors = {}
  if (problem?.code !== 'VALIDATION_FAILED' || !problem.errors) return errors

  for (const issue of problem.errors) {
    if (!issue || typeof issue !== 'object') continue
    const { field: path, code } = issue as Record<string, unknown>
    if (path === 'relativeInformation' && code === 'RequiredForAdvancedCondition') {
      errors.relativeFullName ??= 'This field is required.'
      errors.relativeRelationship ??= 'This field is required.'
      errors.relativePhone ??= 'This field is required.'
      continue
    }
    if (typeof path !== 'string' || typeof code !== 'string' || !Object.hasOwn(fields, path)) continue
    const field = fields[path]
    errors[field] ??= fieldMessage(field, code)
  }
  return errors
}

// Bilinen hata kodları için yerel mesaj kullanılır. Ham sunucu mesajı forma girmez.
function fieldMessage(field: RegistrationField, code: string): string {
  if (code === 'NotBlank' || code === 'NotNull') return 'This field is required.'
  if (code === 'Size' && Object.hasOwn(fieldLimits, field)) return 'Use ' + fieldLimits[field as keyof typeof fieldLimits] + ' characters or fewer.'
  if (field === 'dateOfBirth') {
    if (code === 'PastOrPresent') return 'Date of birth cannot be in the future.'
    if (code === 'Min') return 'Date of birth must be on or after ' + earliestPatientBirthDate + '.'
    return 'Enter a valid date of birth.'
  }
  if (field === 'nationalId') return code === 'Immutable'
    ? 'The national ID cannot be changed.' : 'Enter a synthetic national ID with exactly 11 digits.'
  if (field === 'phone' || field === 'relativePhone') return 'Enter a valid phone number with 7 to 31 characters, optionally prefixed by +.'
  if (field === 'conditionLevel') return 'Choose a condition level.'
  return 'Check the value for this field.'
}