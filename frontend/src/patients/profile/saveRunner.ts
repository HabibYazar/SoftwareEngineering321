import type { Patient } from '../types.ts'
import { isPatient, type PatientDetailsErrors } from '../validation.ts'
import { PatientProfileError, type PatientProfileChanges, type PatientProfileService } from './model.ts'

type SaveResult = { patient: Patient } | { error: string; fieldErrors?: PatientDetailsErrors }

// İkinci gönderimi aynı anda başlatmaz. Sayfadan ayrılınca eski yanıtı yok sayar.
export function createProfileSaver(service: PatientProfileService) {
  let active: AbortController | null = null

  return {
    pending: () => active !== null,
    cancel: () => active?.abort(),
    async save(patient: Patient, changes: PatientProfileChanges, token: string | null): Promise<SaveResult | undefined> {
      if (active) return
      const controller = new AbortController()
      active = controller
      try {
        const updated = await service.update(patient.id, changes, token, controller.signal)
        if (controller.signal.aborted) return
        if (!isPatient(updated) || updated.id.toLowerCase() !== patient.id.toLowerCase() || updated.nationalId !== patient.nationalId) {
          throw new PatientProfileError('response')
        }
        return { patient: updated }
      } catch (error: unknown) {
        if (controller.signal.aborted) return
        // Sunucu 400 döndürürse düzenlenebilir alanların yerel hata mesajlarını da taşırız.
        if (error instanceof PatientProfileError && Object.keys(error.fieldErrors).length) {
          return { error: error.message, fieldErrors: error.fieldErrors }
        }
        return { error: error instanceof PatientProfileError ? error.message : new PatientProfileError('server').message }
      } finally {
        active = null
      }
    },
  }
}