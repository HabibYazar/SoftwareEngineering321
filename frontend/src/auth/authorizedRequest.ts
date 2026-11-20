import { hasPermission, type Permission } from './permissions.ts'
import { hasSessionExpired, type AuthSession } from './session.ts'

// Bu taşıyıcı yalnızca uygulamanın korunan iş servisi istemcilerine verilir.
export function createAuthorizedRequest(session: AuthSession, permission: Permission, request: typeof fetch = fetch): typeof fetch {
  return async (input, init) => {
    const snapshot = session.getSnapshot()
    const active = snapshot.session
    if (!active) return new Response(null, { status: 401 })
    // Arka planda zamanlayıcı gecikmiş olsa da süresi dolan token ağ isteğine eklenmez.
    if (hasSessionExpired(active)) {
      session.expire(snapshot)
      return new Response(null, { status: 401 })
    }
    if (!hasPermission(active.role, permission)) return new Response(null, { status: 403 })

    const headers = new Headers(init?.headers)
    const authorization = 'Bearer ' + active.accessToken
    const original = headers.get('Authorization')
    // Eski bir ekran başka oturumun belirteciyle yeniden istek gönderemez.
    if (original !== null && original !== authorization) return new Response(null, { status: 401 })
    headers.set('Authorization', authorization)

    const response = await request(input, { ...init, headers })
    // Yetki reddi oturumu kapatmaz. İptal edilmiş istek de oturumu değiştiremez.
    if (response.status === 401 && !init?.signal?.aborted) session.expire(snapshot)
    return response
  }
}