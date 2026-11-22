import { RegistrationError, type NewPatient, type Patient, type PatientRegistrationService } from './types.ts'
import { emptyRegistration, validateTurkishNationalId, type RegistrationDraft } from './validation.ts'

export interface RegistrationEntry {
  draft: RegistrationDraft
  includeRelative: boolean
  registered: Patient | null
}

// Taslak sadece doğrulanmış servis yanıtıyla sıfırlanır. Başarısızlıkta aynı giriş nesnesi korunur.
export async function finishRegistration(
  service: PatientRegistrationService, entry: RegistrationEntry, payload: NewPatient, token: string | null, signal: AbortSignal,
): Promise<{ entry: RegistrationEntry; patient: Patient } | { entry: RegistrationEntry; error: unknown } | null> {
  if (signal.aborted) return null
  // Çağıran ekranın form denetiminden bağımsız olarak geçersiz kimliği POST'tan önce durdururuz.
  const nationalId = validateTurkishNationalId(payload.nationalId)
  if (nationalId) return { entry, error: new RegistrationError('invalid', { nationalId }) }
  try {
    const patient = await service.register(payload, token, signal)
    return signal.aborted ? null : { entry: { draft: emptyRegistration, includeRelative: false, registered: patient }, patient }
  } catch (error) {
    return signal.aborted ? null : { entry, error }
  }
}
