export const roles = ['Administrator', 'Receptionist', 'Physician', 'Nurse', 'PatientCareAssistant', 'LabTechnician', 'CleaningStaff'] as const
export type Role = typeof roles[number]
export type LoginRequest = { username: string; password: string }
export type LoginResponse = { accessToken: string; tokenType: 'Bearer'; role: Role; expiresAt: string }
export interface AuthService {
  login(credentials: LoginRequest): Promise<LoginResponse>
  logout(accessToken: string): Promise<void>
}
export class AuthError extends Error {
  readonly code: 'invalid' | 'request' | 'forbidden' | 'pending' | 'network' | 'server' | 'response'
  constructor(code: AuthError['code']) {
    super({
      invalid: 'Invalid username or password.',
      request: 'Unable to sign in. Please check your details and try again.',
      forbidden: 'You do not have permission to sign in.',
      pending: 'Staff sign-in is unavailable because the backend database is not configured.',
      network: 'Unable to reach the server. Check your connection and try again.',
      server: 'The sign-in service is unavailable. Please try again later.',
      response: 'The server returned an unexpected response. Please try again later.',
    }[code])
    this.name = 'AuthError'
    this.code = code
  }
}
// Java LoginResponse mutlak bitiş zamanı ve yedi tanımlı rolden birini döndürür.
export function isLoginResponse(value: unknown): value is LoginResponse {
  if (!value || typeof value !== 'object') return false
  const data = value as Record<string, unknown>
  return typeof data.accessToken === 'string' && data.accessToken.trim().length > 0
    && !/\s/.test(data.accessToken) && data.tokenType === 'Bearer' && roles.some(role => role === data.role)
    && typeof data.expiresAt === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(data.expiresAt)
    && Number.isFinite(Date.parse(data.expiresAt))
}
