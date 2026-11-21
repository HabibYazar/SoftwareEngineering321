import { isIntegrationPending } from '../../apiProblem.ts'
import { isPatient } from '../validation.ts'
import { PatientSearchError, type PatientSearchService } from './model.ts'

// GET /api/v1/patients, Bearer ve tam nationalId veya kısmi name ile 200 Patient[] bekler.
export function createHttpPatientSearchService(request: typeof fetch = fetch): PatientSearchService {
  return {
    async search(query, accessToken, signal) {
      if (!accessToken) throw new PatientSearchError('unauthorized')

      const params = new URLSearchParams({ [query.mode]: query.value })
      const timeout = AbortSignal.timeout(15000)
      const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
      let response: Response
      try {
        response = await request('/api/v1/patients?' + params.toString(), {
          method: 'GET',
          headers: { Authorization: 'Bearer ' + accessToken, Accept: 'application/json, application/problem+json' },
          credentials: 'omit',
          cache: 'no-store',
          signal: requestSignal,
        })
      } catch {
        signal?.throwIfAborted()
        throw new PatientSearchError('network')
      }

      switch (response.status) {
        case 400: throw new PatientSearchError('invalid')
        case 401: throw new PatientSearchError('unauthorized')
        case 403: throw new PatientSearchError('forbidden')
        case 503: throw new PatientSearchError(await isIntegrationPending(response, signal) ? 'pending' : 'server')
        case 200: break
        default: throw new PatientSearchError('server')
      }

      let data: unknown
      try {
        data = await response.json()
      } catch {
        signal?.throwIfAborted()
        throw new PatientSearchError(timeout.aborted ? 'network' : 'response')
      }

      if (!Array.isArray(data) || !data.every(isPatient)
        || new Set(data.map(patient => patient.id)).size !== data.length) throw new PatientSearchError('response')

      // Kimlik aramasında yanlış bir hastayı göstermeyiz. Ad aramasında sunucunun tüm eşleşmeleri korunur.
      if (query.mode === 'nationalId' && (data.length > 1 || data.some(patient => patient.nationalId !== query.value))) {
        throw new PatientSearchError('response')
      }
      return data
    },
  }
}
