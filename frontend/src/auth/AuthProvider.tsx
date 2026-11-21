import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import type { AuthSession } from './session'
import type { AuthService, LoginRequest, Role } from './types'

type AuthContextValue = {
  role: Role | null; busy: boolean; notice: string
  login(credentials: LoginRequest): Promise<void>
  logout(): Promise<void>
  getAccessToken(): string | null
}

const AuthContext = createContext<AuthContextValue | null>(null)

// Giriş, çıkış ve korumalı API yanıtları aynı oturum deposunu günceller.
export function AuthProvider({ service, session, children }: {
  service: AuthService; session: AuthSession; children: ReactNode
}) {
  const snapshot = useSyncExternalStore(session.subscribe, session.getSnapshot)
  const { session: active, notice } = snapshot
  const inFlight = useRef(false)
  const [busy, setBusy] = useState(false)

  // Kullanıcı işlem yapmasa da sunucunun bildirdiği bitiş anında yerel oturum kapatılır.
  useEffect(() => {
    if (!active) return
    const expiresAt = Date.parse(active.expiresAt)
    let timer: ReturnType<typeof setTimeout> | undefined
    function checkExpiry() {
      const remaining = expiresAt - Date.now()
      if (!Number.isFinite(remaining) || remaining <= 0) session.expire(snapshot)
      else timer = setTimeout(checkExpiry, Math.min(remaining, 2_147_483_647))
    }
    checkExpiry()
    return () => clearTimeout(timer)
  }, [active, session, snapshot])

  async function login(credentials: LoginRequest) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    session.update(null)
    try {
      session.update(await service.login(credentials))
    } finally { inFlight.current = false; setBusy(false) }
  }

  async function logout() {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    const accessToken = session.getAccessToken()
    // Sunucudan yanıt gelmeden yerel yetki kaldırılır. Hata oluşursa oturum geri açılmaz.
    session.update(null, 'Signing out…')
    try {
      if (accessToken) await service.logout(accessToken)
      session.update(null, 'You have signed out.')
    } catch {
      session.update(null, 'You are signed out on this browser, but server sign-out could not be confirmed. Your server session may remain active until it expires.')
    } finally { inFlight.current = false; setBusy(false) }
  }

  return <AuthContext.Provider value={{
    role: active?.role ?? null, busy, notice, login, logout, getAccessToken: session.getAccessToken,
  }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider is required')
  return value
}