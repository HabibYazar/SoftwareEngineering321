import { StaffError, type Clinician, type StaffService } from './types.ts'

const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i

// JSON dışarıdan geldiği için TypeScript türüne güvenmeden alanları ve kimlikleri doğrular.
function parseClinicians(value: unknown): Clinician[] {
  if (!Array.isArray(value)) throw new StaffError('response')
  const ids = new Set<string>()

  return value.map((item: unknown) => {
    if (!item || typeof item !== 'object') throw new StaffError('response')
    const { id, displayName, department } = item as Record<string, unknown>
    if (
      typeof id !== 'string' || !uuid.test(id) || ids.has(id.toLowerCase())
      || typeof displayName !== 'string' || !displayName.trim()
      || typeof department !== 'string' || !department.trim()
    ) throw new StaffError('response')

    ids.add(id.toLowerCase())
    return { id, displayName, department }
  })
}

// Tek HTTP giriş noktası, URL kodlamasını, zaman aşımını ve hata dönüşümünü yönetir.
export function createHttpStaffService(request: typeof fetch = fetch): StaffService {
  return {
    async listClinicians(department, signal) {
      const params = new URLSearchParams()
      if (department !== null) params.set('department', department)
      const url = '/api/v1/staff' + (params.size ? '?' + params.toString() : '')
      const timeout = AbortSignal.timeout(15000)
      const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout

      let response: Response
      try {
        response = await request(url, {
          method: 'GET',
          headers: { Accept: 'application/json' },
          cache: 'no-store',
          credentials: 'omit',
          signal: requestSignal,
        })
      } catch {
        // Ekrandan ayrılma nedeniyle iptal edilen istek kullanıcıya hata olarak gösterilmez.
        signal?.throwIfAborted()
        throw new StaffError('network')
      }

      if (response.status === 401 || response.status === 403) throw new StaffError('access')
      // Eksik önizleme uç noktası bir servis kesintisinden farklı gösterilir.
      if (response.status === 404 || response.status === 405 || response.status === 501) throw new StaffError('unavailable')
      if (response.status !== 200) throw new StaffError('server')

      let data: unknown
      try {
        data = await response.json()
      } catch {
        signal?.throwIfAborted()
        throw new StaffError(timeout.aborted ? 'network' : 'response')
      }
      signal?.throwIfAborted()
      if (timeout.aborted) throw new StaffError('network')
      return parseClinicians(data)
    },
  }
}
