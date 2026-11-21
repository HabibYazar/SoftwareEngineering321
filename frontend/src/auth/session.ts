import type { LoginResponse } from './types.ts'

// Tarih, sunucunun verdiği mutlak bitiş anıdır. Sunucu yetki denetimi ayrıca yürür.
export function hasSessionExpired(session: LoginResponse, now = Date.now()): boolean {
  const expiresAt = Date.parse(session.expiresAt)
  return !Number.isFinite(expiresAt) || expiresAt <= now
}

interface SessionSnapshot {
  session: LoginResponse | null
  notice: string
}

// İstek katmanı ile React aynı bellek içi oturumu okur. Tarayıcı depolamasına yazılmaz.
export function createAuthSession() {
  let snapshot: SessionSnapshot = { session: null, notice: '' }
  const listeners = new Set<() => void>()

  function update(session: LoginResponse | null, notice = '') {
    snapshot = { session, notice }
    listeners.forEach(listener => listener())
  }

  return {
    getSnapshot: () => snapshot,
    getAccessToken: () => snapshot.session?.accessToken ?? null,
    subscribe(listener: () => void) {
      listeners.add(listener)
      return () => { listeners.delete(listener) }
    },
    update,
    // Önceki oturumdan geç dönen 401, yeni girişin belirtecini silemez.
    expire(requestSnapshot: SessionSnapshot) {
      if (snapshot === requestSnapshot && snapshot.session) {
        update(null, 'Your session has expired. Sign in again.')
      }
    },
  }
}

export type AuthSession = ReturnType<typeof createAuthSession>