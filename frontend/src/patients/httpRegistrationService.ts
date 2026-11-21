import { isIntegrationPending } from '../apiProblem.ts'
import { readPatientFieldErrors } from './patientProblem.ts'
import { RegistrationError, type PatientRegistrationService } from './types.ts'
import { isPatient } from './validation.ts'

// POST /api/v1/patients, Bearer ile NewPatient gönderir ve 201 Patient bekler. Gönderim otomatik yinelenmez.
export function createHttpRegistrationService(request: typeof fetch = fetch): PatientRegistrationService {
  return {
    async register(patient, accessToken, signal) {
      if (!accessToken) throw new RegistrationError('unauthorized')

      const timeout = AbortSignal.timeout(15000)
      const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
      let response: Response
      try {
        response = await request('/api/v1/patients', {
          method: 'POST',
          headers: {
            Authorization: 'Bearer ' + accessToken,
            'Content-Type': 'application/json',
            Accept: 'application/json, application/problem+json',
          },
          credentials: 'omit',
          cache: 'no-store',
          body: JSON.stringify(patient),
          signal: requestSignal,
        })
      } catch {
        signal?.throwIfAborted()
        throw new RegistrationError('network')
      }

      switch (response.status) {
        case 400: throw new RegistrationError('invalid', await readPatientFieldErrors(response, signal))
        case 401: throw new RegistrationError('unauthorized')
        case 403: throw new RegistrationError('forbidden')
        case 503: throw new RegistrationError(await isIntegrationPending(response, signal) ? 'pending' : 'server')
        case 409: throw new RegistrationError('duplicate')
        case 201: break
        default: throw new RegistrationError('server')
      }

      let data: unknown
      try {
        data = await response.json()
      } catch {
        signal?.throwIfAborted()
        throw new RegistrationError(timeout.aborted ? 'network' : 'response')
      }

      // Farklı kimlik numarası dönen veya sözleşmeye uymayan bir yanıt başarı sayılmaz.
      if (!isPatient(data) || data.nationalId !== patient.nationalId) throw new RegistrationError('response')
      return data
    },
  }
}
