import { isIntegrationPending } from '../../apiProblem.ts'
import { readPatientFieldErrors } from '../patientProblem.ts'
import { isPatient, isPatientId } from '../validation.ts'
import { PatientProfileError, type PatientProfileChanges, type PatientProfileService } from './model.ts'

type UpdateBody = Omit<PatientProfileChanges, 'relativeInformation'> & {
  relativeInformation?: NonNullable<PatientProfileChanges['relativeInformation']>
}

// PUT tam değişim yapar. Yalnızca izinli alanlar taşınır; kimlikler hiçbir zaman gövdeye kopyalanmaz.
function updateBody(changes: PatientProfileChanges): UpdateBody {
  const { firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel, relativeInformation } = changes
  const body: UpdateBody = { firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel }
  // Formun açık kaldırma seçimi null üretir. Java sözleşmesinde yakını kaldırmak için alan atlanır.
  if (relativeInformation) {
    const { fullName, relationship, phone: relativePhone } = relativeInformation
    body.relativeInformation = { fullName, relationship, phone: relativePhone }
  }
  return body
}

// GET ve PUT, /api/v1/patients/{patientId} üzerinde Bearer ile çalışır ve 200 Patient döndürür.
export function createHttpPatientProfileService(request: typeof fetch = fetch): PatientProfileService {
  async function send(id: string, token: string | null, signal?: AbortSignal, body?: UpdateBody) {
    if (!isPatientId(id)) throw new PatientProfileError('missing')
    if (!token) throw new PatientProfileError('unauthorized')
    const timeout = AbortSignal.timeout(15000)
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
    const headers = new Headers({ Authorization: 'Bearer ' + token, Accept: 'application/json, application/problem+json' })
    if (body) headers.set('Content-Type', 'application/json')

    let response: Response
    try {
      response = await request('/api/v1/patients/' + encodeURIComponent(id), {
        method: body ? 'PUT' : 'GET',
        headers, cache: 'no-store', credentials: 'omit', signal: requestSignal,
        body: body ? JSON.stringify(body) : undefined,
      })
    } catch {
      signal?.throwIfAborted()
      throw new PatientProfileError('network')
    }

    // Ortak yetki katmanı geçerli oturumun 401 yanıtını işler. 403 ve 503 oturumu kapatmaz.
    switch (response.status) {
      case 400: {
        const { nationalId, ...fields } = await readPatientFieldErrors(response, signal)
        throw new PatientProfileError(nationalId ? 'identity' : 'invalid', fields)
      }
      case 401: throw new PatientProfileError('unauthorized')
      case 403: throw new PatientProfileError('forbidden')
      case 404: throw new PatientProfileError('missing')
      case 503: throw new PatientProfileError(await isIntegrationPending(response, signal) ? 'pending' : 'server')
      case 200: break
      default: throw new PatientProfileError('server')
    }

    let data: unknown
    try {
      data = await response.json()
    } catch {
      signal?.throwIfAborted()
      throw new PatientProfileError(timeout.aborted ? 'network' : 'response')
    }
    // Başka bir hastanın kaydı bu profil için başarı sayılmaz.
    if (!isPatient(data) || data.id.toLowerCase() !== id.toLowerCase()) throw new PatientProfileError('response')
    return data
  }

  return {
    canUpdate: true,
    load: (id, token, signal) => send(id, token, signal),
    update: (id, changes, token, signal) => send(id, token, signal, updateBody(changes)),
  }
}