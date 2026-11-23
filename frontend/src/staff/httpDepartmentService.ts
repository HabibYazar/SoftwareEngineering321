import { DepartmentError, parseDepartments, type DepartmentService } from './departments.ts'

// Gerçek departman isteği tek sürümlü GET adresine gider. Hata durumda başka uç noktaya geçmez.
export function createHttpDepartmentService(
  request: typeof fetch = fetch, parse = parseDepartments,
): DepartmentService {
  return { async list(signal) {
    const timeout = AbortSignal.timeout(15000)
    const combined = signal ? AbortSignal.any([signal, timeout]) : timeout
    let response: Response
    try {
      response = await request('/api/v1/departments', {
        method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal: combined,
      })
    } catch {
      signal?.throwIfAborted()
      throw new DepartmentError('network')
    }
    if (response.status === 401 || response.status === 403) throw new DepartmentError('access')
    if (response.status === 404 || response.status === 405 || response.status === 501) throw new DepartmentError('unavailable')
    if (response.status !== 200) throw new DepartmentError('server')
    let body: unknown
    try { body = await response.json() }
    catch {
      signal?.throwIfAborted()
      throw new DepartmentError(timeout.aborted ? 'network' : 'response')
    }
    signal?.throwIfAborted()
    if (timeout.aborted) throw new DepartmentError('network')
    return parse(body)
  } }
}
