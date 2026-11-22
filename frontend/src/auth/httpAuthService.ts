import { isIntegrationPending } from '../apiProblem.ts'
import { hasSessionExpired } from './session.ts'
import { AuthError, isLoginResponse, type AuthService } from './types.ts'

// /api/v1/auth/login kullanıcı adı ve parola alır. Yanıttan rol, Bearer token ve bitiş zamanı doğrulanır.
export function createHttpAuthService(request: typeof fetch = fetch): AuthService {
  async function post(path: string, init: RequestInit): Promise<Response> {
    try {
      return await request(`/api/v1/auth/${path}`, {
        ...init, method: 'POST', cache: 'no-store', credentials: 'omit', signal: AbortSignal.timeout(15000),
      })
    } catch { throw new AuthError('network') }
  }
  return {
    async login(credentials) {
      const response = await post('login', {
        headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' }, body: JSON.stringify(credentials),
      })
      if (response.status === 401) throw new AuthError('invalid')
      if (response.status === 400) throw new AuthError('request')
      if (response.status === 403) throw new AuthError('forbidden')
      if (await isIntegrationPending(response)) throw new AuthError('pending')
      if (response.status !== 200) throw new AuthError('server')
      let data: unknown
      try { data = await response.json() } catch { throw new AuthError('response') }
      if (!isLoginResponse(data) || hasSessionExpired(data)) throw new AuthError('response')
      return data
    },
    // POST /api/v1/auth/logout geçerli tokenı iptal eder. 401, tokenın artık geçerli olmadığını belirtir.
    async logout(accessToken) {
      const response = await post('logout', { headers: { Authorization: `Bearer ${accessToken}` } })
      if (response.status !== 204 && response.status !== 401) throw new AuthError('server')
    },
  }
}
