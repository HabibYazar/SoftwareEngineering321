import { createHttpAuthService } from './httpAuthService'
import type { AuthService } from './types'

export const mockEnabled = import.meta.env.DEV && import.meta.env.VITE_AUTH_MOCK === 'true'
export async function createAuthService(): Promise<AuthService> {
  // Vite eliminates this branch and the mock module from production builds.
  if (import.meta.env.DEV && import.meta.env.VITE_AUTH_MOCK === 'true') {
    const { createMockAuthService } = await import('./mockAuthService')
    return createMockAuthService()
  }
  return createHttpAuthService()
}
