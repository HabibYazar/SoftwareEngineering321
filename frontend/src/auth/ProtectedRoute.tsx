import type { ReactNode } from 'react'
import { useAuth } from './AuthProvider'
import { canAccessRoute } from './routing'

// Oturum değiştiğinde gezinme henüz eşitlenmediyse bile korunan bileşen oluşturulmaz.
export function ProtectedRoute({ path, children }: { path: string; children: ReactNode }) {
  const { role } = useAuth()
  return role && canAccessRoute(path, role) ? children : null
}